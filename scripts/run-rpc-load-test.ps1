[CmdletBinding()]
param(
    [ValidateSet("smoke", "benchmark", "stress")]
    [string]$Mode = "smoke",
    [int]$Concurrency = 0,
    [int]$DurationSeconds = 0,
    [ValidateSet("none", "provider-down", "slow", "circuit-recovery")]
    [string]$FaultMode = "none",
    [switch]$AutoStartDependencies,
    [switch]$SkipBuild,
    [int]$RateLimitPerSecond = 100000,
    [int]$RateLimitBurst = 100000,
    [int]$FaultDelayMs = 800,
    [int]$ProviderPort = 18081,
    [int]$SecondaryProviderPort = 18082,
    [string]$ZooKeeperHome = "/mnt/d/apache-zookeeper-3.8.6-bin",
    [string]$OutputDirectory = "load-test-results"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runLogs = Join-Path $repoRoot "load-test-results\logs"
New-Item -ItemType Directory -Force -Path $runLogs | Out-Null
$startedProviders = [System.Collections.Generic.List[object]]::new()
$startedZooKeeper = $false

function Get-MavenCommand {
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $command) { $command = Get-Command mvn -ErrorAction SilentlyContinue }
    if ($command) { return $command.Source }

    $bundled = Get-ChildItem "C:\Program Files\JetBrains" -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($bundled) { return $bundled.FullName }
    throw "Maven was not found. Add mvn to PATH or install IntelliJ IDEA with bundled Maven."
}

function Test-TcpPort([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync("127.0.0.1", $Port)
        return $task.Wait(800) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-TcpPort([int]$Port, [int]$TimeoutSeconds = 30) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-TcpPort $Port) { return }
        Start-Sleep -Milliseconds 300
    }
    throw "Timed out waiting for port $Port."
}

function Wait-PortFree([int]$Port, [int]$TimeoutSeconds = 10) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (-not (Test-TcpPort $Port)) { return $true }
        Start-Sleep -Milliseconds 300
    }
    return -not (Test-TcpPort $Port)
}

function Wait-File([string]$Path, [int]$TimeoutSeconds = 60,
                   [System.Diagnostics.Process]$Process = $null,
                   [string]$StdoutPath = "", [string]$StderrPath = "") {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-Path $Path) { return }
        if ($Process) {
            $Process.Refresh()
            if ($Process.HasExited) {
                if ($StdoutPath -and (Test-Path $StdoutPath)) { Get-Content $StdoutPath }
                if ($StderrPath -and (Test-Path $StderrPath)) { Get-Content $StderrPath }
                throw "Load-test client exited before creating ready marker: $Path"
            }
        }
        Start-Sleep -Milliseconds 200
    }
    throw "Timed out waiting for load-test ready marker: $Path"
}

function Start-Provider([int]$Port, [int]$MetricsPort, [int]$DelayMs = 0,
                        [double]$FailureRate = 0, [string]$FaultControlFile = "") {
    if (Test-TcpPort $Port) { throw "Port $Port is already in use; the script cannot manage this Provider." }
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
    $stdout = Join-Path $runLogs "provider-$Port-$stamp.out.log"
    $stderr = Join-Path $runLogs "provider-$Port-$stamp.err.log"
    $arguments = @(
        "-q", "-pl", "netty-server-demo",
        "org.codehaus.mojo:exec-maven-plugin:3.5.0:java",
        "-Dexec.mainClass=com.rpc.server.SpringServer",
        "-Drpc.zookeeper.address=127.0.0.1:2181",
        "-Drpc.server.port=$Port",
        "-Drpc.server.metrics.port=$MetricsPort",
        "-Drpc.fault.delay.ms=$DelayMs",
        "-Drpc.fault.failure-rate=$FailureRate",
        "-Drpc.fault.control.file=$FaultControlFile",
        "-Drpc.verbose=false"
    )
    $process = Start-Process -FilePath $script:maven -ArgumentList $arguments `
        -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    Wait-TcpPort $Port 40
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $listener) { throw "Provider listener disappeared from port $Port." }
    $managedProvider = [pscustomobject]@{
        Launcher = $process
        ListenerPid = $listener.OwningProcess
        Port = $Port
    }
    $startedProviders.Add($managedProvider)
    Write-Host "Provider ready: port=$Port, delayMs=$DelayMs, failureRate=$FailureRate"
    return $managedProvider
}

function Stop-ManagedProvider($Provider, [string]$Name) {
    if (-not $Provider) { return }
    if ($Provider.ListenerPid -and
        (Get-Process -Id $Provider.ListenerPid -ErrorAction SilentlyContinue)) {
        # Run through cmd so an idempotent "process not found" race cannot become
        # a PowerShell NativeCommandError under ErrorActionPreference=Stop.
        & cmd.exe /d /c "taskkill.exe /PID $($Provider.ListenerPid) /T /F >nul 2>&1" | Out-Null
    }
    if ($Provider.Launcher -and -not $Provider.Launcher.HasExited) {
        Stop-Process -Id $Provider.Launcher.Id -Force -ErrorAction SilentlyContinue
    }
    Wait-PortFree $Provider.Port 10 | Out-Null
    Write-Host "$Name stopped."
}

function Ensure-ZooKeeper {
    if (Test-TcpPort 2181) {
        Write-Host "ZooKeeper ready: 127.0.0.1:2181"
        return
    }
    if (-not $AutoStartDependencies) {
        throw "ZooKeeper is not ready. Start it first or add -AutoStartDependencies."
    }
    if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
        throw "WSL is unavailable, so ZooKeeper cannot be started automatically."
    }
    & wsl.exe bash -lc "test -x '$ZooKeeperHome/bin/zkServer.sh' && '$ZooKeeperHome/bin/zkServer.sh' start"
    if ($LASTEXITCODE -ne 0) { throw "Failed to start ZooKeeper in WSL: $ZooKeeperHome" }
    $script:startedZooKeeper = $true
    Wait-TcpPort 2181 30
    Write-Host "ZooKeeper started by script."
}

$defaults = @{
    smoke = @{ concurrency = 2; duration = 10 }
    benchmark = @{ concurrency = 32; duration = 30 }
    stress = @{ concurrency = 64; duration = 180 }
}
if ($Concurrency -le 0) { $Concurrency = $defaults[$Mode].concurrency }
if ($DurationSeconds -le 0) { $DurationSeconds = $defaults[$Mode].duration }
$outputPath = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    [System.IO.Path]::GetFullPath($OutputDirectory)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repoRoot $OutputDirectory))
}
$maven = Get-MavenCommand

try {
    Set-Location $repoRoot
    if (-not $SkipBuild) {
        Write-Host "Building RPC project..."
        & $maven -q clean install -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }
    }

    Ensure-ZooKeeper

    $faultControlFile = Join-Path $runLogs "fault-control-$(Get-Date -Format 'yyyyMMdd-HHmmss-fff').txt"
    $providerA = $null
    $providerB = $null
    if ($FaultMode -eq "none") {
        if ($startedZooKeeper -and (Test-TcpPort $ProviderPort)) {
            Write-Host "Port $ProviderPort is still occupied; waiting briefly for a previous Provider to exit..."
            Wait-PortFree $ProviderPort 10 | Out-Null
        }
        if (-not (Test-TcpPort $ProviderPort)) {
            if (-not $AutoStartDependencies) {
                throw "Provider $ProviderPort is not ready. Start it first or add -AutoStartDependencies."
            }
            $providerA = Start-Provider $ProviderPort 19091
        } elseif ($startedZooKeeper) {
            throw "Port $ProviderPort was occupied before the new ZooKeeper instance started. Stop the stale Provider and rerun."
        } else {
            Write-Host "Using existing Provider: 127.0.0.1:$ProviderPort"
        }
    } elseif (-not $AutoStartDependencies) {
        throw "Fault modes require -AutoStartDependencies so the script can manage Providers."
    } elseif ($FaultMode -eq "provider-down") {
        $providerA = Start-Provider $ProviderPort 19091
        $providerB = Start-Provider $SecondaryProviderPort 19093
    } elseif ($FaultMode -eq "slow") {
        $providerA = Start-Provider $ProviderPort 19091
        $providerB = Start-Provider $SecondaryProviderPort 19093 $FaultDelayMs 0
    } elseif ($FaultMode -eq "circuit-recovery") {
        Set-Content -LiteralPath $faultControlFile -Value "fail" -Encoding Ascii
        $providerA = Start-Provider $ProviderPort 19091 0 1.0 $faultControlFile
    }

    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $clientOut = Join-Path $runLogs "client-$Mode-$FaultMode-$stamp.out.log"
    $clientErr = Join-Path $runLogs "client-$Mode-$FaultMode-$stamp.err.log"
    $csv = Join-Path $outputPath "rpc-load-results.csv"
    $csvSizeBefore = if (Test-Path $csv) { (Get-Item $csv).Length } else { 0 }
    $readyMarker = Join-Path $runLogs "client-$Mode-$FaultMode-$stamp.ready"
    if (Test-Path $readyMarker) { Remove-Item -LiteralPath $readyMarker -Force }
    $actionDelay = [Math]::Max(3, [Math]::Min(15, [int]($DurationSeconds / 3)))
    $faultAtValue = if ($FaultMode -in @("provider-down", "circuit-recovery")) { $actionDelay + 1 } else { -1 }
    $clientArguments = @(
        "-q", "-pl", "rpc-service",
        "org.codehaus.mojo:exec-maven-plugin:3.5.0:java",
        "-Dexec.mainClass=com.rpc.service.RpcLoadTest",
        "-Dexec.args=`"$Mode $Concurrency $DurationSeconds`"",
        "-Drpc.zookeeper.address=127.0.0.1:2181",
        "-Drpc.rate-limit.per-second=$RateLimitPerSecond",
        "-Drpc.rate-limit.burst=$RateLimitBurst",
        "-Drpc.load.fault-mode=$FaultMode",
        "-Drpc.load.fault.at.seconds=$faultAtValue",
        "-Drpc.load.ready.file=$readyMarker",
        "-Drpc.load.output.dir=$outputPath",
        "-Drpc.verbose=false"
    )
    $client = Start-Process -FilePath $maven -ArgumentList $clientArguments `
        -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $clientOut -RedirectStandardError $clientErr

    if ($FaultMode -eq "provider-down") {
        Wait-File $readyMarker 60 $client $clientOut $clientErr
        Start-Sleep -Seconds $actionDelay
        Stop-ManagedProvider $providerB "Provider B (fault injection)"
    } elseif ($FaultMode -eq "circuit-recovery") {
        Wait-File $readyMarker 60 $client $clientOut $clientErr
        Start-Sleep -Seconds $actionDelay
        Set-Content -LiteralPath $faultControlFile -Value "normal" -Encoding Ascii
        Write-Host "Faulty Provider switched to normal responses; waiting for half-open recovery."
    }

    $client.WaitForExit()
    $client.Refresh()
    if (Test-Path $clientOut) { Get-Content $clientOut }
    $explicitFailure = $null -ne $client.ExitCode -and $client.ExitCode -ne 0
    $mavenFailure = (Test-Path $clientOut) -and [bool](Select-String -Path $clientOut `
        -Pattern '^\[ERROR\] Failed to execute goal' -Quiet)
    $csvUpdated = (Test-Path $csv) -and (Get-Item $csv).Length -gt $csvSizeBefore
    if ($explicitFailure -or $mavenFailure -or -not $csvUpdated) {
        if (Test-Path $clientErr) { Get-Content $clientErr }
        throw "Load-test process failed, exitCode=$($client.ExitCode), csvUpdated=$csvUpdated"
    }

    $latest = Import-Csv $csv | Select-Object -Last 1
    if ($FaultMode -eq "provider-down") {
        if ([long]$latest.post_fault_success -gt 0) { Write-Host "[PASS] Requests succeeded after Provider shutdown; failover is working." }
        else { Write-Warning "[WARN] No request succeeded after Provider shutdown; inspect failover logs." }
    } elseif ($FaultMode -eq "slow") {
        if ([long]$latest.circuit_open_events -gt 0) { Write-Host "[PASS] Slow Provider triggered circuit opening." }
        else { Write-Warning "[WARN] No circuit-open event observed; increase FaultDelayMs or duration." }
    } elseif ($FaultMode -eq "circuit-recovery") {
        if ([long]$latest.circuit_open_events -gt 0 -and [long]$latest.post_fault_success -gt 0) {
            Write-Host "[PASS] Circuit opening and successful post-recovery requests were observed."
        } else {
            Write-Warning "[WARN] Circuit recovery evidence is incomplete; inspect CSV and logs."
        }
    }
    Write-Host "Result CSV: $csv"
} finally {
    foreach ($process in $startedProviders) {
        Stop-ManagedProvider $process "Managed Provider"
    }
    if ($startedZooKeeper) {
        & wsl.exe bash -lc "'$ZooKeeperHome/bin/zkServer.sh' stop" | Out-Null
        Write-Host "ZooKeeper stopped."
    }
    if ($faultControlFile -and (Test-Path $faultControlFile)) {
        Remove-Item -LiteralPath $faultControlFile -Force
    }
    Set-Location $repoRoot
}
