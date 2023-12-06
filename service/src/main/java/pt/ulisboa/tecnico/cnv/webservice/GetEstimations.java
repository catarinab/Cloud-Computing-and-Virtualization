package pt.ulisboa.tecnico.cnv.webservice;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Arrays;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;

public class GetEstimations {

    public class DynamoDBInfo {
        public String requestID;
        public String type;
        public int att1;
        public int att2;
        public float att3;
        public long ninsts = 0;
        public long nloadstore = 0;
        public long cpuTime = 0;

        public DynamoDBInfo(Map<String, AttributeValue> item) {
            this.requestID = item.get("requestID").getS();
            this.type = item.get("requestType").getS();
            this.att1 = Integer.valueOf(item.get("att1").getN());
            this.att2 = Integer.valueOf(item.get("att2").getN());
            this.att3 = Float.valueOf(item.get("att3").getN());
            this.ninsts = Long.valueOf(item.get("ninsts").getN());
            this.nloadstore = Long.valueOf(item.get("nloadstore").getN());
            this.cpuTime = Long.valueOf(item.get("cpuTime").getN());
        }

        public float getSum() {
            return this.att1 + this.att2 + this.att3;
        }

        public String toString() {
            return "requestID: " + requestID + " type: " + type + " att1: " + att1 
            + " att2: " + att2 + " att3: " + att3 + " ninsts: " + ninsts + 
            " nloadstore: " + nloadstore + " cpuTime: " + cpuTime;
        }
    }

    public class SortByAtt1 implements Comparator<DynamoDBInfo> {
        private int att1;

        public SortByAtt1(int att1) {
            this.att1 = att1;
        }

        @Override
        public int compare(DynamoDBInfo o1, DynamoDBInfo o2) {
            return Integer.compare(Math.abs(o1.att1 - att1), Math.abs(o2.att1 - att1));
        }
    }

    public class SortBySum implements Comparator<DynamoDBInfo> {
        private float sum;

        public SortBySum(float sum) {
            this.sum = sum;
        }

        @Override
        public int compare(DynamoDBInfo o1, DynamoDBInfo o2) {
            return Float.compare(o2.getSum() - this.sum, o1.getSum() - this.sum);
        }
    }


    public static String tableName = "requests-table";

    //statistically determined values of instruction per generation
    List<List<Integer>> instPerGen = Arrays.asList(Arrays.asList(9454, 10586, 10743), Arrays.asList(34667, 37747, 38015), 
                                    Arrays.asList(215680, 139088, 147282), Arrays.asList(325394, 325579, 32870));

    private static AmazonDynamoDB dynamoDB;

    public GetEstimations(String credentials) {
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
            .withCredentials(new ProfileCredentialsProvider(credentials, "default"))
            .withRegion("us-east-1")
            .build();

        // Create a table with a primary hash key named 'name', which holds a string
        CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
            .withKeySchema(new KeySchemaElement().withAttributeName("requestID").withKeyType(KeyType.HASH))
            .withAttributeDefinitions(new AttributeDefinition().withAttributeName("requestID").withAttributeType(ScalarAttributeType.S))
            .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

        // Create table if it does not exist yet
        TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
    }

    
    private long getFoxesRabbitEstimation(int att1, int att2, float att3) {
        String requestType = "foxes-rabbits";
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<String, AttributeValue>();
        expressionAttributeValues.put(":requestType", new AttributeValue(requestType));
        expressionAttributeValues.put(":att1", new AttributeValue().withN(String.valueOf(att1)));
        expressionAttributeValues.put(":att2", new AttributeValue().withN(String.valueOf(att2)));
        expressionAttributeValues.put(":att3", new AttributeValue().withN(String.valueOf(att3)));

        ScanRequest scanRequest = new ScanRequest()
        .withTableName(tableName)
        .withFilterExpression("requestType = :requestType AND att1 = :att1 AND att2 = :att2 AND att3 = :att3")
        .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult result = dynamoDB.scan(scanRequest);

        if(result.getCount() != 0) return Long.valueOf(result.getItems().get(0).get("ninsts").getN());

        //use statistically determined values to estimate
        System.out.println("Statistically determining");
        return instPerGen.get(att2-1).get((int)att3-1) * att1;
    }

    private long getInsectWarEstimation(int att1, int att2, float att3) {
        String requestType = "insect-war";
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<String, AttributeValue>();
        ArrayList<DynamoDBInfo> items = new ArrayList<DynamoDBInfo>();
        expressionAttributeValues.put(":requestType", new AttributeValue(requestType));
        expressionAttributeValues.put(":att1", new AttributeValue().withN(String.valueOf(att1)));
        expressionAttributeValues.put(":att2", new AttributeValue().withN(String.valueOf(att2)));
        expressionAttributeValues.put(":att3", new AttributeValue().withN(String.valueOf(att3)));

        ScanRequest scanRequest = new ScanRequest()
        .withTableName(tableName)
        .withFilterExpression("(requestType = :requestType AND att1 = :att1 AND att2 = :att2 AND att3 = :att3) OR (requestType = :requestType AND att1 = :att1 AND att2 = :att3 AND att3 = :att2)")
        .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult result = dynamoDB.scan(scanRequest);
        if(result.getCount() != 0) return Long.valueOf(result.getItems().get(0).get("ninsts").getN());


        //try to estimate with one item of same army size
        expressionAttributeValues.remove(":att1");
        scanRequest = new ScanRequest()
        .withTableName(tableName)
        .withFilterExpression("(requestType = :requestType AND att2 = :att2 AND att3 = :att3) OR (requestType = :requestType AND att2 = :att3 AND att3 = :att2)")
        .withExpressionAttributeValues(expressionAttributeValues);
        result = dynamoDB.scan(scanRequest);
        
        if(result.getCount() != 0) {
            for(Map<String, AttributeValue> item : result.getItems()) items.add(new DynamoDBInfo(item));
            items.sort(new SortByAtt1(att1));
            float simulationRatio = (float) att1/Integer.valueOf(items.get(0).att1);
            float estimatedNinsts = Long.valueOf(items.get(0).ninsts) * simulationRatio;
            return (long) estimatedNinsts;
        }

        //try to estimate with items with same att1 + att2 + att3
        items = new ArrayList<DynamoDBInfo>();
        float sum = att1 + att2 + att3;
        expressionAttributeValues.remove(":att2");
        expressionAttributeValues.remove(":att3");
        scanRequest = new ScanRequest()
        .withTableName(tableName)
        .withFilterExpression("requestType = :requestType")
        .withExpressionAttributeValues(expressionAttributeValues);
        result = dynamoDB.scan(scanRequest);
        for(Map<String, AttributeValue> item : result.getItems()) {
            DynamoDBInfo dynamoDBInfo = new DynamoDBInfo(item);
            if(dynamoDBInfo.getSum() - sum <= 20 ) items.add(dynamoDBInfo);
        }

        items.sort(new SortBySum(sum));
        if(items.size() != 0) return items.get(0).ninsts;

        System.out.println("No similar items found in DynamoDB table.");
        return 0;
        
    }

    private long getCompressionEstimation(long att1, String format, float att3) {
        int att2 = 0;
        System.out.println("att1: " + att1);
        if(format.equals("jpeg")) att2 = 1;
        else if(format.equals("bmp")) att2 = 2;
        String requestType = "compression";
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<String, AttributeValue>();
        expressionAttributeValues.put(":requestType", new AttributeValue(requestType));
        expressionAttributeValues.put(":att1", new AttributeValue().withN(String.valueOf(att1)));
        expressionAttributeValues.put(":att2", new AttributeValue().withN(String.valueOf(att2)));
        expressionAttributeValues.put(":att3", new AttributeValue().withN(String.valueOf(att3)));

        ScanRequest scanRequest = new ScanRequest()
        .withTableName(tableName)
        .withFilterExpression("requestType = :requestType AND att1 = :att1 AND att2 = :att2 AND att3 = :att3")
        .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult result = dynamoDB.scan(scanRequest);

        if(result.getCount() != 0) return Long.valueOf(result.getItems().get(0).get("ninsts").getN());

        expressionAttributeValues.remove(":att3");

        scanRequest = new ScanRequest()
        .withTableName(tableName)
        .withFilterExpression("requestType = :requestType AND att1 = :att1 AND att2 = :att2")
        .withExpressionAttributeValues(expressionAttributeValues);

        result = dynamoDB.scan(scanRequest);

        if(result.getCount() != 0) {
            if(att1 == 2 || att3 != 1) return Long.valueOf(result.getItems().get(0).get("ninsts").getN());
            double ninsts = Long.valueOf(result.getItems().get(0).get("ninsts").getN())*1.2;
            return (long) ninsts;
        }  

        //determine linearly if jpeg or png
        if(att1 < 2) {
            expressionAttributeValues.remove(":att1");
            expressionAttributeValues.put(":att3", new AttributeValue().withN(String.valueOf(att3)));

            scanRequest = new ScanRequest()
            .withTableName(tableName)
            .withFilterExpression("requestType = :requestType AND att2 = :att2 AND att3 = :att3")
            .withExpressionAttributeValues(expressionAttributeValues);

            result = dynamoDB.scan(scanRequest);

            if(result.getCount() >= 2) {
                //x: tamanho, y:ninsts
                DynamoDBInfo item1 = new DynamoDBInfo(result.getItems().get(0));
                DynamoDBInfo item2 = new DynamoDBInfo(result.getItems().get(1));
                double coefficient = (item2.ninsts - item1.ninsts) / (item2.att1 - item1.att1);
                double shift = -(coefficient * item1.att1) +  item1.ninsts;
                double ninsts = (coefficient * att1 + shift);
                return (long) ninsts;
            }
        }
        return 0;
        
    }

    public static Map<String, String> queryToMap(String query) {
        if(query == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for(String param : query.split("&")) {
            String[] entry = param.split("=");
            if(entry.length > 1) {
                result.put(entry[0], entry[1]);
            }else{
                result.put(entry[0], "");
            }
        }
        return result;
    }

    public static String getRequestType(Map<String, String> parameters) {
        if(parameters.containsKey("world"))
            return "foxes-rabbit";
        if(parameters.containsKey("max"))
            return "insect-war";
        if(parameters.containsKey("targetFormat"))
            return "compression";
        return "error";
    }


    public long getEstimation(String requestType, Map<String, String> parameters) {
        try{
            switch(requestType){
                case "foxes-rabbit":
                    System.out.println("foxes-rabbit");
                    return getFoxesRabbitEstimation(Integer.parseInt(parameters.get("generations")), 
                    Integer.parseInt(parameters.get("world")), Float.parseFloat(parameters.get("scenario")));
                case "insect-war":
                    return getInsectWarEstimation(Integer.parseInt(parameters.get("max")), Integer.parseInt(parameters.get("army1")),
                    Float.parseFloat(parameters.get("army2")));
                case "compression":

                    return getCompressionEstimation(parameters.get("inputEncoded").length(), parameters.get("targetFormat"), 
                    Float.parseFloat(parameters.get("compressionFactor")));
            }
        } 
        catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error parsing query");
        }
        System.out.println("Error getting estimation");
        return 0;
    }
}
