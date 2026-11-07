package com.rpc.client.proxy;

import com.rpc.api.model.User;
import com.rpc.api.protocol.RpcResponse;
import com.rpc.client.exception.RpcException;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class RpcResultDecoderTest {

    interface ReturnTypes {
        User user();
        List<User> users();
        long count();
        void complete();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void decodesPojoAndGenericList() throws Exception {
        User user = new User();
        user.setId(7L);
        user.setName("alice");

        User decodedUser = (User) RpcResultDecoder.decode(success(user), method("user"));
        List<User> decodedUsers = (List<User>) RpcResultDecoder.decode(
                success(List.of(user)), method("users"));

        assertEquals(Long.valueOf(7L), decodedUser.getId());
        assertEquals("alice", decodedUsers.get(0).getName());
    }

    @Test
    public void decodesPrimitiveAndVoidReturnTypes() throws Exception {
        assertEquals(12L, RpcResultDecoder.decode(success(12L), method("count")));
        assertNull(RpcResultDecoder.decode(success("ignored"), method("complete")));
    }

    @Test
    public void convertsProtocolFailureToRpcException() throws Exception {
        RpcResponse response = new RpcResponse();
        response.setId(9L);
        response.setCode("REMOTE_ERROR");
        response.setMessage("boom");

        RpcException error = assertThrows(
                RpcException.class,
                () -> RpcResultDecoder.decode(response, method("user")));
        assertEquals("REMOTE_ERROR", error.getCode());
        assertEquals(9L, error.getRequestId());
    }

    private RpcResponse success(Object result) {
        RpcResponse response = new RpcResponse();
        response.setResult(result);
        return response;
    }

    private Method method(String name) throws Exception {
        return ReturnTypes.class.getMethod(name);
    }
}
