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
		
		command "deleteChildDevices"
		command "createChildDevices"

        attribute "buttoonTripleTapped", "number"	
		attribute "buttonFourTaps", "number"	
		attribute "buttonFiveTaps", "number"	         
		attribute "multiTapButton", "number"		
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
		input name: "superviseEnable", type: "bool", title: "Enable Command Supervision if supported", defaultValue: true
		
		// The following preferences are only for use while debugging. Remove them from final code
		input name: "remindEnable", type: "bool", title: "Enable Code To-Do Reminders", defaultValue: true
    }
}

void deleteChildDevices()
{
	getChildDevices()?.each
	{ child ->
		deleteChildDevice(child.deviceNetworkId)
	}
}

void createChildDevices()
{	
	Integer mfr = 	device.getDataValue("manufacturer").toInteger()
	Integer type = 	device.getDataValue("deviceType").toInteger()
	Integer id = 	device.getDataValue("deviceId").toInteger()
	
	Short numberOfEndPoints = getCachedMultiChannelEndPointReport().endPoints
	log.debug "numberOfEndPoints is ${numberOfEndPoints}."


	getChildDevices()?.each
	{ child ->	
	
		List childNetIdComponents = child.deviceNetworkId.split("-ep")
                    
		if (childNetIdComponents.size() != 2) {
			log.debug "child componenent ${child.displayName} to be deleted!"	
			
		} else {
            Boolean endPointInRange = ((0 as Short) < (childNetIdComponents[1] as Short)) && ((childNetIdComponents[1] as Short) < numberOfEndPoints)
			Boolean parentNetIdMatches = (childNetIdComponents[0]  == device.deviceNetworkId)
			
            if (parentNetIdMatches && endPointInRange ) {
				log.debug "child componenent ${child.displayName} is a valid child device!"
		    } else {
				log.debug "child componenent ${child.displayName} NOT a valid child device for this driver!"
			}
		}
	}
	/*
	def endpointInfo = endPointMap?.find{ (it.manufacturer == mfr) && (it.deviceType == type) && (it.deviceId 	== id)}
	if (logEnable) log.debug "Endpoint Info is: ${endpointInfo}"	
	
	endpointInfo.ep.each{k, v ->
		def childNetworkID = "${device.deviceNetworkId}-ep${"${k}".padLeft(3, "0") }"
		def cd = getChildDevice(childNetworkID)
		if (!cd) {
			log.info "creating child device: ${childNetworkID}"
			addChildDevice("hubitat", v.driver, childNetworkID, [name: "${device.displayName}-${v.name}", isComponent: false])
		} else {
			log.info "Child device: ${childNetworkID} already exist. No need to re-create."
		}
	}	
	*/
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

hubitat.zwave.Command  getCachedVersionReport() { 
								getReportCachedByNetworkId(zwave.versionV1.versionGet(), null )
							}
								
hubitat.zwave.Command  getCachedNotificationSupportedReport(Short ep = null ) { 
								if (implementsZwaveClass(0x71, ep) < 2) return null
								getReportCachedByProductId(zwave.notificationV8.notificationSupportedGet(), ep)
							}
								
hubitat.zwave.Command  getCachedMultiChannelEndPointReport() { 
								getReportCachedByProductId(zwave.multiChannelV4.multiChannelEndPointGet(), null )
							}
								
hubitat.zwave.Command  getCachedMultiChannelCapabilityReport(Short ep)  { 
								getReportCachedByProductId(zwave.multiChannelV4.multiChannelCapabilityGet(endPoint: ep), null )
							}
								
hubitat.zwave.Command  getCachedCentralSceneSupportedReport() { 
								getReportCachedByProductId(zwave.centralSceneV3.centralSceneSupportedGet(), null ) 
							}
								
hubitat.zwave.Command  getCachedMeterSupportedReport(Short ep = null ) { 
								if (implementsZwaveClass(0x32, ep) < 2) return null
								getReportCachedByProductId(zwave.meterV5.meterSupportedGet(), ep) 
							}
														
hubitat.zwave.Command  getCachedProtectionSupportedReport(Short ep = null ) { 
								if (implementsZwaveClass(0x75, ep) < 2) return null
								getReportCachedByProductId(zwave.protectionV2.protectionSupportedGet(), ep) 
							}
								
hubitat.zwave.Command  getCachedSwitchMultilevelSupportedReport(Short ep = null ) { 
								if (implementsZwaveClass(0x26, ep) < 3) return null
								getReportCachedByProductId(zwave.switchMultilevelV3.switchMultilevelSupportedGet(), ep) 
							}

hubitat.zwave.Command  getCachedSensorMultilevelSupportedSensorReport(Short ep = null ) { 
								if (implementsZwaveClass(0x31, ep) < 5) return null
								getReportCachedByProductId(zwave.sensorMultilevelV11.sensorMultilevelSupportedGetSensor(), ep) 
							}

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
Integer getManufacturer() { device.getDataValue("manufacturer").toInteger() }
Integer getDeviceType() { device.getDataValue("deviceType").toInteger() }
Integer getDeviceId() {device.getDataValue("deviceId").toInteger() }

String productKey() // Generates a key based on manufacturer / device / firmware. Data is shared among all similar end-devices.
{
	if (remindEnable) log.warn "productKey function should be updated with a hash based on inclusters as some devices may remove change their inclusters depending on pairing state. for example, Zooz Zen 18 motion sensor may or may not show with a battery!"
	
	String manufacturer = 	hubitat.helper.HexUtils.integerToHexString( getManufacturer(), 2)
	String deviceType = 	hubitat.helper.HexUtils.integerToHexString( getDeviceType(), 2)
	String deviceID = 		hubitat.helper.HexUtils.integerToHexString( getDeviceId(), 2) 

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
		if(cmd.is( null ) ) {log.warn "Device ${device.displayName}: failed to retrieve a requested command class ${requestedCommandClass}."; return null }
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

@Field static ConcurrentHashMap<String, ConcurrentHashMap> supervisionRejected = new ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>>()

Boolean commandSupervisionNotSupported(cmd) {	
	Boolean previouslyRejected = ( supervisionRejected.get(firmwareKey())?.get(cmd.CMD) ) ? true : false 
	log.debug "Is class ${cmd.CMD} unsupervisable? ${previouslyRejected}"
	return previouslyRejected 
}

void markSupervisionNotSupported(cmd) {	
	supervisionRejected.get(firmwareKey(), new ConcurrentHashMap<String, Boolean>() ).put(cmd.CMD, true )
}

def supervise(hubitat.zwave.Command command)
{
log.debug "SuperviseEnabled is: " + superviseEnable
log.debug "CommandSupervisionNotSupported is: " + commandSupervisionNotSupported(command)
log.debug "implementsZwaveClass is: " + implementsZwaveClass(0x6C)

    if (superviseEnable && (!commandSupervisionNotSupported(command)) && implementsZwaveClass(0x6C))
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

	switch (cmd.status)
	{
		case 0x00:
			log.warn "Device ${device.displayName}: A Supervised command sent to device ${device.displayName} is not supported. Command was: ${whatWasSent}. Re-sending without supervision."
			markSupervisionNotSupported(whatWasSent)
			sendToDevice(secure(whatWasSent))
			break
		case 0x01:
			if (txtEnable) log.info "Device ${device.displayName}: Still processing command: ${whatWasSent}."
		case 0x02:
			log.warn "Device ${device.displayName}: A Supervised command sent to device ${device.displayName} failed. The command that failed was: ${whatWasSent}."
			break
		case 0xFF:
			if (txtEnable) log.info "Device ${device.displayName}: Successfully processed command ${whatWasSent}."
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
	if (device.is( null )) return null 
	
	return device.deviceNetworkId.split("-ep")[-1] as Short
}

void componentRefresh(cd){
    if (logEnable) log.info "received refresh request from component ${cd.displayName}"
	refresh(cd:cd)
}

void componentOn(cd){
    log.debug "received componentOn request from ${cd.displayName}"
    // getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])
	on(cd:cd)
}

void componentOff(cd){
    log.debug "received componentOff request from ${cd.displayName}"
    // getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])
	off(cd:cd)
}

void componentSetLevel(cd,level,transitionTime = null) {
    if (logEnable) log.info "received setLevel(${level}, ${transitionTime}) request from ${cd.displayName}"
    // getChildDevice(cd.deviceNetworkId).parse([[name:"level", value:level, descriptionText:"${cd.displayName} level was set to ${level}%", unit: "%"]])
	setLevel(level:level, duration:transitionTime, cd:cd)
}

void componentStartLevelChange(cd, direction) {
    if (logEnable) log.info "received startLevelChange(${direction}) request from ${cd.displayName}"
	startLevelChange(direction:direction, cd:cd)
}

void componentStopLevelChange(cd) {
    if (logEnable) log.info "received stopLevelChange request from ${cd.displayName}"
	stopLevelChange(cd:cd)
}

void componentSetSpeed(cd, speed) {
    if (logEnable) log.info "received setSpeed(${speed}) request from ${cd.displayName}"
	log.warn "componentSetSpeed not yet implemented in driver!"
    // getChildDevice(cd.deviceNetworkId).parse([[name:"level", value:level, descriptionText:"${cd.displayName} level was set to ${level}%", unit: "%"]])
	setSpeed(speed:speed, cd:cd)
}

void setSpeed(Map params = [speed: null , cd: null ], speed)
{
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	log.warn "setSpeed function not implemented yet!"
	log.debug "Device ${targetDevice.displayName}: called setSpeed. Child device: ${ (cd) ? true : false }"
}

void setPosition(Map params = [position: null , cd: null ], position )
{
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	log.warn "setPosition function not implemented yet!"
	log.debug "Device ${targetDevice.displayName}: called setPosition. Child device: ${ (cd) ? true : false }"

}

void close( cd = null ) {
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	log.warn "Device ${targetDevice.displayName}: called close(). Function not implemented."
}

void open( cd = null ) {
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

void on(Map params = [cd: null , duration: null , level: null ])
{
	log.debug "In function on(Map ...), map value is: $params"
	def targetDevice = (params.cd ? params.cd : device)
	Short ep = params.cd ? (params.cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
    Map levelEvent = null 
	
	if (implementsZwaveClass(0x26, ep)) // Multilevel  type device
	{ 
		Short level = params.level ? params.level : (targetDevice.currentValue("level") as Short ?: 100)
        level = Math.min(Math.max(level, 1), 100) // Level betweeen 1 and 100%
	
		sendToDevice(secure(supervise(zwave.switchMultilevelV4.switchMultilevelSet(value: Math.min(level, 99), dimmingDuration:(params.duration as Short) )), ep) )

		levelEvent = [name: "level", value: level, descriptionText: "Device ${targetDevice.displayName} set to level ${level}%", type: "digital"]
	} 
	else if (implementsZwaveClass(0x25, ep)) { // Switch Binary Type device
		sendToDevice(secure(supervise(zwave.switchBinaryV1.switchBinarySet(switchValue: 255 )), ep))
	}
	else if (implementsZwaveClass(0x20, ep)) { // Basic Set Type device
		log.warn "Using Basic Set to turn on device ${targetDevice.displayName}. A more specific command class should be used!"
		sendToDevice(secure(zwave.basicV2.basicSet(value: 0xFF ), ep))
	} else  {
		log.debug "Error in function on() - device ${targetDevice.displayName} does not implement a supported class"
	}
	
	if (targetDevice.currentValue("switch") == "off") 
	{	
		if (logEnable) log.debug "Device ${targetDevice.displayName}: Turning switch from off to on."
		targetDevice.sendEvent(name: "switch", value: "on", descriptionText: "Device ${targetDevice.displayName} turned on", type: "digital")
		if (levelEvent) 
			{
				targetDevice.sendEvent(levelEvent)
				log.debug "Device ${targetDevice.displayName}: Sending level event: ${levelEvent}."
			}
	} else {
			if (logEnable) log.debug "Device ${targetDevice.displayName}: Switch already off. Inhibiting sending of off event."
	}
}


void off(Map params = [cd: null , duration: null ]) {

	log.debug "In function off(Map ...), map value is: $params"
	def targetDevice = (params.cd ? params.cd : device)
	Short ep = params.cd ? (params.cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	
	if (txtEnable) log.info "Device ${targetDevice.displayName}: Turning device to: Off."

	if (implementsZwaveClass(0x26, ep)) { // Multilevel  type device
		sendToDevice(secure(supervise(zwave.switchMultilevelV4.switchMultilevelSet(value: 0, dimmingDuration:params.duration as Short)), ep)	)	
	} else if (implementsZwaveClass(0x25, ep)) { // Switch Binary Type device
		sendToDevice(secure(supervise(zwave.switchBinaryV1.switchBinarySet(switchValue: 0 )), ep))
	} else if (implementsZwaveClass(0x20, ep)) { // Basic Set Type device
		log.warn "Device ${targetDevice.displayName}: Using Basic Set to turn on device. A more specific command class should be used!"
		sendToDevice(secure(supervise(zwave.basicV2.basicSet(value: 0 )), ep))
	} else {
		log.debug "Device ${targetDevice.displayName}: Error in function off(). Device does not implement a supported class"
		return
	}

	if (targetDevice.currentValue("switch") == "on") 
	{	
		if (logEnable) log.debug "Device ${targetDevice.displayName}: Turning switch from on to off."
		targetDevice.sendEvent(name: "switch", value: "off", descriptionText: "Device ${targetDevice.displayName} turned off", type: "digital")
	} else {
		if (logEnable) log.debug "Device ${targetDevice.displayName}: Switch already off. Inhibiting sending of off event."
	}
}

void setLevel(level, duration = null )
	{
		setLevel(level:level, duration:duration)
	}
	
void setLevel(Map params = [cd: null , level: null , duration: null ])
{
	log.debug "Called setLevel with a parameter map: ${params}"
	
	def targetDevice = (params.cd ? params.cd : device)
	Short ep = params.cd ? (params.cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	
	Short newDimmerLevel = Math.min(Math.max(params.level as Integer, 0), 99) as Short
	Short transitionTime = null 
	if (params.duration) transitionTime = params.duration as Short
	
	if (newDimmerLevel <= 0) {
		off(cd:cd, duration:transitionTime)
	} else {
		on(cd:cd,level:newDimmerLevel, duration:transitionTime)
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

///////////////////////////////////////////////////////////////////////////////////////////////
///////////////                  Central Scene Processing          ////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////

@Field static  ConcurrentHashMap centralSceneButtonState = new ConcurrentHashMap<String, String>()

String getCentralSceneButtonState(Integer button) { 
 	String key = "${device.deviceNetworkId}.Button.${button}"
	return centralSceneButtonState.get(key)
}

String setCentralSceneButtonState(Integer button, String state) {
 	String key = "${device.deviceNetworkId}.Button.${button}"
	centralSceneButtonState.put(key, state)
	return centralSceneButtonState.get(key)
}

void getCentralSceneInfo() {
	sendToDevice(secure( zwave.centralSceneV3.centralSceneSupportedGet() ))
}

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd)
{
	if ((getCentralSceneButtonState(cmd.sceneNumber as Integer) == "held") && (cmd.keyAttributes == 2)) return

    Map event = [value:cmd.sceneNumber, type:"physical", unit:"button#", isStateChange:true]
	
	event.name = [	0:"pushed", 1:"released", 2:"held",  3:"doubleTapped", 
					4:"buttoonTripleTapped", 5:"buttonFourTaps", 6:"buttonFiveTaps"].get(cmd.keyAttributes as Integer)
	
	String tapDescription = [	0:"Pushed", 1:"Released", 2:"Held",  3:"Double-Tapped", 
								4:"Three Taps", 5:"Four Taps", 6:"Five Taps"].get(cmd.keyAttributes as Integer)
    
	setCentralSceneButtonState(cmd.sceneNumber, event.name)	
	
	event.descriptionText="${device.displayName}: Button #${cmd.sceneNumber}: ${tapDescription}"
	
	log.debug "Central Scene Event is: ${event}."

	sendEvent(event)
	
	// Next code is for the custom attribute "multiTapButton".
	Integer taps = [0:1, 3:2, 4:3, 5:4, 6:5].get(cmd.keyAttributes as Integer)
	if ( taps )
	{
		event.name = "multiTapButton"
		event.unit = "Button #.Tap Count"
		event.value = ("${cmd.sceneNumber}.${taps}" as Float)
		log.debug "multitap event is: ${event}."
		sendEvent(event)		
	} 
}
