import java.util.concurrent.* // Available (white-listed) concurrency classes: ConcurrentHashMap, ConcurrentLinkedQueue, Semaphore, SynchronousQueue
import groovy.transform.Field

metadata {
	definition (name: "Test LZW36 Endpoint Supervision",namespace: "jvm", author: "jvm") {

		command "setRootLevel", [[name:"level",type:"NUMBER", description:"New Level", constraints:["NUMBER"]]]
		command "setEndpoint1Level", [[name:"level",type:"NUMBER", description:"New Level", constraints:["NUMBER"]]]
		command "setEndpoint2Level", [[name:"level",type:"NUMBER", description:"New Level", constraints:["NUMBER"]]]
    }
	
    preferences 
	{
			input name: "superviseEnable", type: "bool", title: "Enable Command Supervision if supported", defaultValue: true
    }	
}

void setRootLevel(level) 
{
	sendSupervised(zwave.switchMultilevelV4.switchMultilevelSet(value: level))
}
void setEndpoint1Level(level) 
{
	sendSupervised(zwave.switchMultilevelV4.switchMultilevelSet(value: level), 1 as Short)

}
void setEndpoint2Level(level) 
{
	sendSupervised(zwave.switchMultilevelV4.switchMultilevelSet(value: level), 2 as Short)
}
////    Send Simple Z-Wave Commands to Device  ////	

void zwaveEvent(hubitat.zwave.commands.switchmultilevelv4.SwitchMultilevelReport cmd, Short ep = null)
{
	log.debug "Received a switch MultiLelel Report: ${cmd}"
}

void sendSupervised(hubitat.zwave.Command cmd, Short ep = null ) { 

		String sendThis
		if (superviseEnable )		
		{
			sendThis = secure(supervise(cmd), ep)
			log.debug "Device ${device.displayName}: Sending supervised command: " + sendThis
		} else {
			sendThis = secure(cmd, ep)
			log.debug "Device ${device.displayName}: Sending Un-supervised command: " + sendThis
		}
		sendHubCommand(new hubitat.device.HubAction( sendThis, hubitat.device.Protocol.ZWAVE)) 
}
	
		
//////////////////////////////////////////////////////////////////////
//////        Handle Supervision request and reports           ///////
////////////////////////////////////////////////////////////////////// 

// @Field static results in variable being shared among all devices that use the same driver, so I use a concurrentHashMap keyed by a device's deviceNetworkId to get a unqiue value for a particular device
// supervisionSessionIDs stores the last used sessionID value (0..31) for a device. It must be incremented mod 32 on each send
// supervisionSentCommands stores the last command sent

@Field static ConcurrentHashMap<String, Short> supervisionSessionIDs = new ConcurrentHashMap<String, Short>(128)
@Field static ConcurrentHashMap<String, Short> supervisionSentCommands = new ConcurrentHashMap<String, ConcurrentHashMap<Short, hubitat.zwave.Command>>(128)

// supervisionRejected is a concurrentHashMap within a concurrentHashMap. A first HashMap is retrieved using the device's firmwareKey. This means that it is shared among all devices that have the same manufacturere, device Type, device ID, and firmware version
// That first map is then a map of all of the commands (cmd.CMD) that have been rejectefd. So, if a command is rejected as not supervisable, it doesn't get sent using supervision by any similar device
@Field static ConcurrentHashMap<String, ConcurrentHashMap> supervisionRejected = new ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>>(128)

Boolean commandSupervisionNotSupported(hubitat.zwave.Command cmd) {	
	Boolean previouslyRejected = ( supervisionRejected.get(firmwareKey())?.get(cmd.CMD) ) ? true : false 
	if (logEnable && previouslyRejected) log.debug "Device ${device.displayName}: Attempted to supervise a class ${cmd.CMD} which was previously rejected as not supervisable."
	return previouslyRejected 
}

void markSupervisionNotSupported(hubitat.zwave.Command cmd) {	
	supervisionRejected.get(firmwareKey(), new ConcurrentHashMap<String, Boolean>(32) ).put(cmd.CMD, true )
}

def supervise(hubitat.zwave.Command command)
{
		// Get the next session ID mod 32, but if there is no stored session ID, initialize it with a random value.
		Short nextSessionID = supervisionSessionIDs.get(device.getDeviceNetworkId() as String,((Math.random() * 32) % 32) as Short )
		nextSessionID = (nextSessionID + 1) % 32 // increment and then mod with 32
		supervisionSessionIDs.replace(device.getDeviceNetworkId(), nextSessionID)
		
		// Store the command that is being sent so that you can log.debug it and resend in case of failure!
		supervisionSentCommands.get(device.getDeviceNetworkId() as String, new ConcurrentHashMap<Short, hubitat.zwave.Command>(128)).put(nextSessionID, command)

		log.debug "Supervising a command: ${command} with session ID: ${nextSessionID}."
		return zwave.supervisionV1.supervisionGet(sessionID: nextSessionID, statusUpdates: false ).encapsulate(command)
}

// This handles a supervised message (a "get") received from the Z-Wave device //
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, Short ep = null ) {
	
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(parseMap, defaultParseMap)
	
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand, ep)
    }
    sendToDevice((new hubitat.zwave.commands.supervisionv1.SupervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0)), ep)
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionReport cmd, Short ep = null ) {
	
	hubitat.zwave.Command whatWasSent = supervisionSentCommands?.get(device.getDeviceNetworkId() as String)?.get(cmd.sessionID)

	switch (cmd.status)
	{
		case 0x00:
			log.error "Device ${device.displayName}: Received Supervision Report with status of 0 indicating Z-Wave Command supervision not supported for: ${whatWasSent}."
			log.info "Re-sending without supervision command: ${whatWasSent}"
			sendToDevice(whatWasSent, ep )
			break
		case 0x01:
			log.warn "Device ${device.displayName}: Received Supervision Report with status of 1 indicating Z-Wave Command still processing for: ${whatWasSent}"
		case 0x02:
			log.error "Device ${device.displayName}: Received Supervision Report with status of 2 indicating Z-Wave supervised command reported failure. Failed command: ${whatWasSent}."
			log.info "Re-sending without supervision command: ${whatWasSent}"
			sendToDevice(whatWasSent, ep )
			break
		case 0xFF:
			log.info "Device ${device.displayName}: Device successfully processed supervised command ${whatWasSent}."
			break
	}
}

//////////////////////////////////////////////////////////////////////
//////                  Z-Wave Helper Functions                ///////
//////   Format messages, Send to Device, secure Messages      ///////
//////////////////////////////////////////////////////////////////////
Map getDefaultParseMap()
{
return [
	0x20:2, // Basic Set
	0x25:2, // Switch Binary
	0x26:4, // Switch MultiLevel 
	0x31:11, // Sensor MultiLevel
	0x32:2, // Meter
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

//// Catch Event Not Otherwise Handled! /////

void zwaveEvent(hubitat.zwave.Command cmd, Short ep = null) {
    log.warn "For ${device.displayName}, Received Z-Wave Message ${cmd} that is not handled by this driver. Endpoint: ${ep}. Message class: ${cmd.class}."
}

////    Hail   ////
void zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
	refresh()
}

////    Security Encapsulation   ////
void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand( parseMap, defaultParseMap )
    if (encapsulatedCommand) { zwaveEvent(encapsulatedCommand) }
}

String secure(Integer cmd, Integer hexBytes = 2, Short ep = null ) { 
    return secure(hubitat.helper.HexUtils.integerToHexString(cmd, hexBytes), ep) 
}

String secure(String cmd, Short ep = null ){ 
	if (ep) {
		return zwaveSecureEncap(zwave.multiChannelV4.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(cmd))
	} else {
		return zwaveSecureEncap(cmd) 
	}
}

String secure(hubitat.zwave.Command cmd, Short ep = null ){ 
	if (ep) {
		return zwaveSecureEncap(zwave.multiChannelV4.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(cmd))
	} else {
		return zwaveSecureEncap(cmd) 
	}
}

////    Multi-Channel Encapsulation   ////
void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCmdEncap cmd) {
    hubitat.zwave.Command  encapsulatedCommand = cmd.encapsulatedCommand(defaultParseMap)
    if (encapsulatedCommand) { zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Short) }
}

////    Z-Wave Message Parsing   ////
void parse(String description) {
	try {
		hubitat.zwave.Command cmd = zwave.parse(description, defaultParseMap)
		if (cmd) { zwaveEvent(cmd) }
	}
	catch (ex) {
		log.error "Device ${device.displayName}: Error in parse() attempting to parse input: ${description}. \nError: ${ex}. \nStack trace is: ${getStackTrace(ex)}.\nException message is ${getExceptionMessageWithLine(ex)}. \nParse map is: ${defaultParseMap}."
	}
}

////    Z-Wave Message Sending to Hub  ////
void sendToDevice(List<hubitat.zwave.Command> cmds) { sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds), hubitat.device.Protocol.ZWAVE)) }

void sendToDevice(hubitat.zwave.Command cmd, Short ep = null ) { sendHubCommand(new hubitat.device.HubAction(secure(cmd, ep), hubitat.device.Protocol.ZWAVE)) }


void sendToDevice(String cmd) { sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE)) }



