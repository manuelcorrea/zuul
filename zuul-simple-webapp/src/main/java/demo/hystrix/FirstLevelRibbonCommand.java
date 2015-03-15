package demo.hystrix;

import com.google.common.collect.Lists;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.client.http.RestClient;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * Created by mcorrea on 3/15/15.
 */
public class FirstLevelRibbonCommand extends RibbonCommand {

    private RestClient restClient;
    private HttpRequest.Verb verb;
    private URI uri;
    private Boolean retryable;
    private Map<String, String> headers;
    private Map<String, String> params;
    private InputStream requestEntity;
    private String host1;
    private String host2;
    
    public FirstLevelRibbonCommand(RestClient restClient, HttpRequest.Verb verb,
                                   String uri, Boolean retryable, Map<String, String> headers,
                                   Map<String, String> params, InputStream requestEntity,
                                   String host1, String host2
    ) throws URISyntaxException {
        this("default", restClient, verb, uri, retryable, headers, params, requestEntity, host1, host2);
    }

    public FirstLevelRibbonCommand(String commandKey, RestClient restClient, HttpRequest.Verb verb, 
                                   String uri, Boolean retryable, Map<String, String> headers, 
                                   Map<String, String> params, InputStream requestEntity,
                                   String host1, String host2
                                   ) throws URISyntaxException {
        super(commandKey, restClient, verb, uri, retryable, headers, params, requestEntity, host1, host2);
        this.restClient = restClient;
        this.verb = verb;
        this.uri = new URI(uri);
        this.retryable = retryable;
        this.headers = headers;
        this.params = params;
        this.requestEntity = requestEntity;
        this.host1 = host1;
        this.host2 = host2;
    }

    @Override
    ILoadBalancer getLoadBalancerClient() {
        ILoadBalancer loadBalancer;
        List<Server> serverList =  Lists.newArrayList(
                new Server(host1, 9292));

        loadBalancer = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancer(serverList);
        return loadBalancer;
    }

    @Override
    protected HttpResponse getFallback() {
        try {
            SecondLevelRibbonCommand fallback = new SecondLevelRibbonCommand(
                    "secondlevel",
                    this.restClient,
                    verb, uri.toString(),
                    retryable, headers, params, requestEntity, host1, host2
            );
            return fallback.execute();
            
        }catch(Exception e){
            return super.getFallback();
        }

    }
    
}
