import java.util.concurrent.* // Available (white-listed) concurrency classes: ConcurrentHashMap, ConcurrentLinkedQueue, Semaphore, SynchronousQueue
import groovy.transform.Field

@Field static reportQueues = new ConcurrentHashMap<String, ConcurrentHashMap>()

SynchronousQueue myReportQueue(String reportClass)
{
	ConcurrentHashMap thisDeviceQueues = reportQueues.get(device.deviceNetworkId, new ConcurrentHashMap<String,SynchronousQueue>())
	SynchronousQueue thisReportQueue = thisDeviceQueues.get(reportClass, new SynchronousQueue())
	return thisReportQueue
}

metadata {
	definition (name: "[Cache Version 6] SynchronousQueue Cache Reports",namespace: "jvm", author: "jvm") {
		capability "Configuration"
		capability "Initialize"
		capability "Refresh"
		capability "Switch"
		capability "SwitchLevel"
		
		command "PreCache"
		command "getCachedVersionReport"
		command "getCachedNotificationSupportedReport"
		command "getCachedMultiChannelEndPointReport"
		command "logStoredReportCache"
    }
    preferences 
	{
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: true
		
		// The following preferences are only for use while debugging. Remove them from final code
		input name: "remindEnable", type: "bool", title: "Enable Code To-Do Reminders", defaultValue: true
    }
}

void PreCache()
{
		getCachedVersionReport() 										

		List<Short> 	deviceClasses = getDataValue("inClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Short }
						deviceClasses += getDataValue("secureInClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Short }
		
		deviceClasses.each{ it -> 
							// log.debug "implements class ${it}: "
							implementsZwaveClass(it as Short)
						}
		getCachedCentralSceneSupportedReport()		
		getCachedNotificationSupportedReport()										
		getCachedMeterSupportedReport()																		
		getCachedProtectionSupportedReport()										
		getCachedSwitchMultilevelSupportedReport()				
		getCachedSensorMultilevelSupportedSensorReport()	
		
		if (implementsZwaveClass(0x60 as Short))
		{
			hubitat.zwave.Command endPointReport = getCachedMultiChannelEndPointReport()
			log.debug endPointReport
			for (Short endPoint = 1; endPoint < (endPointReport.endPoints as Short); endPoint++)
			{
				hubitat.zwave.Command report = getCachedMultiChannelCapabilityReport(endPoint as Short)
				log.debug "For endPoint ${endPoint}, supported classes are: " + report
				
				getCachedNotificationSupportedReport(endPoint)										
				getCachedMeterSupportedReport(endPoint)																		
				getCachedProtectionSupportedReport(endPoint)										
				getCachedSwitchMultilevelSupportedReport(endPoint)				
				getCachedSensorMultilevelSupportedSensorReport(endPoint)			
			}
		}
}


//////////////////////////////////////////////////////////////////////
//////        Report Pre-Caching Library Functions            ///////
////////////////////////////////////////////////////////////////////// 

hubitat.zwave.Command  getCachedVersionReport() 										
								{ getReportCachedByNetworkId(zwave.versionV1.versionGet(), null )}
								
hubitat.zwave.Command  getCachedNotificationSupportedReport(ep = null )					
								{ getReportCachedByProductId(zwave.notificationV8.notificationSupportedGet(), ep)}
								
hubitat.zwave.Command  getCachedMultiChannelEndPointReport()							
								{ getReportCachedByProductId(zwave.multiChannelV4.multiChannelEndPointGet(), null )}
								
hubitat.zwave.Command  getCachedMultiChannelCapabilityReport(Short ep) 					
								{ getReportCachedByProductId(zwave.multiChannelV4.multiChannelCapabilityGet(endPoint: ep), null )}
								
hubitat.zwave.Command  getCachedCentralSceneSupportedReport()							
								{ getReportCachedByProductId(zwave.centralSceneV3.centralSceneSupportedGet(), null ) }
								
hubitat.zwave.Command  getCachedMeterSupportedReport(ep = null )						
								{ getReportCachedByProductId(zwave.meterV5.meterSupportedGet(), ep) }
														
hubitat.zwave.Command  getCachedProtectionSupportedReport(ep = null )					
								{ getReportCachedByProductId(zwave.protectionV2.protectionSupportedGet(), ep) }
								
hubitat.zwave.Command  getCachedSwitchMultilevelSupportedReport(ep = null )				
								{ getReportCachedByProductId(zwave.switchMultilevelV3.switchMultilevelSupportedGet(), ep) }

hubitat.zwave.Command  getCachedSensorMultilevelSupportedSensorReport(ep = null )		
								{ getReportCachedByProductId(zwave.sensorMultilevelV11.sensorMultilevelSupportedGetSensor(), ep) }




@Field static ConcurrentHashMap<String, ConcurrentHashMap> reportsCachedByProductId = new ConcurrentHashMap<String, ConcurrentHashMap>(32)
@Field static ConcurrentHashMap<String, ConcurrentHashMap> reportsCachedByNetworkId = new ConcurrentHashMap<String, ConcurrentHashMap>(64)

///   Functions to generate keys used to access the concurrent Hash Maps and to store into the hash maps ///

String productKey() // Generates a key based on manufacturer / device / firmware. Data is shared among all similar end-devices.
{
	if (remindEnable) log.warn "productKey function should be updated with a hash based on inclusters as some devices may remove change their inclusters depending on pairing state. for example, Zooz Zen 18 motion sensor may or may not show with a battery!"
	
	String manufacturer = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("manufacturer").toInteger(), 2)
	String deviceType = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceType").toInteger(), 2)
	String deviceID = 		hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceId").toInteger(), 2) 

	String key = "${manufacturer}:${deviceType}:${deviceID}:"
	return key
}

String firmwareKey(String firmwareReport)
{
	hubitat.zwave.Command   versionReport = getCachedVersionReport() 
	if (!versionReport)  {
			log.warn "Device ${device.displayName} called firmwareKey function but firmware version is not cached! Device may not be operating correctly. Returning null."
			return null
		}
	return productKey() + versionReport?.format()
}

///////////////////////////////////////////
@Field static ConcurrentHashMap<String, ConcurrentHashMap> CommandClassVersionReportsByProductID = new ConcurrentHashMap<String, ConcurrentHashMap>(32)

hubitat.zwave.Command  getCachedVersionCommandClassReport(Short requestedCommandClass)		
{
	ConcurrentHashMap ClassReports = CommandClassVersionReportsByProductID.get(firmwareKey(), new ConcurrentHashMap<Short,ConcurrentHashMap>())

	hubitat.zwave.Command cmd = ClassReports?.get(requestedCommandClass)
	
	if (cmd) { 
		return cmd
	} else {
		sendToDevice(secure(zwave.versionV3.versionCommandClassGet(requestedCommandClass: requestedCommandClass )))
		cmd = myReportQueue("8614").poll(10, TimeUnit.SECONDS)
		ClassReports.put(cmd.requestedCommandClass, cmd)
	}
	return ClassReports?.get(requestedCommandClass)
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionCommandClassReport cmd, ep = null ) { myReportQueue(cmd.CMD).offer(cmd) }
///////////////////////////////////////////
/*
String reportHexStringToCommandHexString(String reportClass)
{
	Integer getCommandNumber = hubitat.helper.HexUtils.hexStringToInt(reportClass) - 1
	return hubitat.helper.HexUtils.integerToHexString(getCommandNumber, 2)
}
*/
String commandHexStringToReportHexString(String commandClass)
{
	Integer getReportNumber = hubitat.helper.HexUtils.hexStringToInt(commandClass) + 1
	return hubitat.helper.HexUtils.integerToHexString(getReportNumber, 2)
}


hubitat.zwave.Command   getReportCachedByNetworkId(Map options = [:], hubitat.zwave.Command getCmd, ep )  
{
	ConcurrentHashMap cacheForThisNetId = reportsCachedByNetworkId.get(device.deviceNetworkId, new ConcurrentHashMap<String,SynchronousQueue>())
	ConcurrentHashMap cacheForThisEndpoint = cacheForThisNetId?.get((ep ?: 0) as Short, new ConcurrentHashMap<String,Object>() )	
	
	String reportClass = commandHexStringToReportHexString(getCmd.CMD)		
	hubitat.zwave.Command cmd = cacheForThisEndpoint?.get(reportClass)
	
	if (cmd) { 
		log.debug "Returning / replaying cached command"
		return cmd
		// runInMillis(10, replayCommand, [overwrite:false, data: [cachedVersionReport, ep ] ] )
	} else {
		sendToDevice(secure(getCmd, ep))
		Map transferredData = myReportQueue(reportClass).poll(10, TimeUnit.SECONDS)
		cmd =  transferredData.report
		cacheForThisEndpoint.put(cmd.CMD, cmd)
	}
	return cacheForThisEndpoint?.get(reportClass)
}


hubitat.zwave.Command   getReportCachedByProductId(Map options = [:], hubitat.zwave.Command getCmd, Short ep)  
{
	if (!implementsZwaveClass(getCmd.commandClassId, ep))
		{
			log.debug "Command ${getCmd.commandClassId} not implemented by this device for endpoint ${ep ?: 0}: " + getCmd
			return null
		}
	Short subIndex
	switch (getCmd.CMD)
	{
		case "6009":
			subIndex = getCmd.endPoint
			break
		default :
			subIndex = ep ?: 0
			break
	}
	log.debug "In function getReportCachedByProductId, getting report using command ${getCmd}."
	ConcurrentHashMap cacheForThisProductId = reportsCachedByProductId.get(firmwareKey(), new ConcurrentHashMap<String,SynchronousQueue>())
	ConcurrentHashMap cacheForThisSubIndex = cacheForThisProductId?.get(subIndex, new ConcurrentHashMap<String,Object>() )	

	String reportClass = commandHexStringToReportHexString(getCmd.CMD)
	hubitat.zwave.Command cmd = cacheForThisSubIndex?.get(reportClass)
	
	if (cmd) { 
		return cmd
		// runInMillis(10, replayCommand, [overwrite:false, data: [cachedVersionReport, ep ] ] )
	} else {
		log.debug "sending to device a command : ${getCmd} to get report ${reportClass} for subIndex ${subIndex}."
		sendToDevice(secure(getCmd, ep))
		Map transferredData = myReportQueue(reportClass).poll(10, TimeUnit.SECONDS)
		log.debug "Transferred data is: " + transferredData
		cmd =  transferredData.report
		cacheForThisSubIndex.put(cmd.CMD, cmd)
	}
	return cacheForThisSubIndex?.get(reportClass)
}

void replayCommand(params)
{
    Short commandClass =  (params[0].commandClassId) as Short
    Short command =       (params[0].commandId) as Short
    List<Short> payload = (params[0].payload)
    Short ep = params[1]
    
    zwaveEvent(zwave.getCommand(commandClass, command, payload) , ep)
}

void logStoredReportCache()
{
	log.debug "report cache for items stored based on ProductId and firmware version is: " + reportsCachedByProductId
	log.debug "reportsCachedByNetworkId stored based on NetworkId is: " + reportsCachedByNetworkId
	log.debug "CommandClassVersionReportsByProductID is: " + CommandClassVersionReportsByProductID
}

/////////////////  Caching Functions To return Reports! //////////////////////////////

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneSupportedReport  cmd, ep = null )  				{ transferReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv5.MeterSupportedReport cmd, ep = null ) 								{ transferReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelEndPointReport  cmd, ep = null )  				{ transferReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationSupportedReport cmd, ep = null )    	 			{ transferReport(cmd, ep) }
		
void zwaveEvent(hubitat.zwave.commands.notificationv8.EventSupportedReport cmd, ep = null )    	 					{ transferReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.protectionv2.ProtectionSupportedReport  cmd, ep = null )   					{ transferReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelSupportedReport  cmd, ep = null )   		{ transferReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelSupportedSensorReport  cmd, ep = null ) 	{ transferReport(cmd, ep) }		
void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd, ep = null )  									{ transferReport(cmd, ep) }

Boolean transferReport(cmd, ep)
{ 
	Boolean transferredReport = myReportQueue(cmd.CMD).offer([report:cmd, endPoint:ep])
	if (transferredReport) log.debug "Successfully transferred version report to waiting receiver."
	else log.debug "Failed to transfer version report."
	return transferredReport
}

void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCapabilityReport  cmd)
{ 
	// This requires special processing. Endpoint is in the message, not passed as a parameter, but want to store it based on endpoint!
	Boolean transferredReport = myReportQueue(cmd.CMD).offer([report:cmd, endPoint:cmd.endPoint])
	if (transferredReport) log.debug "Successfully transferred version report to waiting receiver."
	else log.debug "Failed to transfer version report."
}


Integer implementsZwaveClass(Short commandClass, Short ep = null )
{
	List<Short> deviceClasses = getDataValue("inClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Short }
				deviceClasses += getDataValue("secureInClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Short }
	if (ep ) 
	{
		Boolean supportsEndpoints = deviceClasses.contains(0x60 as Short)
		Short numberOfEndPoints = getCachedMultiChannelEndPointReport().endPoints
		
		if (supportsEndpoints && (ep <= numberOfEndPoints))
		{
		report = getCachedMultiChannelCapabilityReport(ep)
		if(report.commandClass.contains(commandClass)) return getCachedVersionCommandClassReport(commandClass).commandClassVersion
		} else {
			log.warn "Device ${device.displayName}: called function implementsZwaveClass(commandClass = ${commandClass}, ep = ${ep}). Maximum endpoints supported by this device is: ${numberOfEndPoints ? numberOfEndPoints : 0}" 
			return null
		}
		
	} else { 

		if (deviceClasses.contains(commandClass))
		{
			return getCachedVersionCommandClassReport(commandClass).commandClassVersion
		}	
	} 
	return null
}
		
//////////////////////////////////////////////////////////////////////
//////        Handle Supervision request and reports           ///////
////////////////////////////////////////////////////////////////////// 
@Field static ConcurrentHashMap<String, Short> supervisionSessionIDs = new ConcurrentHashMap<String, Short>()
@Field static ConcurrentHashMap<String, Short> supervisionSentCommands = new ConcurrentHashMap<String, ConcurrentHashMap<Short, hubitat.zwave.Command>>()

hubitat.zwave.Command supervise(hubitat.zwave.Command command)
{
    if (implementsZwaveClass(0x6C))
	{
		// Get the next session ID, but if there is no stored session ID, initialize it with a random value.
		Short nextSessionID = supervisionSessionIDs.get(device.getDeviceNetworkId() as String,((Math.random() * 32) % 32) as Short )
		nextSessionID = (nextSessionID + 1) % 32 // increment and then mod with 32
		supervisionSessionIDs.replace(device.getDeviceNetworkId(), nextSessionID)
		
		// Store the command that is being sent so that you can log.debug it out in case of failure!
		supervisionSentCommands.get(device.getDeviceNetworkId() as String, new ConcurrentHashMap<Short, hubitat.zwave.Command>()).put(nextSessionID, command)

		if (logEnable) log.debug "Supervising a command: ${command} with session ID: ${nextSessionID}."
		return zwave.supervisionV1.supervisionGet(sessionID: nextSessionID, statusUpdates: true).encapsulate(command)
	} else {
		if (logEnable) log.debug "Not Supervising the command ${command}"
		return command
	}
}

// This handles a supervised message (a "get") received from the Z-Wave device //
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, Short ep = null ) {
	if (ep) log.warn "Received an endpoint in a SupervisionGet command ${cmd}. Probably works fine, but confirm handling!"
    if (logEnable) log.debug "Received a SupervisionGet message from ${device.displayName}. The SupervisionGet message is: ${cmd}"

    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(parseMap, defaultParseMap)
	
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
    sendToDevice(secure((new hubitat.zwave.commands.supervisionv1.SupervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0)), ep))
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionReport cmd) {

	hubitat.zwave.Command whatWasSent = supervisionSentCommands?.get(device.getDeviceNetworkId() as String)?.get(cmd.sessionID)

	if ((cmd.status as Integer) == (0x02 as Integer)) {
		log.warn "A Supervised command sent to device ${device.displayName} failed. The command that failed was: ${whatWasSent}."
	} else if (logEnable){
		log.debug "Results of supervised message is a report: ${cmd}, which was received in response to original command: " + whatWasSent
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

//// Catch Event Not Otherwise Handled! /////

void zwaveEvent(hubitat.zwave.Command cmd, ep = null) {
    log.debug "For ${device.displayName}, Received Z-Wave Message ${cmd} that is not handled by this driver. Endpoint: ${ep}. Message class: ${cmd.class}."
}

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
		return zwaveSecureEncap(zwave.multiChannelV4.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(cmd))
	} else {
		return zwaveSecureEncap(cmd) 
	}
}

String secure(hubitat.zwave.Command cmd, ep = null ){ 
	if (ep) {
		return zwaveSecureEncap(zwave.multiChannelV4.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(cmd))
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
