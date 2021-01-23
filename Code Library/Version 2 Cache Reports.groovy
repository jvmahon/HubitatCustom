import java.util.concurrent.*;
import groovy.transform.Field

/*  Available (whitlisted) concurrency classes:
	ConcurrentHashMap
	ConcurrentLinkedQueue
	Semaphore
	Synchronousqueue
*/


@Field static Map<Integer, Integer> defaultParseMap = [
	0x20:2, // Basic Set
	0x25:1, // Switch Binary
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

metadata {
	definition (name: "[Beta Version 2] Cache Reports",namespace: "jvm", author: "jvm") {
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

hubitat.zwave.Command  getCachedMultiChannelEndPointReport()
hubitat.zwave.Command  getCachedMultiChannelCapabilityReport(Short ep)

hubitat.zwave.Command  getCachedVersionReport()

hubitat.zwave.Command  getCachedNotificationSupportedReport()
hubitat.zwave.Command  getCachedEventSupportedReport(Short notificationType)

hubitat.zwave.Command  getCachedCentralSceneSupportedReport()
hubitat.zwave.Command  getCachedMeterSupportedReport()
hubitat.zwave.Command  getCachedProtectionSupportedReport()
hubitat.zwave.Command  getCachedSwitchMultilevelSupportedReport()
hubitat.zwave.Command  getCachedSensorMultilevelSupportedSensorReport()

*/

@Field static ConcurrentHashMap<String, ConcurrentHashMap> reportCacheByFirmware = new ConcurrentHashMap<String, ConcurrentHashMap>(32)
@Field static ConcurrentHashMap<String, ConcurrentHashMap> versionReportCache = new ConcurrentHashMap<String, ConcurrentHashMap>(64)

@Field static Semaphore gettingReports = new Semaphore(32)



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
	String key
	hubitat.zwave.Command   version = getCachedVersionReport() 
	// log.debug "Version report in firmwareKey is: " + version
	// log.debug "version format is: " + version?.format()
	if (!version)  {
			log.warn "called firmwareKey function but firmware version is not yet cached! Using only device and manufacturer information."
			return productKey()
		} else {
			return productKey() + version?.format()
		}
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

Boolean cachedReportExists(String reportClass, Short index  = null )
{
    if (index) log.warn "EndPoint / Indexed Map Support in cachedReportExists is not fully implemented! Does not support this function function"
	if ( reportClass == "8611") return false // Always force firmwareversion to be retrieved as this starts cycle of retrieving other reports!

	ConcurrentHashMap ByFirmwareStorage = reportCacheByFirmware.get(firmwareKey())
	
	// log.debug "value of ByFirmwareStorage in cachedReportExists is: " + ByFirmwareStorage
	if ( ByFirmwareStorage?.containsKey(reportClass) ) {
		log.debug "A cached report already exists for report class: ${reportClass}!"
		return true
	} else {
		log.debug "A cached report does NOT exists for report class: ${reportClass}. Getting the report!"
		return false
	}
}

///   Functions to store and retrieve Z-Wave reports from the concurrent Hash Maps ///
///  The reports are stored as a hex string which is obtained from cmd.format()    ///

void cacheReportByFirmwareVersion(hubitat.zwave.Command cmd, ep = null )
{
	if (ep) { log.warn "Code Error in function cacheReportByFirmwareVersion, Endpoint unexpected for command ${cmd}."}
	ConcurrentHashMap deviceStorage = reportCacheByFirmware.get(firmwareKey(), new ConcurrentHashMap())
	log.debug "deviceStorage for device ${device.displayName} is: " + deviceStorage
	if (deviceStorage.containsKey(cmd.CMD)) {
		deviceStorage.replace(cmd.CMD, cmd)
	} else 	{
		deviceStorage.put(cmd.CMD, cmd)
	}
}

hubitat.zwave.Command  getCachedReportByFirmwareVersion(String reportClass, ep = null )
{
	ConcurrentHashMap deviceStorage = reportCacheByFirmware.get(firmwareKey())
	if (deviceStorage.is( null ) || (deviceStorage.size() == 0)) return null
	return deviceStorage.get(reportClass)
}

void cacheDeviceReport(String command, ep = null )
{
	Integer reportNumber = hubitat.helper.HexUtils.hexStringToInt(command) + 1
	String reportHexString = hubitat.helper.HexUtils.integerToHexString(reportNumber, 2)
	log.debug "In cacheDeviceReport report string is ${reportHexString} for command ${command}."; 

    if (cachedReportExists(reportHexString, ep))  { 
			log.debug "Already have cached report ${reportHexString} for command ${command}."; 
			return 
		}
    
	// waitForReport.tryAcquire(1, 10, TimeUnit.SECONDS )

	log.debug "Sending to device ${device.displayName} the command string: ${command}."
	sendToDevice(secure(command))
	// waitForReport.tryAcquire(1, 10, TimeUnit.SECONDS )
	// waitForReport.release(1)
}

void getSupportedReports(ep = null )
{
	//  Get the Version Report
		sendToDevice(secure( zwave.versionV3.versionGet() ))
	
	// Everything else is retrieved from the Version Report handler by calling getOtherReports()
}

synchronized void getOtherReports(ep = null )
{
	// Call this from the firmware Report Handler
	
	// MultiChannelEndPointReport Get:0x6007  Report:0x6008
	if (implementsZwaveClass(0x60)) cacheDeviceReport("6007") // Request is only sent to the parent. Don't add ep = null!
	
	// CentralSceneSupportedReport Get:0x5B01  Report:0x5B02
	// Per standards, Central Scene should never have an endpoint!
	if (implementsZwaveClass(0x5B)) cacheDeviceReport("5B01")
		
	// CommandRecordsSupportedReport  Get:  Report:0x9B02
	//	if (implementsZwaveClass(0x9B, ep)) cacheDeviceReport()
		
	// DcpListSupportedReport  Get:  Report:0x3A02
	//	if (implementsZwaveClass(0x3A, ep)) cacheDeviceReport()
		
	// DoorLockLoggingRecordsSupportedReport  Get:  Report:0x4C02
	if (implementsZwaveClass(0x4C)) cacheDeviceReport("4C01")
		

	// HrvControlModeSupportedReport  Get:  Report:0x390B
	//	if (implementsZwaveClass(0x39, ep)) cacheDeviceReport()
		
	// HrvStatusSupportedReport  Get:  Report:0x3704
	//	if (implementsZwaveClass(0x37, ep)) cacheDeviceReport()
		
	// IndicatorSupportedReport  Get:0x8704  Report:0x8705
	// Indicator not implemented -- too complex for now!
	// if (implementsZwaveClass(0x87, ep) && ( classLevel(0x87) > 1)) cacheDeviceReport("XXXXX", ep)
		
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
	log.debug "versionReportCache stored based on DNI is: " + versionReportCache
}

/////////////////  Caching Functions To Store Reports! //////////////////////////////

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) { 
	log.debug "Received and saving a VersionReport: ${cmd}"
	if (versionReportCache.containsKey(device.getDeviceNetworkId()) ) {
			log.debug "Already have a stored version report! Replacing it ..."
			versionReportCache.replace( device.getDeviceNetworkId(), cmd)
		} else {
			versionReportCache.put( device.getDeviceNetworkId(), cmd) 
		}
	getOtherReports()
}

hubitat.zwave.Command  getCachedVersionReport() { return versionReportCache.get( device.getDeviceNetworkId() )}

// //////////////////////////////////////////////////////////////////////
void zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneSupportedReport  cmd)  			{ cacheReportByFirmwareVersion (cmd) }
void zwaveEvent(hubitat.zwave.commands.centralscenev2.CentralSceneSupportedReport  cmd)  			{ cacheReportByFirmwareVersion (cmd) }
void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneSupportedReport  cmd)  			{ cacheReportByFirmwareVersion (cmd) }
void zwaveEvent(hubitat.zwave.commands.meterv2.MeterSupportedReport cmd, ep = null ) 				{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv3.MeterSupportedReport cmd, ep = null ) 				{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv4.MeterSupportedReport cmd, ep = null ) 				{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv5.MeterSupportedReport cmd, ep = null ) 				{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.protectionv2.ProtectionSupportedReport  cmd, ep = null )   	{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelSupportedReport  cmd, ep = null )   	{ cacheReportByFirmwareVersion (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelSupportedSensorReport  cmd, ep = null ) 				
{ 
	log.warn "SensorMultilevelSupportedSensorReport caching requires additional code to handle sub-reports!"
	cacheReportByFirmwareVersion (cmd, ep) 
}

hubitat.zwave.Command  getCachedCentralSceneSupportedReport()			{ return (getCachedReportByFirmwareVersion("5B02"))}
hubitat.zwave.Command  getCachedMeterSupportedReport()					{ return (getCachedReportByFirmwareVersion("3204"))}
hubitat.zwave.Command  getCachedProtectionSupportedReport()				{ return (getCachedReportByFirmwareVersion("7505"))}
hubitat.zwave.Command  getCachedSwitchMultilevelSupportedReport()		{ return (getCachedReportByFirmwareVersion("2607"))}
hubitat.zwave.Command  getCachedSensorMultilevelSupportedSensorReport()	{ return (getCachedReportByFirmwareVersion("3102"))}


////////////  Code to Cache and Retrieve Notification Reports - requires Special Handling!  /////////////
void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationSupportedReport cmd, ep = null ) 	
{ 
	cacheReportByFirmwareVersion (cmd, ep) 

	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Integer) == ep}
	} else { targetDevice = device }		
	
	if (ep) log.warn "Device ${device.displayName}: Endpoint handling in report type NotificationSupportedReport is incomplete! Alert developer."

	if (logEnable) log.debug "Device ${device.displayName}: Received Notification Supported Report: " + cmd 
		
	List<hubitat.zwave.Command> cmds=[]
		
		if (cmd.smoke)				cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 1)) // Smoke
		if (cmd.co)					cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 2)) // CO
		if (cmd.co2)				cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 3)) // CO2
		if (cmd.heat)				cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 4)) // Heat
		if (cmd.water)				cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 5)) // Water
		if (cmd.accessControl) 		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 6)) // Access Control
		if (cmd.burglar)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 7)) // Burglar
		if (cmd.powerManagement)	cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 8)) // Power Management
		if (cmd.system)				cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 9)) // System
		if (cmd.emergency)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 10)) // Emergency Alarm
		if (cmd.clock)				cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 11)) // Clock
		if (cmd.appliance)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 12)) // Appliance
		if (cmd.homeHealth)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 13))// Home Health
		if (cmd.siren)				cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 14)) // Siren
		if (cmd.waterValve)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 15)) // Water Valve
		if (cmd.weatherAlarm)		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 16)) // Weather Alarm
		if (cmd.irrigation)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 17)) // Irrigation
		if (cmd.gasAlarm)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 18)) // Gas Alarm
		if (cmd.pestControl)		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 19)) // Pest Control
		if (cmd.lightSensor)		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 20)) // Light Sensor
		if (cmd.waterQuality)		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 21)) // Water Quality
		if (cmd.homeMonitoring)		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 22)) // Home Monitoring

	if (cmds) sendToDevice(secure(cmds))
}

hubitat.zwave.Command  getCachedNotificationSupportedReport()			{ return (getCachedReportByFirmwareVersion("7108"))}


void zwaveEvent(hubitat.zwave.commands.notificationv8.EventSupportedReport cmd, ep = null )   		
{ 

	log.debug "Received a EventSupportedReport: ${cmd}."
	cacheReportByFirmwareAndIndex(cmd, cmd.notificationType)
	log.debug "Cached EventSupportedReport Report is: " + getCachedEventSupportedReport(cmd.notificationType)
}

hubitat.zwave.Command  getCachedEventSupportedReport(Short notificationType){ 
	ConcurrentHashMap deviceStorage = reportCacheByFirmware.get(firmwareKey(), new ConcurrentHashMap())
	ConcurrentHashMap indexedMap = deviceStorage.get("7102", new ConcurrentHashMap())
	return indexedMap.get(notificationType)
}	
	

////////////  Code to Cache and Retrieve MultiChannel Reports - requires Special Handling!  /////////////

void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelEndPointReport  cmd)  			
{ 
	// Cache the original report, then get the endpoint information
	cacheReportByFirmwareVersion (cmd) 
	for( Short ep = 1; ep <= cmd.endPoints; ep++)
	{
		// log.debug "Iterating through MultiChannelEndPointReport for endpoint: ${ep}."
		sendToDevice(secure(zwave.multiChannelV4.multiChannelCapabilityGet(endPoint: ep)))
	}
}

hubitat.zwave.Command  getCachedMultiChannelEndPointReport()			{ return (getCachedReportByFirmwareVersion("6008"))}


hubitat.zwave.Command  getCachedMultiChannelCapabilityReport(Short ep){ 
	ConcurrentHashMap deviceStorage = reportCacheByFirmware.get(firmwareKey(), new ConcurrentHashMap())
	ConcurrentHashMap indexedMap = deviceStorage.get("600A", new ConcurrentHashMap())
	return indexedMap.get(ep)
}

void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCapabilityReport  cmd)  			
{ 
	log.debug "Received a MultiChannelCapabilityReport: ${cmd}."
	cacheReportByFirmwareAndIndex(cmd, cmd.endPoint)
	log.debug "Cached Report is: " + getCachedMultiChannelCapabilityReport(cmd.endPoint)
}

void cacheReportByFirmwareAndIndex(hubitat.zwave.Command cmd, Short index = null )
{
	ConcurrentHashMap deviceStorage = reportCacheByFirmware.get(firmwareKey(), new ConcurrentHashMap())
	ConcurrentHashMap indexedMap = deviceStorage.get(cmd.CMD, new ConcurrentHashMap())
	log.debug "IndexedMap for device ${device.displayName} is: " + indexedMap
	indexedMap.put(index, cmd)
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
    hubitat.zwave.Command  encapsulatedCommand = cmd.encapsulatedCommand(defaultParseMap)

    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
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

