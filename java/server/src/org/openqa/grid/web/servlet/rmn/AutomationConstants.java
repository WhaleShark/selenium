package org.openqa.grid.web.servlet.rmn;

/**
 * Created with IntelliJ IDEA. User: mhardin Date: 1/15/14 Time: 8:54 AM To change this template use
 * File | Settings | File Templates.
 */
public interface AutomationConstants {

    // This is the value that will be in the desired capabilities that our node registers with
    String INSTANCE_ID_DESIRED_CAPABILITIES = "instance_id";
    // Runtime value of the hub instance id that gets passed in as a system property
    String INSTANCE_ID = "instanceId";
    // IP address of the hub that will be passed in as a system property
    String IP_ADDRESS = "ipAddress";
    // Test run UUID
    String UUID = "uuid";

}
