import java.util.concurrent.*;
import groovy.transform.Field


@Field static Map<Integer, Integer> defaultParseMap = [
	0x20:2, // Basic Set
	0x25:2, // Switch Binary
	0x26:4, // Switch MultiLevel 
	0x31:11, // Sensor MultiLevel
	0x32:5, // Meter
	0x5B:1,	// Central Scene
	0x60:2,	// MultiChannel // V4 not working, have to use V2 for now!
	0x62:1,	// Door Lock
	0x63:1,	// User Code
	0x6C:1,	// Supervision
	0x71:8, // Notification
	0x80:1, // Battery
	0x86:1,	// Version  // createCommand currently not working with versions other than 1!
	0x98:1,	// Security
	0x9B:2,	// Configuration
	0x87:3  // Indicator
	]

metadata {
	definition (name: "[Beta] Cache Reports",namespace: "jvm", author: "jvm") {
		capability "Configuration"
		capability "Initialize"
		command "getSupportedReports"
		command "logStoredReportCache"
        command "getSessionID"
        command "turnOn"
    }
    preferences 
	{
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: true
    }
}

void turnOn()
{

	sendToDevice(secure(supervise(zwave.basicV1.basicSet(value: 0xFF))))

}

//////////////////////////////////////////////////////////////////////
//////        Report Pre-Caching Library Functions            ///////
////////////////////////////////////////////////////////////////////// 
/*
This is a library of device / driver independent functions which pre-gathers Z-wave reports and stores them for later use. The reports are gathered at Hub startup and stored in a concurrent hash map. This allows the device reports to be retrieved from the stored cache as needed -- thus allowing the programmer to treat them as just another piece of accessible stored data, rather than having to query when needed and handle in an event handler. 

Functions supported:


Two categories of reports.
*/

@Field static ConcurrentHashMap<String, Map> reportCacheByFirmware = new ConcurrentHashMap<String, Map>(16)
@Field static ConcurrentHashMap<String, Map> reportCacheByDNI = new ConcurrentHashMap<String, Map>(64)
@Field static ConcurrentHashMap<String, Map> reportCacheByEndpoint = new ConcurrentHashMap<String, Map>(32)

@Field static Semaphore waitForReport = new Semaphore(1)

// //////////////////////////////////////////////////////////////////////
hubitat.zwave.Command  getCachedVersionReport(){ return (getCachedReportByNetworkID("8612"))}
// //////////////////////////////////////////////////////////////////////

///   Functions to generate keys used to access the concurrent Hash Maps ///

String productKey()
{
	String manufacturer = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("manufacturer").toInteger(), 2)
	String deviceType = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceType").toInteger(), 2)
	String deviceID = 		hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceId").toInteger(), 2) 
	String key = "${manufacturer}:${deviceType}:${deviceID}:"
	return key
}

String firmwareKey(String firmwareReport)
{
	hubitat.zwave.Command   version = getCachedVersionReport() 
	log.debug "Firmware version report hex string in function firmwareKey is ${version.format()} and report is ${version}."
	String key = productKey() + version.format() // Manufacturer information Plus The Report embodying firmware Info
	return key
}

///   Functions to Test if classes are implemented by the device ///

Boolean implementsZwaveClass( Integer zwaveCommandClass, ep = null )
{
	if (ep)
	{
		log.debug "implementsZwaveClass() code is incomplete. Endpoint checking not supported!"
	} else {
		List<Integer> 	deviceInclusters = getDataValue("inClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Integer }
					deviceInclusters += getDataValue("secureInClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Integer }
					if (!deviceInclusters.contains(32)) deviceInclusters += 32
		return deviceInclusters.contains(zwaveCommandClass)
	}
}

Integer classLevel(Integer checkClass)
{
log.debug "Called classLevel with class value ${checkClass}. This function is currently a stub that always returns 16."
	return 16
}

Boolean cachedReportExists(String command, ep  = null )
{
    Map deviceStorage = [:]
	
	deviceStorage = reportCacheByDNI.get(device.getDeviceNetworkId())
	if ( deviceStorage?.get(reportClass) ) return true
	
    deviceStorage = reportCacheByDNI.get(device.getDeviceNetworkId())
	if ( deviceStorage?.get(reportClass) ) return true
	
    if (ep) log.warn "cachedReportExists is not fully implemented! Does not support the endpoint function"
    return false
}

///   Functions to store and retrieve Z-Wave reports from the concurrent Hash Maps ///
///  The reports are stored as a hex string which is obtained from cmd.format()    ///

void storeReportByNetworkID(hubitat.zwave.Command cmd, ep = null )
{
	Map deviceStorage = reportCacheByDNI.get((device.getDeviceNetworkId()), [:])
	log.debug "deviceStorage for device ${device.displayName} is: " + deviceStorage
	if (deviceStorage.containsKey(cmd.CMD)){
		deviceStorage.replace(cmd.CMD, cmd.format())
	} else 	{
		deviceStorage.put(cmd.CMD, cmd.format())
	}
}

hubitat.zwave.Command  getCachedReportByNetworkID(String reportClass, ep = null )
{
	Map deviceStorage = reportCacheByDNI.get(device.getDeviceNetworkId() as String)
	if (deviceStorage.is( null ) || (deviceStorage.size() == 0)) 
		{
			return null
		}
	String commandString = deviceStorage.get(reportClass)
	log.debug "Recreating command using Commandstring for class ${reportClass} is ${commandString}."
	hubitat.zwave.Command  newCommand = createCommand(commandString)
	log.debug "New command is: ${newCommand}"
	return newCommand
}

void storeReportByFirmwareVersion(hubitat.zwave.Command cmd, ep = null )
{
	Map deviceStorage = reportCacheByFirmware.get(firmwareKey(), [:])
	log.debug "deviceStorage for device ${device.displayName} is: " + deviceStorage
	if (deviceStorage.containsKey(cmd.CMD)) {
		deviceStorage.replace(cmd.CMD, cmd.format())
	} else 	{
		deviceStorage.put(cmd.CMD, cmd.format())
	}
}

hubitat.zwave.Command  getCachedReportByFirmwareVersion(String reportClass, ep = null )
{
	Map deviceStorage = reportCacheByFirmware.get(firmwareKey())
	if (deviceStorage.is( null ) || (deviceStorage.size() == 0)) return null
	String commandString = deviceStorage.get(reportClass)
	log.debug "Commandstring for class ${reportClass} is ${commandString}."
	hubitat.zwave.Command  newCommand = createCommand(commandString)
	log.debug "New command is: ${newCommand}"
	return newCommand
}

void storeReportByEndpoint(cmd, ep)
{
	String endpointKey = firmwareKey() +":${ep}"
	Map deviceStorage = reportCacheByEndpoint.get(endpointKey(), [:])
}

void cacheDeviceReport(String command, ep = null )
{
    if (cachedReportExists(command, ep))  { log.debug "Already have cached report for ${command}."; return }
    
	waitForReport.tryAcquire(1, 10, TimeUnit.SECONDS )

	log.debug "Sending to device ${device.displayName} the command string: ${command}."
	sendToDevice(secure(command))
	waitForReport.tryAcquire(1, 10, TimeUnit.SECONDS )
	waitForReport.release(1)
}

void getSupportedReports(ep = null)
{
	// Many of the reports are stored based on the firmware version, so don't handle anything else until
	// sure that the firmware version was retrieved!
	
	// Other supported reports are "gotten" from the VersionReport handler!
	cacheDeviceReport("8611")
}

void getOtherReports(ep = null )
{
	// Call this from the firmware Report Handler
	
	// MultiChannelEndPointReport Get:0x6007  Report:0x6008
	if (implementsZwaveClass(0x60)) cacheDeviceReport("6007") // Request is only sent to the parent. Don't add ep = null!
		
	// AlarmTypeSupportedReport Get:  Report:0x7108
	// Deprecated
	if (implementsZwaveClass(0x71, ep)) cacheDeviceReport("7107")
		
	// CentralSceneSupportedReport Get:0x5B01  Report:0x5B02
	// Per standards, Central Scene should never have an endpoint!
	if (implementsZwaveClass(0x5B)) cacheDeviceReport("5B01")
		
	// CommandRecordsSupportedReport  Get:  Report:0x9B02
	//	if (implementsZwaveClass(0x9B, ep)) cacheDeviceReport()
		
	// DcpListSupportedReport  Get:  Report:0x3A02
	//	if (implementsZwaveClass(0x3A, ep)) cacheDeviceReport()
		
	// DoorLockLoggingRecordsSupportedReport  Get:  Report:0x4C02
	if (implementsZwaveClass(0x4C)) cacheDeviceReport("4C01")
		
	// EventSupportedReport  Get:0x7101  Report:0x7102
	if (implementsZwaveClass(0x71, ep)) cacheDeviceReport("7101", ep)
		
	// HrvControlModeSupportedReport  Get:  Report:0x390B
	//	if (implementsZwaveClass(0x39, ep)) cacheDeviceReport()
		
	// HrvStatusSupportedReport  Get:  Report:0x3704
	//	if (implementsZwaveClass(0x37, ep)) cacheDeviceReport()
		
	// IndicatorSupportedReport  Get:0x8704  Report:0x8705
	if (implementsZwaveClass(0x87, ep) && ( classLevel(0x87) > 1)) cacheDeviceReport("870400", ep)
		
	// MeterSupportedReport  Get:0x3203  Report:0x3204
	if (implementsZwaveClass(0x32, ep)) cacheDeviceReport("3203", ep)
		
	// MeterTblStatusSupportedReport  Get:  Report:0x3D08
	//	if (implementsZwaveClass(0x3D, ep)) cacheDeviceReport()
		
	// NotificationSupportedReport  Get:0x7107  Report:0x7108
	if (implementsZwaveClass(0x71, ep)) cacheDeviceReport("7107", ep)
		
	// PrepaymentSupportedReport  Get:  Report:0x3F04
	if (implementsZwaveClass(0x3F, ep)) cacheDeviceReport("3F03")
		
	// ProtectionSupportedReport  Get:0x7504  Report:0x7505
	if (implementsZwaveClass(0x75, ep) && ( classLevel(0x75) > 1)) cacheDeviceReport("7504", ep)
		
	// RateTblSupportedReport  Get:  Report:0x4902
	//	if (implementsZwaveClass(0x49, ep)) cacheDeviceReport()
		
	// ScheduleEntryTypeSupportedReport  Get:  Report:0x4E0A
	// Deprecated
	//	if (implementsZwaveClass(0x4E, ep)) cacheDeviceReport()
		
	// ScheduleSupportedReport  Get:  Report:0x5302
	//	if (implementsZwaveClass(0x53, ep)) cacheDeviceReport()
		
	// SecurityCommandsSupportedReport  Get:  Report:0x9803
	// Not necessary.Hubitat will provde this as secureInClusters
	// if (implementsZwaveClass(0x98, ep)) cacheDeviceReport()
		
	// SecurityPanelModeSupportedReport   Get:  Report:0x2402
	//	if (implementsZwaveClass(0x24, ep)) cacheDeviceReport ()
		
	// SecurityPanelZoneSupportedReport  Get:  Report:0x2E02
	//	if (implementsZwaveClass(0x2E, ep)) cacheDeviceReport()
		
	// SensorAlarmSupportedReport  Get:  Report:0x9C04
	// Deprecated
	//	if (implementsZwaveClass(0x9C, ep)) cacheDeviceReport()
	
	// SensorMultilevelSupportedSensorReport   Get:0x3101  Report:0x3101
	if (implementsZwaveClass(0x31, ep)) cacheDeviceReport("3101", ep)	
		
	// SimpleAvControlSupportedReport  Get:  Report:0x9405
	if (implementsZwaveClass(0x94, ep)) cacheDeviceReport("9404")
		
	// SwitchColorSupportedReport  Get:  Report:0x3302
	if (implementsZwaveClass(0x33, ep)) cacheDeviceReport("3301")
		
	// SwitchMultilevelSupportedReport  Get:0x2606  Report:0x2607
	// Only supported by V3 or later devices!
	if (implementsZwaveClass(0x26, ep) && (classLevel(0x26) > 2)) cacheDeviceReport("2606", ep)
		
	// ThermostatFanModeSupportedReport  Get:  Report:0x4405
	if (implementsZwaveClass(0x44)) cacheDeviceReport("4404")
		
	// ThermostatModeSupportedReport  Get:  Report:0x4005
	//	if (implementsZwaveClass(0x40)) cacheDeviceReport()
		
	// ThermostatOperatingLoggingSupportedReport  Get:  Report:0x4204
	//	if (implementsZwaveClass(0x42)) cacheDeviceReport()
		
	// ThermostatSetpointSupportedReport  Get:  Report:0x4305
	//	if (implementsZwaveClass(0x43)) cacheDeviceReport()
}

void logStoredReportCache()
{
	log.debug "report cache for items stored based on firmware version is: " + reportCacheByFirmware
	log.debug "report cache for items stored based on DNI is: " + reportCacheByDNI
	log.debug "report cache for items stored based on Endpoint is: " + reportCacheByEndpoint
}

/////////////////  Caching Functions To Store Reports! //////////////////////////////
void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) { cacheReportByDeviceNetworkID (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) { cacheReportByDeviceNetworkID (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) { cacheReportByDeviceNetworkID (cmd, ep) }
void  cacheReportByDeviceNetworkID (cmd, ep = null )
{
	// The information cached here is common to all devices with the same manufacturere / device type / device ID  code and firmware version.
	if (ep) log.debug "Endpoint Functionality Not Implemented for report caching"
	log.debug "Received report for caching by Network ID with command string ${cmd.CMD} and format ${cmd.format()}"
	storeReportByNetworkID(cmd, ep)
	waitForReport.release(1)
	getOtherReports()

}

void zwaveEvent(hubitat.zwave.commands.multichannelv2.MultiChannelEndPointReport  cmd)  			{ cacheReportByFirmwareVersion (cmd) }
void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelEndPointReport  cmd)  			{ cacheReportByFirmwareVersion (cmd) }
void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelEndPointReport  cmd)  			{ cacheReportByFirmwareVersion (cmd) }
void zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneSupportedReport  cmd)  			{ cacheReportByFirmwareVersion (cmd) }
void zwaveEvent(hubitat.zwave.commands.centralscenev2.CentralSceneSupportedReport  cmd)  			{ cacheReportByFirmwareVersion (cmd) }
void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneSupportedReport  cmd)  			{ cacheReportByFirmwareVersion (cmd) }
void zwaveEvent(hubitat.zwave.commands.meterv2.MeterSupportedReport cmd, ep = null ) 				{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv3.MeterSupportedReport cmd, ep = null ) 				{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv4.MeterSupportedReport cmd, ep = null ) 				{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv5.MeterSupportedReport cmd, ep = null ) 				{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationSupportedReport cmd, ep = null ) 	{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.notificationv8.EventSupportedReport cmd, ep = null )   		{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.indicatorv3.IndicatorSupportedReport  cmd, ep = null )   	{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.protectionv2.ProtectionSupportedReport  cmd, ep = null )   	{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelSupportedReport  cmd, ep = null )   	{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelSupportedSensorReport  cmd, ep = null ) 				
{ 
	log.warn "SensorMultilevelSupportedSensorReport caching requires additional code to handle sub-reports!"
	cacheReportByFirmwareVersion (cmd, ep) 
}

void  cacheReportByFirmwareVersion (cmd, ep = null )
{
	// The information cached here is common to all devices with the same manufacturer / device type / device ID  code and firmware version.
	if (ep) log.debug "Endpoint Functionality Not Implemented for report caching"
	log.debug "Received report for caching by Firmware Version with command string ${cmd.CMD} and format ${cmd.format()} and report type ${cmd}."
	storeReportByFirmwareVersion(cmd, ep)
	waitForReport.release(1)

}

void zwaveEvent(hubitat.zwave.commands.multichannelv2.MultiChannelCapabilityReport  cmd)  			{ cacheMCCReportByFirmwareVersion (cmd) }
void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCapabilityReport  cmd)  			{ cacheMCCReportByFirmwareVersion (cmd) }
void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCapabilityReport  cmd)  			{ cacheMCCReportByFirmwareVersion (cmd) }
void  cacheMCCReportByFirmwareVersion (cmd)
{
	// The information cached here is common to all devices with the same manufacturer / device type / device ID  code and firmware version.
	log.debug "Received report for caching by Firmware Version with command string ${cmd.CMD} and format ${cmd.format()} and report type ${cmd}."
	Map deviceStorage = reportCacheByFirmware.get(firmwareKey(), [:])
	
	log.debug "deviceStorage for device ${device.displayName} is: " + deviceStorage
	
	if (deviceStorage.containsKey(cmd.CMD))
	{
		deviceStorage.replace(cmd.CMD, [(cmd.endPoint):(cmd.commandClass)])
	} else 	{
		deviceStorage.put(cmd.CMD, cmd.format())
	}
	
	waitForReport.release(1)
}
//////////////////////////////////////////////////////////////////////
//////        Handle Supervision request and reports           ///////
////////////////////////////////////////////////////////////////////// 
@Field static ConcurrentHashMap<String, Short> supervisionSessionIDs = new ConcurrentHashMap<String, Short>()

Short getSessionID()
{
    Short nextSessionID = supervisionSessionIDs.get(device.getDeviceNetworkId() as String,(Math.random() * 32) % 32 )
	nextSessionID = (nextSessionID + 1) % 32
    supervisionSessionIDs.replace(device.getDeviceNetworkId(), nextSessionID)
	log.debug nextSessionID
    return nextSessionID   
}

hubitat.zwave.Command supervise(String commandString)
{
    hubitat.zwave.Command newCommand = createCommand(commandString)
    supervise(newCommand)
}

hubitat.zwave.Command supervise(hubitat.zwave.Command command)
{
    log.debug "Supervising a command: ${command}"
    if (implementsZwaveClass(0x6C))
        {
            return zwave.supervisionV1.supervisionGet(sessionID: getSessionID(), statusUpdates: true).encapsulate(command)
        } else {
            return command
        }
}

// This handles a supervised message (a "get") received from the Z-Wave device //
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    if (logEnable) log.debug "Device ${device.displayName}: Supervision get: ${cmd}"

    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(parseMap, defaultParseMap)
	
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
    sendToDevice(secure((new hubitat.zwave.commands.supervisionv1.SupervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0))))
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionReport cmd) {
log.debug "Results of supervised message is a report: ${cmd}."
}



//////////////////////////////////////////////////////////////////////
//////                  Z-Wave Helper Functions                ///////
//////   Format messages, Send to Device, secure Messages      ///////
//////////////////////////////////////////////////////////////////////

//// Catch Event Not Otherwise Handled! /////

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "For ${device.displayName}, Received Z-Wave Message that is not handled by this driver: ${cmd.class}, ${cmd}."
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
void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) { processMultichannelEncapsulatedCommand( cmd) }
void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCmdEncap cmd) { processMultichannelEncapsulatedCommand( cmd) }
void processMultichannelEncapsulatedCommand( cmd)
{
    def encapsulatedCommand = cmd.encapsulatedCommand(defaultParseMap)

    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
    }
}

////    Z-Wave Message Parsing   ////
void parse(String description) {
	hubitat.zwave.Command cmd = zwave.parse(description, defaultParseMap)
	log.debug "in parse() received description ${description} and generated command ${cmd} of class ${cmd.class}."
    if (cmd) { zwaveEvent(cmd) }
}

////    Z-Wave Message Sending to Hub  ////
void sendToDevice(List<hubitat.zwave.Command> cmds) { sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds), hubitat.device.Protocol.ZWAVE)) }
void sendToDevice(hubitat.zwave.Command cmd) { sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE)) }
void sendToDevice(String cmd) { sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE)) }

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) { return delayBetween(cmds.collect{ it }, delay) }



//////////////////////////////////////////////////////////////////////
//////        Hubitat Message Handling Helper Functions        ///////
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

hubitat.zwave.Command  createCommand(String commandString)
{
    log.debug "substrings: ${commandString.substring(0,2)}, ${commandString.substring(2,4)}, ${commandString.substring(4)}."
    
    Short commandClass =    hubitat.helper.HexUtils.hexStringToInt(commandString.substring(0,2))
    Short command =         hubitat.helper.HexUtils.hexStringToInt(commandString.substring(2,4))
    List<Short> payload =     hubitat.helper.HexUtils.hexStringToIntArray(commandString.substring(4))
    Short version = defaultParseMap.get(commandClass as Integer)
	hubitat.zwave.Command  cmd = zwave.getCommand(commandClass, command, payload, version)  
	log.debug "Created command using version ${version} is: ${cmd} with a format() string: ${cmd.format()}"
		
	return cmd
}