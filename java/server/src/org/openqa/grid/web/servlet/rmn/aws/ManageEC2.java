package org.openqa.grid.web.servlet.rmn.aws;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.InstanceStateChange;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.sun.org.apache.xml.internal.security.utils.Base64;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * User: mmerrell
 * Date: 12/18/13
 */
public class ManageEC2 {
    private static final Logger log = Logger.getLogger(ManageEC2.class.getName());
    private AmazonEC2Client client;
    private Properties awsProperties;

    private String region;

    public ManageEC2() {
        // Default the the east region
        this("east");
    }

    public ManageEC2(String region) {
        this.region = region;
        awsProperties = initAWSProperties();
        BasicAWSCredentials creds = getCredentials();
        client = new AmazonEC2Client(creds);
        client.setEndpoint(awsProperties.getProperty(region + "_endpoint"));
    }

    /**
     * Inits the AWS properties from the specified file
     * @return
     */
    private Properties initAWSProperties() {
        Properties properties = new Properties();
        String propertiesLocation = System.getProperty("propertyFileLocation");
        System.out.print(System.getProperty("user.dir"));
        if(propertiesLocation == null) {
            throw new RuntimeException("propertyFileLocation property must be set");
        }
        try {
            File f = new File(propertiesLocation);
            InputStream is = new FileInputStream(f);
            properties.load(is);
            } catch (IOException e) {
                log.severe("Could not load aws.properties" + e);
        }
        return properties;
    }

    private BasicAWSCredentials getCredentials() {
        return new BasicAWSCredentials(awsProperties.getProperty("access_key"),awsProperties.getProperty("secret_key"));
    }

    /**
     * Creates an instance of the Grid Hub (as specified by the aws.properties file)
     * @return The Instance
     */
    public List<Instance> launchChromeGridNode(String uuid, String os, String hostName, int nodeCount) {

        String nodeConfig = getNodeConfig(uuid,hostName);
        log.info("Node Config: " + nodeConfig);

        RunInstancesRequest runRequest = new RunInstancesRequest();
        runRequest
                .withImageId(awsProperties.getProperty(region + "_linux_node_ami"))
                .withInstanceType(awsProperties.getProperty("node_instance_type"))
                .withMinCount(1)
                .withMaxCount(nodeCount)
                .withUserData(nodeConfig)
                .withKeyName("grid-test-" + region)
                .withSubnetId(awsProperties.getProperty(region + "_subnet_id"))
                .withSecurityGroupIds(awsProperties.getProperty(region + "_security_group"))
        ;
        // Set AMI name depending on OS
        getRequestForOs(runRequest,os);
        log.info("Sending run request to AWS...");
        RunInstancesResult runInstancesResult = client.runInstances(runRequest);

        log.info("Run request result returned.  Adding tags");
        //Tag the instances with the standard RMN AWS data
        List<Instance> instances = runInstancesResult.getReservation().getInstances();
        for(Instance instance : instances) {
            String instanceId = instance.getInstanceId();
            CreateTagsRequest ctr = setTagsForInstance(instanceId);
            client.createTags(ctr);
        }

        return instances;
    }

    private CreateTagsRequest setTagsForInstance(String instanceId) {
        List<Tag> tags = new ArrayList<Tag>();

        Tag accountingTag = new Tag();
        accountingTag.setKey("Accounting");
        accountingTag.setValue(awsProperties.getProperty("accounting_tag"));

        Tag functionTag = new Tag();
        functionTag.setKey("Function");
        functionTag.setValue(awsProperties.getProperty("function_tag"));

        Tag productTag = new Tag();
        productTag.setKey("Product");
        productTag.setValue(awsProperties.getProperty("product_tag"));

        tags.add(accountingTag);
        tags.add(functionTag);
        tags.add(productTag);

        CreateTagsRequest ctr = new CreateTagsRequest();
        ctr.setTags(tags);
        ctr.withResources(instanceId);
        return ctr;
    }

    private RunInstancesRequest getRequestForOs(RunInstancesRequest request, String os) {
        // Unspecified OS will default to Ubuntu
        if (null == os) {
            os = "ubuntu";
        }
        String requestedProperty;
        if(os.equals("ubuntu")) {
            requestedProperty = region + "_linux_node_ami";
        } else if(os.equals("windows")) {
            requestedProperty = region + "_windows_node_ami";
        } else {
            throw new RuntimeException("Unsupported OS: " + os);
        }

        request.withImageId(awsProperties.getProperty(requestedProperty));
        return request;
    }

    /**
     * Terminates the specified instance
     * @param instanceId
     */
    public void terminateInstance(String instanceId) {
        TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest();
        terminateRequest.withInstanceIds(instanceId);
        TerminateInstancesResult result = client.terminateInstances(terminateRequest);
        for(InstanceStateChange stateChange : result.getTerminatingInstances()) {
            if(instanceId.equals(stateChange.getInstanceId())) {
                InstanceState currentState = stateChange.getCurrentState();
                if(currentState.getCode() != 32 && currentState.getCode() != 48) {
                    log.severe(String.format("Machine state for id %s should be terminated (48) or shutting down (32) but was %s instead",instanceId,currentState.getCode()));
                }
            }
        }
    }

    /**
     * Reads the hub.json file and returns its contents as a Base64-encoded string
     * @return
     */
    private String getNodeConfig(String uuid,String hostName) {
        String location = System.getProperty("nodeConfig");
        if(location == null) {
            throw new RuntimeException("Property 'nodeConfig' cannot be null");
        }
        String nodeConfig = getFileContents(location);
        nodeConfig = nodeConfig.replaceAll("<MAX_SESSION>", "6");
        nodeConfig = nodeConfig.replaceAll("<UUID>", uuid);
        nodeConfig = nodeConfig.replaceFirst("<HOST_NAME>", hostName);

        return Base64.encode(nodeConfig.getBytes());
    }

    /**
     * Returns the contents as a String from the specified file
     * @param fileName
     * @return
     */
    private String getFileContents(String fileName) {
        String fileContents = "";
        try {
            File f = new File(fileName);
            InputStream stream = new FileInputStream(f);
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            String strLine;
            while ( ( strLine = br.readLine() ) != null )   {
                fileContents += strLine;
            }
            stream.close();
        } catch (IOException e) {
            throw new RuntimeException( "Could not read file - " + fileName + " - " + e );
        }
        return fileContents;
    }
}
