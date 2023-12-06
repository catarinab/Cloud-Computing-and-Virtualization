package pt.ulisboa.tecnico.cnv.webservice;

import java.util.Map;

/**
 * Class for Auto Scaler Group
 */
public class AutoScaler {

    private static final double minCpu = 40;

    public static final double maxWork = 400000000;

    private static Aws aws;

    private static String properties;

    public AutoScaler(Aws receivedAws, String receivedAwsProperties) {
        aws = receivedAws;
        properties = receivedAwsProperties;
    }

    public void autoscale() throws InterruptedException {
        while (true) {
            Thread.sleep(1000 * 60 * 1);
            scale();
            System.out.println("Auto-Scaled.");
        }
    }

    private void scale() {
        aws.getCpu();

        double avgCpu = 0;
        double avgWork = 0;
        int counter = 0;
        boolean newInstances = false;
        for (final Map.Entry<String, Aws.InstanceInfo> entry : aws.instances().entrySet()) {
            if (entry.getValue().terminate && entry.getValue().requests == 0) {
                new Thread(new Runnable() {
                    public void run() {
                        aws.terminateInstance(entry.getKey());
                    }
                }).start();
            }
            else if (!entry.getValue().terminate) {
                avgCpu += entry.getValue().cpu;
                avgWork += entry.getValue().work;
                counter++;
                if (entry.getValue().fresh) {
                    newInstances = true;
                }
            }
        }

        if (counter > 0) {
            avgCpu = avgCpu / counter;
            avgWork = avgWork / counter;
            if (!newInstances && avgWork > maxWork * 0.7) {
                aws.launchInstance(properties);
            } 
            else if (!newInstances && avgWork < maxWork * 0.3 && (counter > 1 || avgCpu < minCpu)) {
                aws.leastUsedInstance().terminate = true;
            }
        }
    }
}