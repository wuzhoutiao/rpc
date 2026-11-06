package com.rpc.server.handler;

import com.rpc.api.protocol.HeartbeatMessage;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RpcServerHeartbeatHandlerTest {

    @Test
    public void pongDoesNotEnterRpcRequestProcessing() {
        EmbeddedChannel channel = new EmbeddedChannel(new RpcServerHeartbeatHandler());

        assertFalse(channel.writeInbound(HeartbeatMessage.PONG));
        assertNull(channel.readOutbound());

        channel.finishAndReleaseAll();
    }

    @Test
    public void pingIsRepliedWithoutEnteringRpcRequestProcessing() {
        EmbeddedChannel channel = new EmbeddedChannel(new RpcServerHeartbeatHandler());

        assertFalse(channel.writeInbound(HeartbeatMessage.PING));
        assertEquals(
                HeartbeatMessage.PONG + HeartbeatMessage.LINE_DELIMITER,
                channel.readOutbound()
        );
        assertNull(channel.readInbound());

        channel.finishAndReleaseAll();
    }

    @Test
    public void writerIdleSendsPing() {
        EmbeddedChannel channel = new EmbeddedChannel(new RpcServerHeartbeatHandler());

        channel.pipeline().fireUserEventTriggered(
                IdleStateEvent.WRITER_IDLE_STATE_EVENT
        );
        assertEquals(
                HeartbeatMessage.PING + HeartbeatMessage.LINE_DELIMITER,
                channel.readOutbound()
        );

        channel.finishAndReleaseAll();
    }

    @Test
    public void readerIdleClosesUnresponsiveConnection() {
        EmbeddedChannel channel = new EmbeddedChannel(new RpcServerHeartbeatHandler());

        assertTrue(channel.isActive());
        channel.pipeline().fireUserEventTriggered(
                IdleStateEvent.READER_IDLE_STATE_EVENT
        );
        assertFalse(channel.isActive());

        channel.finishAndReleaseAll();
    }
}
