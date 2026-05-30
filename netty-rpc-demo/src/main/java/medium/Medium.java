package medium;

import com.alibaba.fastjson.JSONObject;
import constants.Constants;
import protocol.Response;
import protocol.ServerRequest;

import java.lang.reflect.Method;
import java.util.HashMap;

/* 把对外暴露的方法封装成beanMap通过InitMedium扫描发现并放入medium中*/


public class Medium {
    public static HashMap<String, BeanMethod> mediamap = new HashMap<String,BeanMethod>();
    private static Medium media=null; //全局唯一Medium对象，单例模式
    //懒汉式单例
    private Medium(){}
    public static Medium newInstance(){
        if(media==null){
            media=new Medium();
        }
        return media;
    }

    /* 获得请求，判断是否在服务端找到该方法，如果没找到则返回错误码，如果找到了就反射调用返回结果*/
    public Response process(ServerRequest request){
        Response result = new Response();
        try{
            String command = request.getCommand();
            BeanMethod beanMethod= mediamap.get(command);
            if(beanMethod==null){
                result.setCode(Constants.METHOD_NOT_FOUND);
                result.setMessage("在服务端未找到客户端请求的方法");
                result.setId(request.getId());
                return result;
            }
            Object bean = beanMethod.getBean();
            Method method = beanMethod.getMethod();
            Class type = method.getParameterTypes()[0]; //先只实现只有一个参数的方法
            Object content = request.getContent(); //拿到请求的泛型对象
            Object args = JSONObject.parseObject(JSONObject.toJSONString(content),type); //先转换为JSON字符串，然后再按真正的参数类型反序列化
            result = (Response) method.invoke(bean,args); //把业务方法返回的Response对象，赋值给局部变量result
            result.setId(request.getId());
        }catch(Exception e){
            e.printStackTrace();
        }
        return result;
    }

}
