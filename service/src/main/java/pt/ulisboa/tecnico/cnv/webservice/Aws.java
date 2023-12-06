package pt.ulisboa.tecnico.cnv.webservice;

import java.io.FileInputStream;

import java.util.Properties;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.amazonaws.util.EC2MetadataUtils;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.Datapoint;

public class Aws {
    private static String id;

    private static AmazonEC2 ec2Client;

    private static AmazonCloudWatch cloudWatch;

    private static String amiId;

    public static class InstanceInfo {
        public Instance instance;
        public int requests = 0;
        public boolean terminate = false;
        public boolean fresh = true;
        public double cpu = 0;
        public double work = 0;
    }
    private static ConcurrentHashMap<String, InstanceInfo> instances;
    private static AtomicInteger instancesNum;

    public Aws(String credentialsFile) {
        id = EC2MetadataUtils.getInstanceId();
        try {
            ec2Client = AmazonEC2ClientBuilder.standard()
                .withRegion("us-east-1")
                .withCredentials(new ProfileCredentialsProvider(credentialsFile, "default"))
                .build();
        }
        catch (Exception e) {
            System.err.println("Error creating ec2 client from file.");
            e.printStackTrace();
            System.exit(1);
        }
        try {
            cloudWatch = AmazonCloudWatchClientBuilder.standard()
                .withRegion("us-east-1")
                .withCredentials(new ProfileCredentialsProvider(credentialsFile, "default"))
                .build();
        }
        catch (Exception e) {
            System.err.println("Error creating cloud watch from file.");
            e.printStackTrace();
            System.exit(1);
        }

        DescribeImagesRequest request = new DescribeImagesRequest().withOwners("self");
        DescribeImagesResult result = ec2Client.describeImages(request);
        amiId = result.getImages().get(0).getImageId();

        instances = new ConcurrentHashMap<>();
        getInstances();
        instancesNum = new AtomicInteger(instances.size());
    }

    public ConcurrentHashMap<String, InstanceInfo> instances() {
        return instances;
    }

    public AtomicInteger instancesNum() {
        return instancesNum;
    }

    private void getInstances() {
        for (Reservation reservation : ec2Client.describeInstances().getReservations()) {
            for (Instance foundInstance : reservation.getInstances()) {
                String foundInstanceId = foundInstance.getInstanceId();
                if (!foundInstanceId.equals(id) && foundInstance.getState().getCode() == 16) {
                    InstanceInfo instanceInfo;
                    if (!instances.containsKey(foundInstanceId)) {
                        instanceInfo = new InstanceInfo();
                    }
                    else {
                        instanceInfo = instances.get(foundInstanceId);
                    }
                    instanceInfo.instance = foundInstance;
                    instances.put(foundInstanceId, instanceInfo);
                }
            }
        }
    }

    public void launchInstance(String propertiesFile) {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(propertiesFile));
        } 
        catch (Exception e) {
            System.err.println("Error loading properties from file.");
            e.printStackTrace();
            System.exit(1);
        }
        RunInstancesRequest request = new RunInstancesRequest();
        request.withImageId(amiId)
            .withInstanceType(properties.getProperty("instanceType"))
            .withKeyName(properties.getProperty("keyName"))
            .withSecurityGroups(properties.getProperty("securityGroup"))
            .withMonitoring(true)
            .withMaxCount(1)
            .withMinCount(1);
        ec2Client.runInstances(request);
        System.out.println("Launching an instance.");
        instancesNum.getAndIncrement();
    }

    public void terminateInstance(String instanceId) {
        TerminateInstancesRequest request = new TerminateInstancesRequest().withInstanceIds(instanceId);
        ec2Client.terminateInstances(request);

        while (statusInstance(instanceId) == 16) {
            try {
                Thread.sleep(1);
            } 
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        instances.remove(instanceId);
        System.out.println("Terminating an instance.");
        instancesNum.getAndDecrement();
    }

    public int statusInstance(String instanceId) {
        DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(instanceId);
        DescribeInstancesResult result = ec2Client.describeInstances(request);
        if (result.getReservations().isEmpty()) {
            return -1;
        } 
        else {
            return result.getReservations().get(0).getInstances().get(0).getState().getCode();
        }
    }

    public void getCpu() {
        getInstances();

        Dimension instanceDimension = new Dimension();
        instanceDimension.setName("InstanceId");
        for (Map.Entry<String, InstanceInfo> entry : instances.entrySet()) {
            instanceDimension.setValue(entry.getValue().instance.getInstanceId());
            GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                .withStartTime(new Date(new Date().getTime() - (1000 * 60 * 10)))
                .withNamespace("AWS/EC2")
                .withPeriod(60)
                .withMetricName("CPUUtilization")
                .withStatistics("Average")
                .withDimensions(instanceDimension)
                .withEndTime(new Date());
            GetMetricStatisticsResult result = cloudWatch.getMetricStatistics(request);

            Datapoint lastDatapoint = null;
            for (Datapoint datapoint : result.getDatapoints()) {
                if (lastDatapoint == null || lastDatapoint.getTimestamp().before(datapoint.getTimestamp())) {
                    lastDatapoint = datapoint;
                }
            }

            double average = 0;
            if (lastDatapoint != null) {
                average = lastDatapoint.getAverage();
                entry.getValue().fresh = false;
            }
            entry.getValue().cpu = average;
        }
    }

    public InstanceInfo leastUsedInstance() {
        getInstances();

        InstanceInfo instanceInfo = null;
        double workMin = -1;
        for (Map.Entry<String, InstanceInfo> entry : instances.entrySet()) {
            if (workMin == -1 || (entry.getValue().work < workMin && !entry.getValue().terminate)) {
                instanceInfo = entry.getValue();
                workMin = entry.getValue().work;
            }
        }
        return instanceInfo;
    }
}
