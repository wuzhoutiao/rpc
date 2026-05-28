package client.core;

import io.netty.channel.ChannelFuture;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ChannelManager {
    public static CopyOnWriteArrayList<ChannelFuture>  channelFutures = new CopyOnWriteArrayList<ChannelFuture>(); //保存所有Netty连接
    public static CopyOnWriteArrayList<String> realServerPath=new CopyOnWriteArrayList<String>(); //保存真实服务器地址
    public static AtomicInteger position = new AtomicInteger(0);//轮流请求不同服务器；多个线程可能同时发 RPC 请求，AtomicInteger保证线程安全

    public static void removeChannelFuture(ChannelFuture channelFuture){
        channelFutures.remove(channelFuture);
    }

    public static void addChannelFuture(ChannelFuture channelFuture){
        channelFutures.add(channelFuture);
    }

    public static void clearChannelFutures(){
        channelFutures.clear();
    }

    public static ChannelFuture get(AtomicInteger i){
        ChannelFuture channelFuture = null;
        int size = channelFutures.size(); //获取连接池大小，即服务器数量
        if(i.get() >= size){ //判断轮询是否越界
            channelFuture = channelFutures.get(0); //如果越界，则重新从0号服务器开始
            ChannelManager.position = new AtomicInteger(1); //下次从1号服务器开始
        }else{
            channelFuture = channelFutures.get(i.getAndIncrement()); //如果没有越界，则先获取当前值再自增
        }
        return channelFuture;
    }

}
