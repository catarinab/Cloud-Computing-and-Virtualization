package pt.ulisboa.tecnico.cnv.webservice;

import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.DataOutputStream;

import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Map.Entry;

import java.net.HttpURLConnection;

import com.sun.net.httpserver.HttpHandler;
import com.amazonaws.services.dynamodbv2.xspec.S;
import com.sun.net.httpserver.HttpExchange;
import java.net.URI;
import java.net.URL;

/**
 * General handler for user request to Endpoint '/' with any parameters
 */
public class LoadBalancer implements HttpHandler {

    private Aws aws;

    private String credentials;

    private String properties;

    private Boolean isCompression;

    private Lambda lambda;

    private GetEstimations getEstimations;

    public LoadBalancer(Aws receivedAws, String receivedCredentials, String receivedAwsProperties, boolean receivedCompression) {
        aws = receivedAws;
        credentials = receivedCredentials;
        properties = receivedAwsProperties;
        isCompression = receivedCompression;
        lambda = new Lambda();
        getEstimations = new GetEstimations(credentials);
    }

    /**
     * Handles the request 
     * should call ELB, ASG and make API call to workers
     */
    @Override
    public void handle(HttpExchange he) throws IOException {
        // Handling CORS
        he.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        if (he.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            he.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            he.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            he.sendResponseHeaders(204, -1);
            return;
        }

        // parse request
        URI requestedUri = he.getRequestURI(); 
        String requestBody = null;
        Map<String, String> parameters;
        String requestType;
        if (!isCompression) {
            String query = requestedUri.getRawQuery();
            parameters = GetEstimations.queryToMap(query);
            requestType = GetEstimations.getRequestType(parameters);
        }
        else {
            InputStream stream = he.getRequestBody();
            requestBody = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
            String[] resultSplits = requestBody.split(",");
            parameters = new HashMap<>();
            parameters.put("inputEncoded", resultSplits[1]);
            parameters.put("targetFormat", resultSplits[0].split(":")[1].split(";")[0]);
            parameters.put("compressionFactor", resultSplits[0].split(":")[2].split(";")[0]);
            requestType = "compression";
        }
        long requestWork = getEstimations.getEstimation(requestType, parameters);
        String response = null;
        if (requestWork > 0 && requestWork <= AutoScaler.maxWork * 0.001) {
            System.out.println("Request sent to lambda");
            response = lambda.invokeFunction(requestType, parameters);
        }
        else {
            Aws.InstanceInfo instanceInfo = null;
            boolean complete = false;
            while (!complete) {
                instanceInfo = reserveInstance(requestWork);
                String instanceIpAddr = instanceInfo.instance.getPublicIpAddress();
                String serverUrl = "http://" + instanceIpAddr + ":" + 8000 + requestedUri;
                System.out.println("Request sent to " + instanceInfo.instance.getInstanceId());
                try {
                    response = getResponse(serverUrl, instanceInfo, requestBody, requestWork);
                } 
                catch (IOException e) {
                    continue;
                }
                complete = true;
            }

            synchronized (aws) {
                instanceInfo.requests--;
                instanceInfo.work -= requestWork;
            }
        }

        he.sendResponseHeaders(200, response.toString().length());
        OutputStream os = he.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private Aws.InstanceInfo reserveInstance(long requestWork) {
        Aws.InstanceInfo instanceInfo = aws.leastUsedInstance();
        synchronized (aws) {
            if (instanceInfo == null || (instanceInfo.work + requestWork >= AutoScaler.maxWork && instanceInfo.requests > 0)) {
                aws.launchInstance(properties);
                while (instanceInfo == null || (instanceInfo.work + requestWork >= AutoScaler.maxWork && instanceInfo.requests > 0)) {
                    try {
                        Thread.sleep(1000);
                    } 
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    instanceInfo = aws.leastUsedInstance();
                }
            }
            instanceInfo.requests++;
            instanceInfo.work += requestWork;
        }
        return instanceInfo;
    }

    private String getResponse(String url, Aws.InstanceInfo instanceInfo, String requestBody, double requestWork) throws IOException {
        while (true) {
            try {
                // Open a connection to the URL
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

                // Set the request method (GET, POST, etc.)
                if (isCompression) {
                    byte[] requestBodyBytes = requestBody.getBytes();
                    connection.setDoOutput(true);
                    OutputStream outputStream = connection.getOutputStream();
                    outputStream.write(requestBodyBytes, 0, requestBodyBytes.length);
                }

                // Read the response from the API
                InputStream inputStream = connection.getInputStream();

                while (true) {
                    if (inputStream.available() != 0) {
                        // Read the response from the API
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                        String line;
                        StringBuilder response = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        // Process the response
                        System.out.println("Response: " + response.toString());

                        // Close the connection
                        connection.disconnect();
                        return response.toString();
                    } 
                    else {
                        if (aws.statusInstance(instanceInfo.instance.getInstanceId()) == 16) {
                            Thread.sleep(2000);
                        }
                    }
                }
            } 
            catch (IOException exception) {
                if (aws.statusInstance(instanceInfo.instance.getInstanceId()) == 16) {
                    try {
                        Thread.sleep(1000);
                    } 
                    catch (InterruptedException e) {
                        synchronized (aws) {
                            instanceInfo.requests--;
                            instanceInfo.work -= requestWork;
                        }
                        Thread.currentThread().interrupt();
                    }
                }
            } 
            catch (InterruptedException exception) {
                System.out.println("InterruptedException");
                synchronized (aws) {
                    instanceInfo.requests--;
                    instanceInfo.work -= requestWork;
                }
                Thread.currentThread().interrupt();
            }
        }
    }
}