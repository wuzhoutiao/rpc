package client.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ChannelManager {

    private static final ConcurrentHashMap<String, Channel> channels=new ConcurrentHashMap<>(); //已经连接成功的服务器，其中key:服务器地址，value:对应的netty channel
    private static final Set<String> connectingServers = ConcurrentHashMap.newKeySet(); //正在建立连接的服务器
    private static volatile Set<String> latestServers = Collections.emptySet();//Zookeeper 最新的服务器列表
    private static volatile  List<Channel> channelSnapshot= Collections.emptyList();//保存固定顺序的连接快照，供轮询使用
    private static final AtomicInteger position = new AtomicInteger(0);//轮流请求不同服务器；多个线程可能同时发 RPC 请求，AtomicInteger保证线程安全
    private static volatile boolean shuttingDown = false;// RPC 客户端是否已经进入关闭流程

    public static synchronized void updateChannels(Set<String> newServers, Bootstrap bootstrap){
        if (shuttingDown) {
            return;
        }
        latestServers = Collections.unmodifiableSet(new HashSet<>(newServers)); //保存最新的服务器列表

        //删除已经下线的服务器
        Set<String> oldServers = new HashSet<>(channels.keySet());
        for(String oldServer : oldServers){
            if (!newServers.contains(oldServer)){
                Channel channel = channels.remove(oldServer);
                if(channel!=null){
                    channel.close();
                }
                System.out.println("服务端下线：" + oldServer);
            }
        }

        //为新增服务器建立连接
        for(String server:newServers){
            connectServer(server,bootstrap);}

        refreshSnapshot();
    }

    private static void connectServer(String server, Bootstrap bootstrap){
        if (shuttingDown) {
            return;
        }
        //服务器已经从 zk 中下线
        if(!latestServers.contains(server)) return ;
        //已存在有效连接
        Channel existingChannel =channels.get(server);
        if(existingChannel!=null && existingChannel.isActive()) return;
        //如果已经在连接中，就不重复连接
        if(!connectingServers.add(server)) return;
        try{
            String[] address = server.split(":");
            String ip = address[0];
            int port = Integer.parseInt(address[1]);
            //Netty 异步建立连接
            ChannelFuture future = bootstrap.connect(ip,port);
            //注册连接完成监听器
            future.addListener((ChannelFutureListener) f->{
                boolean shouldReconnect=false;
                synchronized (ChannelManager.class){
                    connectingServers.remove(server); //从正在连接的集合中移除
                    if(f.isSuccess()){
                        Channel newChannel = f.channel();
                        //连接成功时，再次检查服务器是否还存在
                        if(shuttingDown||!latestServers.contains(server)){
                            newChannel.close();
                            System.out.println("客户端正在关闭或服务器已下线，放弃连接：" + server);
                            return;
                        }
                        //加入连接池
                        Channel oldChannel = channels.put(server,newChannel);
                        if(oldChannel!=null&&oldChannel!=newChannel){
                            oldChannel.close();
                        }
                        //监听连接断开
                        newChannel.closeFuture().addListener((ChannelFutureListener) closeFuture->{
                            boolean reconnect;
                            synchronized (ChannelManager.class){
                                //只删除当前断开的 channel
                                channels.remove(server,newChannel);
                                refreshSnapshot();
                                reconnect = !shuttingDown && latestServers.contains(server);
                            }
                            System.out.println("连接已断开："+server);
                            if(reconnect){
                                scheduleReconnect(server,bootstrap);
                            }
                        });
                        refreshSnapshot();
                        System.out.println(("异步连接成功："+server));
                    }else{
                        System.err.println("异步连接失败："+server);

                        if(f.cause()!=null){
                            f.cause().printStackTrace();
                        }
                        shouldReconnect= !shuttingDown && latestServers.contains(server);
                    }
                }
                if(shouldReconnect){
                    scheduleReconnect(server,bootstrap);
                }
            });

        }catch (Exception e){
            connectingServers.remove(server);
            System.err.println("发起连接失败：" + server);
            e.printStackTrace();
            if(!shuttingDown&&latestServers.contains(server)){
                scheduleReconnect(server,bootstrap);
            }
        }
    }

    //延迟3秒重新连接服务器
    private static void scheduleReconnect(
            String server,
            Bootstrap bootstrap) {

        if (shuttingDown) {
            return;
        }

        try {
            bootstrap.config().group().next().schedule(() -> {

                synchronized (ChannelManager.class) {

                    // 客户端正在关闭，不允许重连
                    if (shuttingDown) {
                        return;
                    }

                    // 服务器已经从 ZooKeeper 下线
                    if (!latestServers.contains(server)) {
                        return;
                    }

                    // 已经存在可用连接
                    Channel channel = channels.get(server);

                    if (channel != null && channel.isActive()) {
                        return;
                    }

                    // 重新建立连接
                    connectServer(server, bootstrap);
                }

            }, 3, TimeUnit.SECONDS);

        } catch (RejectedExecutionException e) {

            // 线程组关闭期间可能拒绝新的调度任务
            if (!shuttingDown) {
                throw e;
            }
        }
    }



    public static Channel getChannel(){
        if (shuttingDown) {
            throw new IllegalStateException("RPC 客户端正在关闭，无法发送请求");
        }
        List<Channel> snapshot =channelSnapshot;
        if(snapshot.isEmpty()){
            throw new IllegalStateException("当前没有可用的 RPC 服务器");
        }
        int size=snapshot.size();
        int start=Math.floorMod(position.getAndIncrement(),size);

        for(int i=0;i<size;i++){
            int index=(start+i)%size;
            Channel channel=snapshot.get(index);
            if(channel.isActive()){
                return channel;
            }
        }
        throw new IllegalStateException("当前 RPC 连接均不可用");
    }


    //当服务器列表发生变化时，更新Map，重新生成快照
    private static void refreshSnapshot(){
        List<String> addresses=new ArrayList<>(channels.keySet());

        Collections.sort(addresses); //按服务器地址排序

        List<Channel> snapshot=new ArrayList<>();

        for(String address:addresses){
            Channel channel = channels.get(address);
            if(channel!=null&&channel.isActive()){
                snapshot.add(channel);
            }
        }
        channelSnapshot=Collections.unmodifiableList(snapshot);
    }

    /**
     * 关闭所有 RPC 连接，并停止自动重连
     */
    public static synchronized void shutdown() {

        if (shuttingDown) {
            return;
        }

        System.out.println("ChannelManager 开始关闭");

        // 1. 标记关闭状态
        shuttingDown = true;

        // 2. 清空服务器列表
        latestServers = Collections.emptySet();

        // 3. 清空连接快照
        channelSnapshot = Collections.emptyList();

        // 4. 关闭所有已有连接
        for (Channel channel : channels.values()) {

            if (channel != null) {
                channel.close();
            }
        }

        // 5. 清空连接池
        channels.clear();

        // 6. 清空正在连接的服务器记录
        connectingServers.clear();

        System.out.println("ChannelManager 已发起所有连接的关闭");
    }

}
