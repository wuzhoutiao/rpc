package com.rpc.server.medium;

import com.alibaba.fastjson.JSONObject;
import com.rpc.api.protocol.Request;
import com.rpc.api.protocol.RpcResponse;
import com.rpc.server.constants.Constants;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/* 保存对外暴露的 RPC 方法，并负责请求分发。 */


public class RpcRequestDispatcher {
    private static final ConcurrentMap<String, RpcMethod> METHOD_MAP =
            new ConcurrentHashMap<>();
    private static final RpcRequestDispatcher INSTANCE = new RpcRequestDispatcher();

    private RpcRequestDispatcher(){}

    public static RpcRequestDispatcher newInstance(){
        return INSTANCE;
    }

    public static void register(String command, RpcMethod rpcMethod) {
        METHOD_MAP.put(command, rpcMethod);
    }

    public static Set<String> getCommands() {
        return Collections.unmodifiableSet(METHOD_MAP.keySet());
    }

    /* 获得请求，判断是否在服务端找到该方法，如果没找到则返回错误码，如果找到了就反射调用返回结果*/
    public RpcResponse process(Request request){
        RpcResponse response = new RpcResponse();
        try{
            String command = request.getCommand();
            RpcMethod rpcMethod = METHOD_MAP.get(command);
            if(rpcMethod == null){
                response.setCode(Constants.METHOD_NOT_FOUND);
                response.setMessage("在服务端未找到客户端请求的方法");
                response.setId(request.getId());
                return response;
            }
            Object bean = rpcMethod.getBean();
            Method method = rpcMethod.getMethod();
            Object[] arguments = request.getArguments();
            Type[] parameterTypes = method.getGenericParameterTypes();
            if (arguments.length != parameterTypes.length) {
                throw new IllegalArgumentException(
                        "参数数量不匹配，期望 " + parameterTypes.length + "，实际 " + arguments.length);
            }
            Object[] convertedArguments = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                convertedArguments[i] = JSONObject.parseObject(
                        JSONObject.toJSONString(arguments[i]), parameterTypes[i]);
            }
            Object businessResult = method.invoke(bean, convertedArguments);
            response.setResult(businessResult);
            response.setId(request.getId());
        }catch(Exception e){
            e.printStackTrace();

            response = new RpcResponse();
            response.setCode(Constants.FAILURE);
            response.setMessage(getErrorMessage(e));

            // 即使业务方法调用失败，也必须回传原请求 ID，客户端才能唤醒对应的 ResultFuture
            if (request != null && request.getId() != null) {
                response.setId(request.getId());
            }
        }
        return response;
    }

    private String getErrorMessage(Exception exception) {
        Throwable cause = exception;

        if (exception instanceof InvocationTargetException
                && ((InvocationTargetException) exception).getTargetException() != null) {
            cause = ((InvocationTargetException) exception).getTargetException();
        }

        String message = cause.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = cause.getClass().getSimpleName();
        }
        return "服务端调用方法失败：" + message;
    }

}
