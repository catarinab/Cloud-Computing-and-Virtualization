package pt.ulisboa.tecnico.cnv.webservice;

import java.util.Map;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LambdaException;

public class Lambda {
    
    LambdaClient awsLambda;
    private static String foxesRabbits = "foxes-rabbit";
    private static String insectWar = "insect-war";
    private static String compression = "compression";
    Region region = Region.US_EAST_1;

    public Lambda() {
        awsLambda = LambdaClient.builder()
        .region(region)
        .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .build();
    }
    
	public String invokeFunction(String requestType, Map<String, String> parameters) throws LambdaException {
        String json;
        String invokeFunc;
        if(requestType.equals(foxesRabbits)) {
            json = "{\"generations\":\""+ Integer.parseInt(parameters.get("generations"))+
            "\", \"world\":\""+ Integer.parseInt(parameters.get("world"))+
            "\", \"scenario\":\""+ Integer.parseInt(parameters.get("scenario"))+"\"}";
            invokeFunc = foxesRabbits;
        }
        else if(requestType.equals(insectWar)) {
            json = "{\"max\":\""+ Integer.parseInt(parameters.get("max"))+
            "\", \"army1\":\""+ Integer.parseInt(parameters.get("army1"))+
            "\", \"army2\":\""+ Integer.parseInt(parameters.get("army2"))+"\"}";
            invokeFunc = insectWar;
        }
        else if(requestType.equals(compression)) {
            json = "{\"body\":\""+ parameters.get("inputEncoded")+
            "\", \"targetFormat\":\""+ parameters.get("targetFormat")+
            "\", \"compressionFactor\":\""+ Float.parseFloat(parameters.get("compressionFactor"))+"\"}";
            invokeFunc = compression;
        }
        else {
            System.out.println("Invalid request type");
            return null;
        }
        SdkBytes payload = SdkBytes.fromUtf8String(json);

        InvokeRequest request = InvokeRequest.builder().functionName(invokeFunc).payload(payload).build();

        InvokeResponse res = awsLambda.invoke(request);
        String value;
        String response = res.payload().asUtf8String();
        if (requestType.equals(compression)){
            response = response.replace("\"", "");
            value = String.format("data:image/%s;base64,%s", parameters.get("targetFormat"), response);
        }
        else value = response;
        return value;
   }
}
