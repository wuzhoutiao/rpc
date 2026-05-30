package handler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class MyNettyServerInitializer extends ChannelInitializer<NioSocketChannel> {
    @Override
    protected void initChannel(NioSocketChannel ch) throws Exception {

        ch.pipeline().addLast(new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Delimiters.lineDelimiter()[0])); // 设置\r\n为切割消息的分隔符
        ch.pipeline().addLast(new StringDecoder());//字符串解码器
        ch.pipeline().addLast(new StringEncoder());//字符串编码器，把字符串转换为字节
        // ch.pipeline().addLast(new IdleStateHandler(20, 15, 10, TimeUnit.SECONDS)); 心跳
        ch.pipeline().addLast(new MyServerHandler());//业务逻辑处理处
    }
}
