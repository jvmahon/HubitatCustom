import java.util.concurrent.* // Available (white-listed) concurrency classes: ConcurrentHashMap, ConcurrentLinkedQueue, Semaphore, SynchronousQueue
import groovy.transform.Field



metadata {
	definition (name: "[Cache Version 6] SynchronousQueue Cache Reports",namespace: "jvm", author: "jvm") {
		capability "Configuration"
		capability "Initialize"
		capability "Refresh"
		capability "Switch"
		capability "SwitchLevel"
		capability "ChangeLevel"
		
		command "test"
		command "preCacheReports"
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

void installed() { initialize() }

void configure() { initialize() }

void initialize( )
{
	preCacheReports()
}

void refresh()
{
}























//////////////////////////////////////////////////////////////////////
//////        Report Pre-Caching Library Functions            ///////
////////////////////////////////////////////////////////////////////// 

hubitat.zwave.Command  getCachedVersionReport() 										
								{ getReportCachedByNetworkId(zwave.versionV1.versionGet(), null )}
								
hubitat.zwave.Command  getCachedNotificationSupportedReport(Short ep = null )					
								{ 
									if (implementsZwaveClass(0x71, ep) < 2) return null
									getReportCachedByProductId(zwave.notificationV8.notificationSupportedGet(), ep)
								}
								
hubitat.zwave.Command  getCachedMultiChannelEndPointReport()							
								{ getReportCachedByProductId(zwave.multiChannelV4.multiChannelEndPointGet(), null )}
								
hubitat.zwave.Command  getCachedMultiChannelCapabilityReport(Short ep) 					
								{ getReportCachedByProductId(zwave.multiChannelV4.multiChannelCapabilityGet(endPoint: ep), null )}
								
hubitat.zwave.Command  getCachedCentralSceneSupportedReport()							
								{ getReportCachedByProductId(zwave.centralSceneV3.centralSceneSupportedGet(), null ) }
								
hubitat.zwave.Command  getCachedMeterSupportedReport(Short ep = null )						
								{ 
									if (implementsZwaveClass(0x32, ep) < 2) return null
									getReportCachedByProductId(zwave.meterV5.meterSupportedGet(), ep) 
								}
														
hubitat.zwave.Command  getCachedProtectionSupportedReport(Short ep = null )					
								{ 
									if (implementsZwaveClass(0x75, ep) < 2) return null
									getReportCachedByProductId(zwave.protectionV2.protectionSupportedGet(), ep) 
								}
								
hubitat.zwave.Command  getCachedSwitchMultilevelSupportedReport(Short ep = null )				
								{ 
									if (implementsZwaveClass(0x26, ep) < 3) return null
									getReportCachedByProductId(zwave.switchMultilevelV3.switchMultilevelSupportedGet(), ep) 
								}

hubitat.zwave.Command  getCachedSensorMultilevelSupportedSensorReport(Short ep = null )		
								{ 
									if (implementsZwaveClass(0x31, ep) < 5) return null
									getReportCachedByProductId(zwave.sensorMultilevelV11.sensorMultilevelSupportedGetSensor(), ep) 
								}

//  ==============================================


// =============================================
void preCacheReports()
{
		getCachedVersionReport() 										

		List<Short> 	deviceClasses = getDataValue("inClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Short }
						deviceClasses += getDataValue("secureInClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Short }
		
		deviceClasses.each{ it -> 
							// log.debug "implements class ${it}: "
							implementsZwaveClass(it)
						}
		getCachedCentralSceneSupportedReport()		
		getCachedNotificationSupportedReport()										
		getCachedMeterSupportedReport()																		
		getCachedProtectionSupportedReport()										
		getCachedSwitchMultilevelSupportedReport()				
		getCachedSensorMultilevelSupportedSensorReport()	
		
		if (implementsZwaveClass(0x60))
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


@Field static ConcurrentHashMap<String, ConcurrentHashMap> reportsCachedByProductId = new ConcurrentHashMap<String, ConcurrentHashMap>(32)
@Field static ConcurrentHashMap<String, ConcurrentHashMap> reportsCachedByNetworkId = new ConcurrentHashMap<String, ConcurrentHashMap>(64)
@Field static reportQueues = new ConcurrentHashMap<String, ConcurrentHashMap>()

SynchronousQueue myReportQueue(String reportClass)
{
	ConcurrentHashMap thisDeviceQueues = reportQueues.get(device.deviceNetworkId, new ConcurrentHashMap<String,SynchronousQueue>())
	SynchronousQueue thisReportQueue = thisDeviceQueues.get(reportClass, new SynchronousQueue())
	return thisReportQueue
}

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
		return cmd
		// runInMillis(10, replayCommand, [overwrite:false, data: [cachedVersionReport, ep ] ] )
	} else {
		Map transferredData;
		while( transferredData.is (null ) )
		{
			sendToDevice(secure(getCmd, ep))
			transferredData = myReportQueue(reportClass).poll(10, TimeUnit.SECONDS)
		}
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
		case "6009": // This get request carries the endPoint within the message, so use that to key the storage array
			subIndex = getCmd.endPoint
			break
		default :
			subIndex = ep ?: 0
			break
	}
	ConcurrentHashMap cacheForThisProductId = reportsCachedByProductId.get(firmwareKey(), new ConcurrentHashMap<String,SynchronousQueue>())
	ConcurrentHashMap cacheForThisSubIndex = cacheForThisProductId?.get(subIndex, new ConcurrentHashMap<String,Object>() )	

	String reportClass = commandHexStringToReportHexString(getCmd.CMD)
	hubitat.zwave.Command cmd = cacheForThisSubIndex?.get(reportClass)
	
	if (cmd) { 
		return cmd
		if (logEnable) log.debug "Device ${device.displayName}: In function getReportCachedByProductId, getting report using command ${getCmd} for endpoint ${ep}."
	} else {
		if (logEnable) log.debug "Device ${device.displayName}: sending to device a command : ${getCmd} to get report ${reportClass} for subIndex ${subIndex}."
		sendToDevice(secure(getCmd, ep))
		Map transferredData = myReportQueue(reportClass).poll(10, TimeUnit.SECONDS)
		if (logEnable) log.debug "Device ${device.displayName}: Transferred data for report ${getCmd} is: " + transferredData
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
	else log.warn "Device ${device.displayName} Failed to transfer report ${cmd}"
	return transferredReport
}

void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCapabilityReport  cmd)
{ 
	// This requires special processing. Endpoint is in the message, not passed as a parameter, but want to store it based on endpoint!
	Boolean transferredReport = myReportQueue(cmd.CMD).offer([report:cmd, endPoint:cmd.endPoint])
	if (transferredReport) log.debug "Successfully transferred version report to waiting receiver."
	else log.debug "Failed to transfer version report."
}

List <Short> getNotificationSupportedReportsAsList(Short ep = null ) 	
{ 
	hubitat.zwave.Command  report = getCachedNotificationSupportedReport(ep)
	
	List<Short> notificationTypes =[]
		if (report.smoke)				notificationTypes << 1 // Smoke
		if (report.co)					notificationTypes << 2 // CO
		if (report.co2)					notificationTypes << 3 // CO2
		if (report.heat)				notificationTypes << 4 // Heat
		if (report.water)				notificationTypes << 5 // Water
		if (report.accessControl) 		notificationTypes << 6 // Access Control
		if (report.burglar)				notificationTypes << 7 // Burglar
		if (report.powerManagement)		notificationTypes << 8 // Power Management
		if (report.system)				notificationTypes << 9 // System
		if (report.emergency)			notificationTypes << 10 // Emergency Alarm
		if (report.clock)				notificationTypes << 11 // Clock
		if (report.appliance)			notificationTypes << 12 // Appliance
		if (report.homeHealth)			notificationTypes << 13 // Home Health
		if (report.siren)				notificationTypes << 14 // Siren
		if (report.waterValve)			notificationTypes << 15 // Water Valve
		if (report.weatherAlarm)		notificationTypes << 16 // Weather Alarm
		if (report.irrigation)			notificationTypes << 17 // Irrigation
		if (report.gasAlarm)			notificationTypes << 18 // Gas Alarm
		if (report.pestControl)			notificationTypes << 19 // Pest Control
		if (report.lightSensor)			notificationTypes << 20 // Light Sensor
		if (report.waterQuality)		notificationTypes << 21 // Water Quality
		if (report.homeMonitoring)		notificationTypes << 22 // Home Monitoring
	return notificationTypes
}

////////////////////////////////////////////////////////////////////////////////////////////////////
Integer implementsZwaveClass(commandClass, ep = null ) {implementsZwaveClass(commandClass as Short, ep as Short )}
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

def supervise(hubitat.zwave.Command command)
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
		return zwave.supervisionV1.supervisionGet(sessionID: nextSessionID, statusUpdates: true ).encapsulate(command)
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

//////////////////////////////////////////////////////////////////////
//////        Hubitat Event Handling Helper Functions        ///////
//////////////////////////////////////////////////////////////////////

////    Hubitat Event Message Sending on Event Stream (not Z-Wave!)  ////
void sendEventToAll(Map event)
{
	if (logEnable) log.debug "Device ${device.displayName}: processing event: " + event
	if (logEnable) log.debug "Device ${device.displayName}: Device has attribute: ${event.name}: " + device.hasAttribute(event.name as String)
	if (device.hasAttribute(event.name as String)) sendEvent(event)

	getChildDevices()?.each{ child ->
			if (logEnable) log.debug "Device ${device.displayName}: For child device ${child.displayName}, processing event: " + event
			if (logEnable) log.debug "Device ${device.displayName}: Child device has attribute: ${event.name}: " + child.hasAttribute(event.name as String)
			if (child.hasAttribute(event.name as String)) child.sendEvent(event)
		}
}


//////////////////////////////////////////////////////////////////////
//////        Child Device Methods        ///////
////////////////////////////////////////////////////////////////////// 

Short getEndpoint(com.hubitat.app.DeviceWrapper device)
{
	return device.deviceNetworkId.split("-ep")[-1] as Short
}

void componentRefresh(cd){
    if (logEnable) log.info "received refresh request from component ${cd.displayName}"
	refresh(cd)
}

void componentOn(cd){
    log.debug "received componentOn request from ${cd.displayName}"
    // getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])
	on(cd)
}

void componentOff(cd){
    log.debug "received componentOff request from ${cd.displayName}"
    // getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])
	off(cd)
}

void componentSetLevel(cd,level,transitionTime = null) {
    if (logEnable) log.info "received setLevel(${level}, ${transitionTime}) request from ${cd.displayName}"
    // getChildDevice(cd.deviceNetworkId).parse([[name:"level", value:level, descriptionText:"${cd.displayName} level was set to ${level}%", unit: "%"]])
	setLevel(level, transitionTime, cd)
}

void componentStartLevelChange(cd, direction) {
    if (logEnable) log.info "received startLevelChange(${direction}) request from ${cd.displayName}"
	startLevelChange(direction, cd)
}

void componentStopLevelChange(cd) {
    if (logEnable) log.info "received stopLevelChange request from ${cd.displayName}"
	stopLevelChange(cd)
}

void componentSetSpeed(cd, speed) {
    if (logEnable) log.info "received setSpeed(${speed}) request from ${cd.displayName}"
	log.warn "componentSetSpeed not yet implemented in driver!"
    // getChildDevice(cd.deviceNetworkId).parse([[name:"level", value:level, descriptionText:"${cd.displayName} level was set to ${level}%", unit: "%"]])
	setSpeed(speed, cd)
}

void setSpeed(speed, cd = null )
{
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	log.warn "setSpeed function not implemented yet!"
	log.debug "Device ${targetDevice.displayName}: called setSpeed. Child device: ${ (cd) ? true : false }"
}

void setPosition(position, cd = null )
{
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	log.warn "setPosition function not implemented yet!"
	log.debug "Device ${targetDevice.displayName}: called setPosition. Child device: ${ (cd) ? true : false }"

}

void close() {
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	log.warn "Device ${targetDevice.displayName}: called close(). Function not implemented."
}

void open() {
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	log.warn "Device ${targetDevice.displayName}: called close(). Function not implemented."
}

//////////////////////////////////////////////////////////////////////
//////        Handle Battery Reports and Device Functions        ///////
////////////////////////////////////////////////////////////////////// 
void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) 
{
	if (cmd.batteryLevel == 0xFF) {
		sendEvent ( name: "battery", value:1, unit: "%", descriptionText: "Device ${device.displayName}, Low Battery Alert. Change now!")
	} else {
		sendEvent ( name: "battery", value:cmd.batteryLevel, unit: "%", descriptionText: "Device ${device.displayName} battery level is ${cmd.batteryLevel}.")
	}
}

void batteryGet() {
	sendToDevice(secure(zwave.batteryV1.batteryGet()))
}
//////////////////////////////////////////////////////////////////////
//////        Handle Basic Reports and Device Functions        ///////
////////////////////////////////////////////////////////////////////// 
def getTargetDeviceByEndPoint(Short ep = null )
{
	if (ep) { return getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Short) == (ep as Short)}
	} else { return device }
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv2.SwitchBinaryReport cmd, ep = null)
{
	def targetDevice = getTargetDeviceByEndPoint(ep)

	if (! targetDevice.hasAttribute("switch")) log.warn "For device ${targetDevice.displayName}, received a Switch Binary Report for a device that does not have a switch!"
	
	String priorSwitchState = targetDevice.currentValue("switch")
	String newSwitchState = ((cmd.value > 0) ? "on" : "off")
	
    if (priorSwitchState != newSwitchState) // Only send the state report if there is a change in switch state!
	{
		targetDevice.sendEvent(	name: "switch", value: newSwitchState, 
						descriptionText: "Device ${targetDevice.displayName} set to ${newSwitchState}.", 
						type: "physical")
		if (txtEnable) log.info "Device ${targetDevice.displayName} set to ${newSwitchState}."
	}
}



void zwaveEvent(hubitat.zwave.commands.switchmultilevelv4.SwitchMultilevelReport cmd, Short ep = null)
{
	def targetDevice = getTargetDeviceByEndPoint(ep)
	
	if (targetDevice.hasAttribute("position")) 
	{ 
		targetDevice.sendEvent( name: "position", value: (cmd.value == 99 ? 100 : cmd.value) , unit: "%", 
				descriptionText: "Device ${targetDevice.displayName} position set to ${cmd.value}%", type: "physical" )
		return 
	}
	
	Boolean hasSwitch = targetDevice.hasAttribute("switch")
	Boolean isDimmer = targetDevice.hasAttribute("level")

	Boolean turnedOn = false
	Short newLevel = 0

	if ((! (cmd.duration.is( null ) || cmd.targetValue.is( null ) )) && ((cmd.duration as Short) > (0 as Short))) //  Consider duration and target, but only when both are present and in transition with duration > 0 
	{
		turnedOn = (cmd.targetValue as Short) != (0 as Short)
		newLevel = (cmd.targetValue as Short)
	} else {
		turnedOn = (cmd.value as Short) > (0 as Short)
		newLevel = cmd.value as Short
	}
	
	String priorSwitchState = targetDevice.currentValue("switch")
	String newSwitchState = (turnedOn ? "on" : "off")
	Short priorLevel = targetDevice.currentValue("level")
	Short targetLevel

	if (newLevel == 99)
	{
		if ( priorLevel == 100) targetLevel = 100
		if ( priorLevel == 99) targetLevel = 99
	} else targetLevel = newLevel
	
    if (isDimmer && (priorSwitchState != newSwitchState))
	{
		targetDevice.sendEvent(	name: "switch", value: newSwitchState, 
						descriptionText: "Device ${targetDevice.displayName} set to ${newSwitchState}.", 
						type: "physical" )
		if (txtEnable) log.info "Device ${targetDevice.displayName} set to ${newSwitchState}."
	}
	if (hasDimmer && turnedOn) // If it was turned off, that would be handle in the "isDimmer" block above.
	{
		// Don't send the event if the level doesn't change except if transitioning from off to on, always send!
		if ((priorLevel != targetLevel) || (priorSwitchState != newSwitchState))
		{
			targetDevice.sendEvent( 	name: "level", value: targetLevel, 
					descriptionText: "Device ${targetDevice.displayName} level set to ${targetLevel}%", 
					type: "physical" )
			if (txtEnable) log.info "Device ${targetDevice.displayName} level set to ${targetLevel}%"		
		}
	}

	if (!isDimmer && !hasDimmer) log.warn "For device ${targetDevice.displayName} receive a report which wasn't processed. Need to check report handling code." + cmd
}


void on(cd = null ) {
	def targetDevice = (cd ? cd : device)
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
    
	if (implementsZwaveClass(0x26, ep)) // Multilevel  type device
	{ 
		Integer level = (targetDevice.currentValue("level") as Integer) ?: 100
        level = ((level < 1) || (level > 100)) ? 100 : level // If level got set to less than 1 somehow,then turn it on to 100%
		if (txtEnable) log.info "Device ${targetDevice.displayName}: Turned On at Level: ${level}."

		sendToDevice(secure(supervise(zwave.switchMultilevelV4.switchMultilevelSet(value: ((level > 99) ? 99 : level))), ep)	)	
		//sendToDevice(secure(supervise(zwave.basicV2.basicSet(value: ((level > 99) ? 99 : level))), ep)	)
		targetDevice.sendEvent(name: "switch", value: "on", descriptionText: "Device ${targetDevice.displayName} turned on", type: "digital")
		targetDevice.sendEvent(name: "level", value: level, descriptionText: "Device ${targetDevice.displayName} set to level ${level}%", type: "digital")
	} 
	else if (implementsZwaveClass(0x25, ep)) // Switch Binary Type device
	{
		if (txtEnable) log.info "Device ${targetDevice.displayName}: Turning to: On."
		sendToDevice(secure(supervise(zwave.switchBinaryV1.switchBinarySet(switchValue: 255 )), ep))
		targetDevice.sendEvent(name: "switch", value: "on", descriptionText: "Device ${targetDevice.displayName} turned on", type: "digital")
	}
	else if (implementsZwaveClass(0x20, ep)) // Basic Set Type device
	{
		log.warn "Using Basic Set to turn on device ${targetDevice.displayName}. A more specific command class should be used!"
		if (txtEnable) log.info "Device ${targetDevice.displayName}: Turning to: On."
		sendToDevice(secure(zwave.basicV2.basicSet(value: 55 ), ep))
		targetDevice.sendEvent(name: "switch", value: "on", descriptionText: "Device ${targetDevice.displayName} turned on", type: "digital")
	} else  {
		log.debug "Error in function on() - device ${targetDevice.displayName} does not implement a supported class"
	}
}


void off(cd = null ) {
	def targetDevice = (cd ? cd : device)
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	
	if (txtEnable) log.info "Device ${targetDevice.displayName}: Turning device to: Off."

	if (implementsZwaveClass(0x26, ep)) { // Multilevel  type device
		sendToDevice(secure(zwave.switchMultilevelV4.switchMultilevelSet(value: 0), ep)	)	
	} else if (implementsZwaveClass(0x25, ep)) { // Switch Binary Type device
		sendToDevice(secure(supervise(zwave.switchBinaryV1.switchBinarySet(switchValue: 0 )), ep))
	} else if (implementsZwaveClass(0x20, ep)) { // Basic Set Type device
		log.warn "Using Basic Set to turn on device ${targetDevice.displayName}. A more specific command class should be used!"
		sendToDevice(secure(supervise(zwave.basicV2.basicSet(value: 0 )), ep))
	} else {
		log.debug "Error in function off() - device ${targetDevice.displayName} does not implement a supported class"
		return
	}
	targetDevice.sendEvent(name: "switch", value: "off", descriptionText: "Device ${targetDevice.displayName} turned off", type: "digital")		
	
}

void setLevel(level, duration = 0, cd = null )
{
	def targetDevice = (cd ?: device)
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	
	if (logEnable) log.debug "Device ${targetDevice.displayName}: Executing function setlevel(level = ${level}, duration = ${duration})."
	if ( level > 100 ) level = 100
	if ( duration < 0 ) duration = 0
	if ( duration > 120 ) 
		{
			log.warn "Device ${targetDevice.displayName}: tried to set a dimming duration value greater than 120 seconds. To avoid excessive turn on / off delays, this driver only allows dimming duration values of up to 120."
			duration = 120
		}

	if (level <= 0)
	{
		// Turn off the switch, but don't change level -- it gets used when turning back on!
		Boolean stateChange = ((targetDevice.currentValue("switch") == "on") ? true : false)
	
		if (implementsZwaveClass(0x26, ep)) { // Multilevel type device
			sendToDevice(secure(zwave.switchMultilevelV4.switchMultilevelSet(value: 0, dimmingDuration: duration), ep ))	
		} else if (implementsZwaveClass(0x25, ep)) { // Switch Binary Type device
			sendToDevice(secure(supervise(zwave.switchBinaryV1.switchBinarySet(switchValue: 0)), ep))
		} else if (implementsZwaveClass(0x20, ep)) { // Basic Set Type device
			log.warn "Using Basic Set to turn on device ${targetDevice.displayName}. A more specific command class should be used!"
			sendToDevice(secure(supervise(zwave.basicV2.basicSet(value: 0) ), ep))
		} else {
			log.debug "Error in function setLevel() - device ${targetDevice.displayName} does not implement a supported class"
			return
		}
		if (logEnable) log.debug "For device ${targetDevice.displayName}, current switch value is ${targetDevice.currentValue("switch")}"
		if (targetDevice.currentValue("switch") == "on") 
		{	
			if (logEnable) log.debug "Turning switch from on to off in setlevel function"
			targetDevice.sendEvent(name: "switch", value: "off", descriptionText: "Device ${targetDevice.displayName} turned off", type: "digital")
		}		
		return
	} else 
	{
	
		if (implementsZwaveClass(0x26, ep)) { // Multilevel type device
			sendToDevice(secure(zwave.switchMultilevelV4.switchMultilevelSet(value: ((level > 99) ? 99 : level), dimmingDuration: duration), ep ))
		} else if (implementsZwaveClass(0x25, ep)) { // Switch Binary Type device
			sendToDevice(secure(supervise(zwave.switchBinaryV1.switchBinarySet(switchValue: (level > 99) ? 99 : level)), ep ))
		} else if (implementsZwaveClass(0x20, ep)) { // Basic Set Type device
			log.warn "Using Basic Set to turn on device ${targetDevice.displayName}. A more specific command class should be used!"
			sendToDevice(secure(supervise(zwave.basicV2.basicSet(value: (level > 99) ? 99 : level) ), ep))
		} else {
			log.debug "Error in function setLevel() - device ${targetDevice.displayName} does not implement a supported class"
			return
		}
		if (logEnable) log.debug "Current switch value: ${targetDevice.currentValue("switch")}, is it off: ${targetDevice.currentValue("switch") == "off"}"
		if (targetDevice.currentValue("switch") == "off") 
		{	
			if (logEnable) log.debug "Turning switch from off to on in setlevel function"
			targetDevice.sendEvent(name: "switch", value: "on", descriptionText: "Device ${targetDevice.displayName} turned on", type: "digital")
		}
		targetDevice.sendEvent(name: "level", value: level, descriptionText: "Device ${targetDevice.displayName} set to ${level}%", type: "digital")
	}
}

void startLevelChange(direction, cd = null ){
	def targetDevice = (cd ? cd : device)
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	
    Integer upDown = (direction == "down" ? 1 : 0)
    sendToDevice(secure(supervise(zwave.switchMultilevelV1.switchMultilevelStartLevelChange(upDown: upDown, ignoreStartLevel: 1, startLevel: 0)), ep))
}

void stopLevelChange(cd = null ){
	def targetDevice = (cd ? cd : device)
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	
	List<hubitat.zwave.Command> cmds = []
		cmds.add(secure(supervise(zwave.switchMultilevelV1.switchMultilevelStopLevelChange()), ep))
		cmds.add(secure(zwave.basicV1.basicGet(), ep))
	sendToDevice(cmds)
}

void test(){
	List<hubitat.zwave.Command> cmds = []
		cmds.add(secure(supervise(zwave.switchMultilevelV1.switchMultilevelStopLevelChange()), ep))
		cmds.add(secure(zwave.basicV1.basicGet(), ep))
	sendToDevice(cmds)
}
