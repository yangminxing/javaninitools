package main.java.com.ninitools.requestclients;


import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * 多异步请求工具类
 * 1.List结果的顺序同构造函数的参数顺序
 * 2.构造函数的参数不可以完全相同，否则后面排序的话会不正确
 * 3.发生错误时将Throws Exception
 * try
 * {
 *     List<String> [RESULT] =new ParallelRequestUtil([TimeOut], [URL1], [URL2],[URL3]....[URLN]).getResult(); //List结果顺序 URL1返回 URL2返回...
 *     List<String> resultString_1=new ParallelRequestUtil(3000,"http://192.168.0.1/usr?delay=1","http://192.168.0.2").getResult(); //指定超时时间 3Sec
 *     List<String> resultString_2=new ParallelRequestUtil("http://192.168.0.1","http://192.168.0.2").getResult(); //默认超时时间 5Sec
 * }
 * catch(Exception e)
 * {
 *
 * }
 *
 * @author ymx
 */
public class ParallelRequestUtil {

    //超时时间
    private int timeOut;
    //请求URL集合，Set防止地址重复，请求地址不可以重复
    private Map<String,Integer> requesturls=new HashMap<String,Integer>();

    public static void main(String[] args)
    {
        try
        {
            List<String> result =new ParallelRequestUtil(20000, "http://localhost:8081/usr2?delay=5000","http://localhost:8081/usr2?delay=200","http://localhost:8081/usr2?delay=1000").getResult();
            for(String rl:result)
            {
                System.out.println("result :"+rl);
            }
        }
        catch (Exception e)
        {
            System.out.println("err"+e);
        }
    }

    /**
     * 工具类构造函数
     *
     * @param timeOut 超时时间 (毫秒)
     * @param urls 请求地址（返回按照顺序）
     */
    public ParallelRequestUtil(int timeOut, String... urls) throws Exception
    {
        if(urls==null||urls.length==0)
        {
            throw new Exception("ParallelRequestUtil 参数不可为空");
        }

        for(int sortNum=0;sortNum<urls.length;sortNum++)
        {
            if(requesturls.containsKey(urls[sortNum]))
                continue;
            requesturls.put(urls[sortNum], sortNum);
        }

        this.timeOut=timeOut;
    }

    /**
     * 工具类构造函数
     *
     * @param urls 请求地址（返回按照顺序）
     */
    public ParallelRequestUtil(String... urls) throws Exception
    {
        this(5000,urls);
    }

    public List<String> getResult() throws Exception
    {
        //排序好的结果集
        List<String> linkedListResult=new LinkedList<String>();
        try
        {
            int urlSize=requesturls.size();
            //设置超时时间
            RequestConfig requestConfig=RequestConfig.custom()
                    .setSocketTimeout(timeOut)
                    .setConnectionRequestTimeout(timeOut).build();

            //创建Client对象
            CloseableHttpAsyncClient httpclient= HttpAsyncClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build();

            try {
                //启动Client对象
                httpclient.start();

                //设置URL
                final HttpGet[] requests = new HttpGet[urlSize];
                Iterator iter=requesturls.keySet().iterator();
                int stu=0;
                while(iter.hasNext())
                {
                    String p=iter.next().toString();
                    System.out.println(p);
                    requests[stu++] = new HttpGet(p);
                }

                //声明线程计数器
                final CountDownLatch latch = new CountDownLatch(urlSize);

                //声明结果 Map放排序的结果
                final Map<Integer,String> resultMap=new TreeMap<Integer, String>(new Comparator<Integer>() {
                    @Override
                    public int compare(Integer o1, Integer o2) {
                        return o1-o2;
                    }
                });

                //声明错误信息
                final List<String> resultErrorList=new ArrayList<String>();

                for (final HttpGet request : requests) {
                    System.out.println("Start request"+request.toString());

                    //添加请求参数
                    final HttpContext context=HttpClientContext.create();
                    context.setAttribute("sourceurl", request.getURI().toString());

                    //发起请求
                    httpclient.execute(request, context, new FutureCallback<HttpResponse>() {
                        @Override
                        public void completed(HttpResponse httpResponse) {
                            try {
                                latch.countDown();
                                //添加结果集
                                resultMap.put(((Integer)requesturls.get(context.getAttribute("sourceurl"))), EntityUtils.toString(httpResponse.getEntity()));

                            } catch (Exception e) {
                                this.failed(new IOException("ParallelRequestUtil 获取实体过程中发生错误!具体错误为 : " + e));
                            }
                        }

                        @Override
                        public void failed(Exception e) {
                            latch.countDown();
                            resultErrorList.add("ParallelRequestUtil 请求服务失败!请求地址:"+context.getAttribute("sourceurl")+".具体错误为 : "+e);
                        }

                        @Override
                        public void cancelled() {
                            latch.countDown();
                            this.failed(new Exception("ParallelRequestUtil 操作取消!"));
                        }
                    });
                }
                latch.await();

                //设置返回结果集
                Iterator<Map.Entry<Integer,String>> it=resultMap.entrySet().iterator();
                while(it.hasNext())
                    linkedListResult.add(it.next().getValue());

                //发生错误
                if(resultErrorList.size()!=0)
                {
                    StringBuilder sb=new StringBuilder();
                    for(String err:resultErrorList)
                        sb.append(err);
                    throw new Exception(sb.toString());
                }

            }
            catch (Exception e)
            {
                throw e;
            }
            finally {
                //关闭Client
                httpclient.close();
            }

        }
        catch (Exception e)
        {
            throw e;
        }
        //完成
        return linkedListResult;
    }

}
