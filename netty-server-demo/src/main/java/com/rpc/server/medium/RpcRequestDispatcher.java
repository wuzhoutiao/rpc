package com.rpc.server.medium;

import com.alibaba.fastjson.JSONObject;
import com.rpc.api.protocol.Request;
import com.rpc.api.protocol.Response;
import com.rpc.server.constants.Constants;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    public Response process(Request request){
        Response result = new Response();
        try{
            String command = request.getCommand();
            RpcMethod rpcMethod = METHOD_MAP.get(command);
            if(rpcMethod == null){
                result.setCode(Constants.METHOD_NOT_FOUND);
                result.setMessage("在服务端未找到客户端请求的方法");
                result.setId(request.getId());
                return result;
            }
            Object bean = rpcMethod.getBean();
            Method method = rpcMethod.getMethod();
            Class<?> type = method.getParameterTypes()[0]; //先只实现只有一个参数的方法
            Object content = request.getContent(); //拿到请求的泛型对象
            Object args = JSONObject.parseObject(JSONObject.toJSONString(content),type); //先转换为JSON字符串，然后再按真正的参数类型反序列化
            result = (Response) method.invoke(bean,args); //把业务方法返回的Response对象，赋值给局部变量result
            result.setId(request.getId());
        }catch(Exception e){
            e.printStackTrace();

            result = new Response();
            result.setCode(Constants.FAILURE);
            result.setMessage(getErrorMessage(e));

            // 即使业务方法调用失败，也必须回传原请求 ID，客户端才能唤醒对应的 ResultFuture
            if (request != null && request.getId() != null) {
                result.setId(request.getId());
            }
        }
        return result;
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
