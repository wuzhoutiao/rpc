package client.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ChannelManager {

    private static final ConcurrentHashMap<String, Channel> channels=new ConcurrentHashMap<>(); //key:服务器地址，value:对应的netty channel
    private static final AtomicInteger position = new AtomicInteger(0);//轮流请求不同服务器；多个线程可能同时发 RPC 请求，AtomicInteger保证线程安全

    public static synchronized void updateChannels(Set<String> newServers, Bootstrap bootstrap){
        //找出已经下线的服务器
        Set<String> oldServers = new HashSet<>(channels.keySet());
        for(String oldServer : oldServers){
            if (!newServers.contains(oldServer)){
                Channel channel = channels.remove(oldServer);
                if(channel!=null){
                    channel.close();
                }
                System.out.println("服务端下线，移除连接：" + oldServer);
            }
        }

        //为新增服务器建立连接
        for(String server:newServers){
            Channel existingChannel =channels.get(server);
            if(existingChannel!=null && existingChannel.isActive()){
                continue;
            }

            String[] address = server.split(":");
            String ip = address[0];
            int port = Integer.parseInt(address[1]);

            try{
                //等待连接建立完成
                ChannelFuture future = bootstrap.connect(ip,port).sync();

                if(future.isSuccess()){
                    Channel newChannel = future.channel();
                    Channel oldChannel = channels.put(server,newChannel);

                    if(oldChannel!=null && oldChannel!=newChannel){
                        oldChannel.close();
                    }

                    System.out.println("建立连接成功：" + server);
                }

            }catch (Exception e){
                System.err.println("连接服务器失败：" + server);
                e.printStackTrace();
            }
        }
    }

    public static Channel getChannel(){
        List<Channel> activeChannels = new ArrayList<>();
        for(Channel channel : channels.values()){
            if(channel.isActive()){
                activeChannels.add(channel);
            }
        }

        if(activeChannels.isEmpty()){
            throw new IllegalStateException("当前没有可用的RPC服务器");
        }

        int index=Math.floorMod(position.getAndIncrement(),activeChannels.size());

        return  activeChannels.get(index);
    }

}
