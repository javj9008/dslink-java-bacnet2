package bacnet;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.npdu.Network;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkUtils;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.primitive.OctetString;

public class BacnetIpConn extends BacnetConn {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(BacnetIpConn.class);
	
	String subnetMask;
	int port;
	String localBindAddress;
	boolean isRegisteredAsForeignDevice;
	String bbmdIpList;

	BacnetIpConn(BacnetLink link, Node node) {
		super(link, node);
	}

	@Override
	Network getNetwork() {
		return new IpNetworkBuilder().withSubnetMask(subnetMask).withPort(port).withLocalBindAddress(localBindAddress).withLocalNetworkNumber(localNetworkNumber).build();
	}
	
	void parseBroadcastManagementDevice() {
		String bbmdIp = null;
		int bbmdPort = IpNetwork.DEFAULT_PORT;
		int networkNumber = 0;
		for (String entry : bbmdIpList.split(",")) {
			entry = entry.trim();
			if (!entry.isEmpty()) {
				Pattern p = Pattern.compile("^\\s*(.*?):(\\d+):(\\d+)$");
				Matcher m = p.matcher(entry);
				if (m.matches()) {
					bbmdIp = m.group(1);
					bbmdPort = Integer.parseInt(m.group(2));
					networkNumber = Integer.parseInt(m.group(3));
					if (!bbmdIp.isEmpty()) {
						bbmdIpToPort.put(bbmdIp, bbmdPort);
						OctetString os = IpNetworkUtils.toOctetString(bbmdIp, bbmdPort);
						networkRouters.put(networkNumber, os);
					}
				}
			}
		}
	}
	
	@Override
	void registerAsForeignDevice(Transport transport) {
		Network network = transport.getNetwork();
		if (!isRegisteredAsForeignDevice)
			return;
		
		for (Map.Entry<Integer, OctetString> entry : networkRouters.entrySet()) {
			Integer networkNumber = entry.getKey();
			OctetString linkService = entry.getValue();
			transport.addNetworkRouter(networkNumber, linkService);
		}

		for (Map.Entry<String, Integer> entry : bbmdIpToPort.entrySet()) {
			String bbmdIp = entry.getKey();
			Integer bbmdPort = entry.getValue();
			try {
				((IpNetwork) network).registerAsForeignDevice(
						new InetSocketAddress(InetAddress.getByName(bbmdIp), bbmdPort), 100);
			} catch (UnknownHostException e) {
				LOGGER.debug("", e);
			} catch (BACnetException e) {
				LOGGER.debug("", e);
			}
		}

	}
	
	@Override
	protected void makeEditAction() {
		Action act = new Action(Permission.READ, new Handler<ActionResult>(){
			@Override
			public void handle(ActionResult event) {
				edit(event);
			}
		});
		act.addParameter(new Parameter("Subnet Mask", ValueType.STRING, new Value(subnetMask)));
		act.addParameter(new Parameter("Port", ValueType.NUMBER, new Value(port)));
		act.addParameter(new Parameter("Local Bind Address", ValueType.STRING, new Value(localBindAddress)));
		act.addParameter(new Parameter("Local Network Number", ValueType.NUMBER, new Value(localNetworkNumber)));
		act.addParameter(new Parameter("Register As Foreign Device In BBMD", ValueType.BOOL, new Value(isRegisteredAsForeignDevice)));
		act.addParameter(new Parameter("BBMD IPs With Network Number", ValueType.STRING, new Value(bbmdIpList)));
//		act.addParameter(new Parameter("strict device comparisons", ValueType.BOOL, new Value()));
		act.addParameter(new Parameter("Timeout", ValueType.NUMBER, new Value(timeout)));
		act.addParameter(new Parameter("Segment Timeout", ValueType.NUMBER, new Value(segmentTimeout)));
		act.addParameter(new Parameter("Segment Window", ValueType.NUMBER, new Value(segmentWindow)));
		act.addParameter(new Parameter("Retries", ValueType.NUMBER, new Value(retries)));
		act.addParameter(new Parameter("Local Device ID", ValueType.NUMBER, new Value(localDeviceId)));
		act.addParameter(new Parameter("Local Device Name", ValueType.STRING, new Value(localDeviceName)));
		act.addParameter(new Parameter("Local Device Vendor", ValueType.STRING, new Value(localDeviceVendor)));
		
		Node anode = node.getChild(ACTION_EDIT, true);
		if (anode == null) {
			node.createChild(ACTION_EDIT, true).setAction(act).build().setSerializable(false);
		} else {
			anode.setAction(act);
		}
	}
	
	@Override
	protected void setVarsFromConfigs() {
		super.setVarsFromConfigs();
		subnetMask = Utils.safeGetRoConfigString(node, "Subnet Mask", subnetMask);
		port = Utils.safeGetRoConfigNum(node, "Port", port).intValue();
		localBindAddress = Utils.safeGetRoConfigString(node, "Local Bind Address", localBindAddress);
		isRegisteredAsForeignDevice = Utils.safeGetRoConfigBool(node, "Register As Foreign Device In BBMD", isRegisteredAsForeignDevice);
		bbmdIpList = Utils.safeGetRoConfigString(node, "BBMD IPs With Network Number", bbmdIpList);
		parseBroadcastManagementDevice();
	}
	
}