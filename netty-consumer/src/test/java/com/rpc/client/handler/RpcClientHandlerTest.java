package com.rpc.client.handler;

import com.rpc.api.protocol.HeartbeatMessage;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class RpcClientHandlerTest {

    @Test
    public void pingIsRepliedWithoutEnteringRpcResponseProcessing() {
        EmbeddedChannel channel = new EmbeddedChannel(new RpcClientHandler());

        assertFalse(channel.writeInbound(HeartbeatMessage.PING));
        assertEquals(
                HeartbeatMessage.PONG + HeartbeatMessage.LINE_DELIMITER,
                channel.readOutbound()
        );
        assertNull(channel.readInbound());

        channel.finishAndReleaseAll();
    }
}
