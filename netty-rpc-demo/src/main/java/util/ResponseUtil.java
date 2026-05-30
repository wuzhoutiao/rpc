package util;

import constants.Constants;
import protocol.Response;

public class ResponseUtil {

    //操作成功无返回数据
    public static Response createSuccessResponse(){
        return new Response();
    }

    //操作成功，并返回结果
    public static Response createSuccessResponse(Object content){
        Response response = new Response();
        response.setResult(content);
        return response;
    }

    public static Response createFailResponse(){
        Response response = new Response();
        response.setCode(Constants.FAILURE);
        response.setMessage("操作失败");
        return response;
    }

    public static Response createFailResponse(String code,String msg){
        Response response = new Response();
        response.setCode(code);
        response.setMessage(msg);
        return response;
    }

}
