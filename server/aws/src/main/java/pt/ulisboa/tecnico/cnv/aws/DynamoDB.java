package pt.ulisboa.tecnico.cnv.aws;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import pt.ulisboa.tecnico.cnv.javassist.tools.ICount.DynamoDBInfo;
import pt.ulisboa.tecnico.cnv.javassist.tools.ICount;

public class DynamoDB {
    public static String tableName = "requests-table";

    private static AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard()
            .withCredentials(new EnvironmentVariableCredentialsProvider())
            .withRegion("us-east-1")
            .build();
    
    public DynamoDB() {
        try {

            // Create a table with a primary hash key named 'name', which holds a string
            CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                .withKeySchema(new KeySchemaElement().withAttributeName("requestID").withKeyType(KeyType.HASH))
                .withAttributeDefinitions(new AttributeDefinition().withAttributeName("requestID").withAttributeType(ScalarAttributeType.S))
                .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

            // Create table if it does not exist yet
            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
            // wait for the table to move into ACTIVE state
            TableUtils.waitUntilActive(dynamoDB, tableName);

        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
        catch (InterruptedException ie) {
            System.out.println("Network was interrupted");
            System.out.println("Error Message: " + ie.getMessage());
        }
    }


    public void newItem() {
        try{
            DynamoDBInfo info = ICount.getDynamoDBInfo();
            Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
            System.out.println("Adding a new item...");
            item.put("requestID", new AttributeValue(info.requestID));
            item.put("requestType", new AttributeValue(info.type.toString()));
            item.put("att1", new AttributeValue().withN(Integer.toString(info.att1)));
            item.put("att2", new AttributeValue().withN(Integer.toString(info.att2)));
            item.put("att3", new AttributeValue().withN(Float.toString(info.att3)));
            item.put("ninsts", new AttributeValue().withN(Long.toString(info.insts)));
            item.put("nloadstore", new AttributeValue().withN(Long.toString(info.nloadstore)));
            item.put("cpuTime", new AttributeValue().withN(Long.toString(info.cpuTime)));
            dynamoDB.putItem(new PutItemRequest(tableName, item));
        }
        catch(InterruptedException e) {
            System.out.println("Network was interrupted");
        }
    }
}
