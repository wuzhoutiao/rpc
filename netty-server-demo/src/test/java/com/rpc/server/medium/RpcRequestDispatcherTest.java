package com.rpc.server.medium;

import com.rpc.api.protocol.Request;
import com.rpc.api.protocol.RpcResponse;
import com.rpc.api.protocol.RpcMethodSignature;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RpcRequestDispatcherTest {

    interface OverloadedService {
        String call();
        String call(long id);
        String call(long id, String name);
        List<String> echo(List<String> values);
    }

    static class OverloadedServiceImpl implements OverloadedService {
        public String call() { return "zero"; }
        public String call(long id) { return "one:" + id; }
        public String call(long id, String name) { return "two:" + id + ":" + name; }
        public List<String> echo(List<String> values) { return values; }
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

    @Test
    public void dispatchesGenericBusinessReturnValue() throws Exception {
        Method method = OverloadedService.class.getMethod("echo", List.class);
        Request request = request(method, new Object[]{List.of("a", "b")});

        assertEquals(List.of("a", "b"), RpcRequestDispatcher.newInstance().process(request).getResult());
    }

    private RpcResponse invoke(Class<?>[] parameterTypes, Object[] arguments) throws Exception {
        Method method = OverloadedService.class.getMethod("call", parameterTypes);
        return RpcRequestDispatcher.newInstance().process(request(method, arguments));
    }

    private Request request(Method method, Object[] arguments) {
        Request request = new Request();
        request.setId(99L);
        request.setCommand(RpcMethodSignature.of(OverloadedService.class, method));
        request.setArguments(arguments);
        return request;
    }
}
