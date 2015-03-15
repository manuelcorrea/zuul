package demo.hystrix;

import com.netflix.hystrix.Hystrix;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.ribbon.transport.netty.RibbonTransport;
import com.netflix.ribbon.transport.netty.http.LoadBalancingHttpClient;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.functions.Action1;

import java.nio.charset.Charset;

/**
 * Created by mcorrea on 3/4/15.
 */
public class GetRibbonFailover extends HystrixCommand<HttpClientResponse<ByteBuf>> {
    
    private String primaryUrl;
    private String fallbackUrl;
    
    public GetRibbonFailover(String primaryUrl, String fallbackUrl){
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("GetCommand"))
                .andCommandPropertiesDefaults(
                        HystrixCommandProperties.Setter().
                                withExecutionIsolationStrategy(
                                        HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE)
                )
        );
        
        this.primaryUrl = primaryUrl;
        this.fallbackUrl = fallbackUrl;
    }

    @Override
    protected HttpClientResponse<ByteBuf> run() throws Exception {
        try {
            return goPrimary();
        } catch (Exception e) {
            throw e;
        }
    }
    
    
    private HttpClientResponse<ByteBuf> goPrimary() throws Exception {
        LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient();
        HttpClientRequest<ByteBuf> request = HttpClientRequest.createGet(primaryUrl);
        return client.submit(request).toBlocking().first();
    }

    @Override
    protected  HttpClientResponse<ByteBuf> getFallback() {
        LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient();
        HttpClientRequest<ByteBuf> request = HttpClientRequest.createGet(fallbackUrl);
        return client.submit(request).toBlocking().first();
    }

    public static void main(String args[])throws Exception{
        GetRibbonFailover get = new GetRibbonFailover("http://localhost:9292/?status=200&size=1", 
                "http://testingendpoint.cbplatform.link/?status=200&size=1");
        
        HttpClientResponse<ByteBuf> res = get.execute();

        System.out.println(res.getStatus().code());
        res.getContent().subscribe(new Action1<ByteBuf>() {

            @Override
            public void call(ByteBuf content) {
                System.out.println("Response content: " + content.toString(Charset.defaultCharset()));
            }

        });

        Hystrix.reset();
    }
    
}
