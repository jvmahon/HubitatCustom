import java.util.concurrent.* // Available (white-listed) concurrency classes: ConcurrentHashMap, ConcurrentLinkedQueue, Semaphore, SynchronousQueue
import groovy.transform.Field

metadata {
	definition (name: "[Test] Save and Replay Reports",namespace: "jvm", author: "jvm") {
		command "getVersionReport"
		command "logCachedVersionReport"
		command "replayVersionReport"
    }
}

@Field static SynchronousQueue transferQueue = new SynchronousQueue()
@Field static hubitat.zwave.Command cachedVersionReport // Store a received report

hubitat.zwave.Command getVersionReport()
{
	sendToDevice(secure(zwave.versionV3.versionGet()))
	
	cachedVersionReport = transferQueue.poll(10, TimeUnit.SECONDS)
	
	log.debug "Received and cached a VersionReport in function getVersionReport. Report is ${cachedVersionReport}."
}

hubitat.zwave.Command replayVersionReport()
{
	// Resend the report
	zwaveEvent(cachedVersionReport)
	
	// Wait for it to be transferred back
	cachedVersionReport = transferQueue.poll(10, TimeUnit.SECONDS)
	
	// Log it
	log.debug "cachedVersionReport in function replayVersionReport is ${cachedVersionReport}."
}

void logCachedVersionReport()
{
	log.debug "cached VersionReport is ${cachedVersionReport} with payload ${cachedVersionReport.payload} and format ${cachedVersionReport.format()}."
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd, ep = null ) 
{	
	log.debug "Received a VersionReport and attempting to transfer it within 10 seconds."
	Boolean transferredReport = transferQueue.offer(cmd, 10, TimeUnit.SECONDS)
	log.debug "Transfer Success = ${transferredReport},  Report is: ${cmd}."
}




Map getDefaultParseMap()
{
	return [
		0x20:2, // Basic Set
		0x25:2, // Switch Binary
		0x26:4, // Switch MultiLevel 
		0x31:11, // Sensor MultiLevel
		0x32:5, // Meter
		0x5B:3,	// Central Scene
		0x60:4,	// MultiChannel
		0x62:1,	// Door Lock
		0x63:1,	// User Code
		0x6C:1,	// Supervision
		0x71:8, // Notification
		0x80:1, // Battery
		0x86:3,	// Version
		0x98:1,	// Security
		0x9B:2,	// Configuration
		0x87:3  // Indicator
		]
}

// ==================================================


//////////////////////////////////////////////////////////////////////
//////                  Z-Wave Helper Functions                ///////
//////   Format messages, Send to Device, secure Messages      ///////
//////////////////////////////////////////////////////////////////////


////    Security Encapsulation   ////
void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) 
{
	hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand( parseMap, defaultParseMap )

    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

String secure(Integer cmd, Integer hexBytes = 2, ep = null ) { 
    return secure(hubitat.helper.HexUtils.integerToHexString(cmd, hexBytes), ep) 
}

String secure(String cmd, ep = null ){ 
	if (ep) {
		return zwaveSecureEncap(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(cmd))
	} else {
		return zwaveSecureEncap(cmd) 
	}
}

String secure(hubitat.zwave.Command cmd, ep = null ){ 
	if (ep) {
		return zwaveSecureEncap(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(cmd))
	} else {
		return zwaveSecureEncap(cmd) 
	}
}

////    Multi-Channel Encapsulation   ////
void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCmdEncap cmd)
{
    hubitat.zwave.Command  encapsulatedCommand = cmd.encapsulatedCommand(defaultParseMap)

    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Short)
    }
}

////    Z-Wave Message Parsing   ////
void parse(String description) {
	hubitat.zwave.Command cmd = zwave.parse(description, defaultParseMap)
    if (cmd) { zwaveEvent(cmd) }
}

////    Z-Wave Message Sending to Hub  ////
void sendToDevice(List<hubitat.zwave.Command> cmds) { sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds), hubitat.device.Protocol.ZWAVE)) }
void sendToDevice(hubitat.zwave.Command cmd) { sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE)) }
void sendToDevice(String cmd) { sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE)) }

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) { return delayBetween(cmds.collect{ it }, delay) }

