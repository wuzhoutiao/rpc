package com.rpc.server.medium;

import com.rpc.api.protocol.Request;
import com.rpc.api.protocol.Response;
import com.rpc.api.protocol.RpcMethodSignature;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class RpcRequestDispatcherTest {

    interface OverloadedService {
        Response call();
        Response call(long id);
        Response call(long id, String name);
    }

    static class OverloadedServiceImpl implements OverloadedService {
        public Response call() { return response("zero"); }
        public Response call(long id) { return response("one:" + id); }
        public Response call(long id, String name) { return response("two:" + id + ":" + name); }
        private Response response(String value) {
            Response response = new Response();
            response.setResult(value);
            return response;
        }
    }

    @BeforeClass
    public static void registerMethods() throws Exception {
        OverloadedServiceImpl bean = new OverloadedServiceImpl();
        for (Method interfaceMethod : OverloadedService.class.getMethods()) {
            RpcMethod rpcMethod = new RpcMethod();
            rpcMethod.setBean(bean);
            rpcMethod.setMethod(OverloadedServiceImpl.class.getMethod(
                    interfaceMethod.getName(), interfaceMethod.getParameterTypes()));
            RpcRequestDispatcher.register(
                    RpcMethodSignature.of(OverloadedService.class, interfaceMethod), rpcMethod);
        }
    }

    @Test
    public void dispatchesZeroArgumentMethod() throws Exception {
        assertEquals("zero", invoke(new Class<?>[0], new Object[0]).getResult());
    }

    @Test
    public void dispatchesOverloadedSingleArgumentMethod() throws Exception {
        assertEquals("one:7", invoke(new Class<?>[]{long.class}, new Object[]{7}).getResult());
    }

    @Test
    public void dispatchesOverloadedMultiArgumentMethod() throws Exception {
        assertEquals("two:7:alice", invoke(
                new Class<?>[]{long.class, String.class}, new Object[]{7, "alice"}).getResult());
    }

    private Response invoke(Class<?>[] parameterTypes, Object[] arguments) throws Exception {
        Method method = OverloadedService.class.getMethod("call", parameterTypes);
        Request request = new Request();
        request.setId(99L);
        request.setCommand(RpcMethodSignature.of(OverloadedService.class, method));
        request.setArguments(arguments);
        return RpcRequestDispatcher.newInstance().process(request);
    }
}
