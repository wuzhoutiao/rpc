package future;

import constants.Constants;
import protocol.ClientRequest;
import protocol.Response;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ResultFuture {
    public final static ConcurrentHashMap<Long,ResultFuture> map = new ConcurrentHashMap<Long,ResultFuture>(); //其中key是requestId
    final Lock lock = new ReentrantLock();
    private Condition condition = lock.newCondition();
    private Response response;
    private final long timeOut = 2*60*1000L;
    private Long startTime = System.currentTimeMillis();

    public ResultFuture(ClientRequest request) {
        map.put(request.getId(), this);
    }

    public Response get() {
        lock.lock();
        try {
            while (!done()){
                condition.await();
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            lock.unlock();
        }
        System.out.println("id="+this.response.getId()+"的请求，已收到服务端返回结果");
        return this.response;
    }


    //收到response后，唤醒对应future
    public static void receive(Response response) {
        if(response!=null){
            ResultFuture future = map.get(response.getId());
            if(future!=null){
                Lock lock = future.lock;
                lock.lock();
                try {
                    future.setResponse(response);
                    future.condition.signal(); //唤醒线程
                    map.remove(response.getId());
                }catch (Exception e){
                    e.printStackTrace();
                }finally {
                    lock.unlock();
                }
            }
        }
    }

    public boolean done() {
        if(this.response != null){
            return true;
        }
        return false;
    }

    public Long getTimeout() {
        return timeOut;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    //清理线程，即使服务端没返回，也能主动构造超时Response
    static class ClearFutureThread extends Thread{
        @Override
        public void run() {
            while(true) {
                Set<Long> RequestIds = map.keySet();
                for (Long requestId : RequestIds) {
                    ResultFuture future = map.get(requestId);
                    if (future == null) { //防止空指针
                        continue;
                    }
                    if (future.getTimeout() < (System.currentTimeMillis() - future.getStartTime())) { // 请求还在等待，但超时
                        Response res = new Response();
                        res.setId(requestId);
                        res.setCode(Constants.TIMEOUT);
                        res.setMessage("超时");
                        System.out.println("请求" + requestId + "超时");
                        receive(res);
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    static {
        ClearFutureThread clearFutureThread = new ClearFutureThread(); //类加载时，自动启动一个后台清理线程
        clearFutureThread.setDaemon(true); //设置为守护线程，当JVM退出时，不阻止程序结束
        clearFutureThread.start();
    }
}
