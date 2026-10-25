package client.core;

import client.constants.Constants;
import client.protocol.ClientRequest;
import com.rpc.common.protocol.Response;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ResultFuture {

    // 保存正在等待响应的 RPC 请求
    public static final ConcurrentHashMap<Long, ResultFuture> map =
            new ConcurrentHashMap<>();

    private final Lock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    // RPC 响应
    private Response response;

    // 请求是否已经完成
    private boolean completed = false;

    // 超时时间：120 秒
    private final long timeOut = 2 * 60 * 1000L;

    // 请求开始时间
    private final long startTime = System.currentTimeMillis();

    // 请求 ID
    private final long requestId;

    // 所有 ResultFuture 共用的生命周期锁
    private static final Object LIFECYCLE_LOCK = new Object();

    // 是否已经关闭
    private static volatile boolean shuttingDown = false;

    public ResultFuture(ClientRequest request) {

        this.requestId = request.getId();

        synchronized (LIFECYCLE_LOCK) {

            // 客户端关闭后，不允许注册新请求
            if (shuttingDown) {
                throw new IllegalStateException(
                        "RPC 客户端已关闭，无法创建新请求"
                );
            }

            ResultFuture previous = map.putIfAbsent(requestId, this);

            if (previous != null) {
                throw new IllegalStateException(
                        "重复的 RPC 请求 ID：" + requestId
                );
            }
        }
    }
    /**
     * 等待 RPC 响应
     */
    public Response get() {

        lock.lock();

        try {

            while (!completed) {
                condition.await();
            }

            return response;

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "等待 RPC 响应时线程被中断，requestId = " + requestId,
                    e
            );

        } finally {

            // 如果等待线程被中断，移除未完成请求；
            // 如果请求已完成，这里不会影响其他请求。
            map.remove(requestId, this);

            lock.unlock();
        }
    }

    /**
     * 收到服务端响应、发送失败响应或超时响应
     */
    public static void receive(Response response) {

        if (response == null) {
            return;
        }

        ResultFuture future = map.get(response.getId());

        if (future == null) {
            return;
        }

        future.complete(response);
    }

    /**
     * 完成当前请求
     *
     * 同一个请求只能完成一次。
     */
    private void complete(Response response) {

        lock.lock();

        try {

            // 已经完成，不允许覆盖之前的结果
            if (completed) {
                return;
            }

            this.response = response;

            this.completed = true;

            // 唤醒等待线程
            condition.signalAll();

            System.out.println(
                    "RPC 请求完成，requestId = " + requestId
                            + "，code = " + response.getCode()
            );

        } finally {

            // 只移除当前 ResultFuture，避免误删其他请求
            map.remove(requestId, this);

            lock.unlock();
        }
    }

    /**
     * 判断请求是否已经超时
     */
    private boolean isTimeout() {

        return System.currentTimeMillis() - startTime >= timeOut;
    }

    /**
     * 创建超时响应
     */
    private Response createTimeoutResponse() {

        Response response = new Response();

        response.setId(requestId);

        response.setCode(Constants.TIMEOUT);

        response.setMessage("RPC 请求超时");

        return response;
    }

    /**
     * 后台超时清理线程
     */
    static class ClearFutureThread extends Thread {

        @Override
        public void run() {

            while (!isInterrupted()) {

                try {

                    for (ResultFuture future : map.values()) {

                        if (future.isTimeout()) {

                            future.complete(
                                    future.createTimeoutResponse()
                            );
                        }
                    }

                    TimeUnit.SECONDS.sleep(1);

                } catch (InterruptedException e) {

                    interrupt();
                    break;
                }
            }
        }
    }

    static {

        ClearFutureThread clearFutureThread =
                new ClearFutureThread();

        clearFutureThread.setName("rpc-future-timeout-cleaner");

        clearFutureThread.setDaemon(true);

        clearFutureThread.start();
    }

    /**
     * RPC 客户端关闭时，结束所有尚未完成的请求
     */
    public static void shutdownAll() {

        synchronized (LIFECYCLE_LOCK) {

            if (shuttingDown) {
                return;
            }

            // 1. 禁止新请求注册
            shuttingDown = true;

            System.out.println("开始结束所有未完成的 RPC 请求");

            // 2. 遍历当前所有请求
            for (ResultFuture future : map.values()) {

                Response response = new Response();

                response.setId(future.requestId);
                response.setCode("RPC_CLIENT_SHUTDOWN");
                response.setMessage("RPC 客户端已关闭");

                // 3. 使用现有完成机制唤醒等待线程
                future.complete(response);
            }

            System.out.println("所有未完成的 RPC 请求已处理");
        }
    }

}

