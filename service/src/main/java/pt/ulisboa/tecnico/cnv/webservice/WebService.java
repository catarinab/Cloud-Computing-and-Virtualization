package pt.ulisboa.tecnico.cnv.webservice;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.lang.Thread;

import com.amazonaws.auth.*;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;

public class WebService {

    private static String credentials = "service/src/main/java/pt/ulisboa/tecnico/cnv/webservice/profile.credentials";

    private static String properties = "service/src/main/java/pt/ulisboa/tecnico/cnv/webservice/instance.properties";

    public static void main(String[] args) throws Exception {
        Aws aws = new Aws(credentials);
        AutoScaler autoscaler = new AutoScaler(aws, properties);

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/test", new RootHandler());
        server.createContext("/simulate", new LoadBalancer(aws, credentials, properties, false));
        server.createContext("/compressimage", new LoadBalancer(aws, credentials, properties, true));
        server.createContext("/insectwar", new LoadBalancer(aws, credentials, properties, false));
        
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("Loadbalancer and Autoscaler Started");
        
        autoscaler.autoscale();
    }
}
