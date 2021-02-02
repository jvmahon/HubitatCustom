import java.util.concurrent.* // Available (whitlisted) concurrency classes: ConcurrentHashMap, ConcurrentLinkedQueue, Semaphore, Synchronousqueue
import groovy.transform.Field

metadata {
	definition (name: "[Beta Version 3] Cache Reports",namespace: "jvm", author: "jvm") {
		capability "Configuration"
		capability "Initialize"
		capability "Refresh"
		command "getSupportedReports"
		command "logStoredReportCache"
        command "getSessionID"
        command "turnOn"
		command "showCommandClassReport"
		command "clearState"
		command "showReports"
    }
    preferences 
	{
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: true
		
		// The following preferences are only for use while debugging. Remove them from final code
		input name: "remindEnable", type: "bool", title: "Enable Code To-Do Reminders", defaultValue: true
    }
}

@Field static Map<Integer, Integer> defaultParseMap = [
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
// ================ functions for Debugging ! ==================
void turnOn()
{
	sendToDevice(secure(supervise(zwave.basicV1.basicSet(value: 0xFF))))
}
void clearState()
{
	state.clear()
}

void showCommandClassReport()
{
	log.debug "Command Class Report is: " + getCachedVersionCommandClassReport()
}

void showReports()
{
log.debug "Report for class: 6007 is: " + getCachedReport("6007")
log.debug "Report for class: 6008 is: " + getCachedReport("6008")
log.debug "Report for class: 8611 is: " + getCachedReport("8611")
log.debug "Report for class: 8612 is: " + getCachedReport("8612")
}

// ==================================================
void endpointRefresh(ep = null )
{
	List<hubitat.zwave.Command> cmds = []
		if (implementsZwaveClass(0x25, ep)) cmds << secure(zwave.switchBinaryV2.switchBinaryGet(), ep)
		if (implementsZwaveClass(0x28, ep)) cmds << secure(zwave.switchToggleBinaryV1.switchToggleBinaryGet(), ep)
		if (implementsZwaveClass(0x26, ep)) cmds << secure(zwave.switchMultilevelv1.switchMultilevelGet(), ep)
		// Should not have to refresh using basicGet! Per standards - should use the specific classes!
		// if (implementsZwaveClass(0x20, ep)) cmds << secure(zwave.basicV1.basicGet(), ep)
		if (implementsZwaveClass(0x62, ep)) cmds << secure(zwave.doorlockV1.doorLockOperationGet(), ep)
		if (implementsZwaveClass(0x76, ep)) cmds << secure(zwave.lockV1.lockGet(), ep)

		if (implementsZwaveClass(0x80, ep)) cmds << secure(zwave.batteryV1.batteryGet())
		if (implementsZwaveClass(0x81, ep)) cmds << secure(zwave.clockV1.clockGet())
	log.debug "Refreshing with commands: " + cmds
	if (cmds) sendToDevice(cmds)
}

void refresh()
{
	if (implementsZwaveClass(0x60))
	{
		hubitat.zwave.Command  report = getCachedMultiChannelEndPointReport()
		for ( Short ep = 1; ep <= report.endPoints; ep++)
		{
			endpointRefresh(ep)
		}
	} else {
		endpointRefresh()
	}
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

Map<Short, Short> getCachedVersionCommandClassReport()

*/

@Field static ConcurrentHashMap<String, ConcurrentHashMap> reportCacheByFirmware = new ConcurrentHashMap<String, ConcurrentHashMap>(32)
@Field static ConcurrentHashMap<String, ConcurrentHashMap> versionReportCache = new ConcurrentHashMap<String, ConcurrentHashMap>(64)

///   Functions to generate keys used to access the concurrent Hash Maps and to store into the hash maps ///

String productKey() // Generates a key based on manufacturere / device / firmware. Data is shared among all similar end-devices.
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
	hubitat.zwave.Command   version = getCachedVersionReport() 
	if (!version)  {
			log.warn "called firmwareKey function but firmware version is not yet cached! Using only device and manufacturer information."
			return productKey()
		} else {
			return productKey() + version?.format()
		}
}

///   Functions to Test if classes are implemented by the device ///

Boolean implementsZwaveClass(zwaveCommandClass, Short ep = null )
{
log.warn "Confirm / Fix endpont handling code in implementsZwaveClass"
log.debug "called implementsZwaveClass for class: ${zwaveCommandClass}, and endpoint ${ep}."
	if (ep)
	{
		hubitat.zwave.Command cmd = getCachedMultiChannelCapabilityReport(ep as Short)
		log.debug " the multiChannel report in implementsZwaveClass for this device is ${cmd}."
		return cmd.commandClass.contains(zwaveCommandClass as Short) 
	} else {
		// log.debug "Does it contain the class: " + getCachedVersionCommandClassReport()?.containsKey(zwaveCommandClass as Short)
		return getCachedVersionCommandClassReport()?.containsKey(zwaveCommandClass as Short)
	}
}

Short classLevel(Short zwaveCommandClass)
{
	return getCachedVersionCommandClassReport().get(zwaveCommandClass as Short)
}

Boolean cachedReportExists(String reportClass, Short ep = null )
{
	ConcurrentHashMap deviceStorage = reportCacheByFirmware.get(firmwareKey())
	
	log.debug "value of deviceStorage in cachedReportExists is: " + deviceStorage
	if (deviceStorage.is( null )) return false

	ConcurrentHashMap endPointStorage = deviceStorage.get((ep ?: 0) as Short)
	log.debug "value of endPointStorage in cachedReportExists is: " + endPointStorage
	if (endPointStorage.is( null )) return false
	
	if ( endPointStorage.containsKey(reportClass) ) {
		log.debug "A cached report already exists for report class: ${reportClass}. cachedReportExists returning true"
		return true
	} else if ( getCachedVersionReport() && (reportClass == "8612")) {
		log.debug "A cached Version report already exists for report class: ${reportClass}. cachedReportExists returning true"
		return true	
	} else {
		log.debug "A cached report does NOT exists for report class: ${reportClass}. cachedReportExists returning false!"
		return false
	}
}

///   Functions to store and retrieve Z-Wave reports from the concurrent Hash Maps ///
///  The reports are stored as a hex string which is obtained from cmd.format()    ///


void cacheReport(hubitat.zwave.Command cmd, ep = null )
{
	ConcurrentHashMap deviceStorage = reportCacheByFirmware.get(firmwareKey(), new ConcurrentHashMap<Short,ConcurrentHashMap>())
	ConcurrentHashMap endPointStorage = deviceStorage.get((ep ?: 0) as Short, new ConcurrentHashMap<String,Object>())
	
	log.debug "endPointStorage for device ${device.displayName} and endpoint ${(ep ?: 0)} is: " + endPointStorage
	if (endPointStorage.containsKey(cmd.CMD)) {
		endPointStorage.replace(cmd.CMD, cmd)
	} else 	{
		endPointStorage.put(cmd.CMD, cmd)
	}
}


hubitat.zwave.Command getCachedReport(String reportClass, ep  = null )
{
	ConcurrentHashMap deviceStorage = reportCacheByFirmware.get(firmwareKey())
	ConcurrentHashMap endPointStorage = deviceStorage?.get((ep ?: 0) as Short)
	if ( endPointStorage.is( null )) return null
	
	report = endPointStorage.get(reportClass)
	if ( report ) return report
	
	report = getCachedVersionReport()
	if ( report && (reportClass == "8612") )  return report
	
	return null
}

Map getCachedReportAsMap(String reportClass, ep  = null )
{
	ConcurrentHashMap deviceStorage = reportCacheByFirmware.get(firmwareKey())
	ConcurrentHashMap endPointStorage = deviceStorage?.get((ep ?: 0) as Short)
	return endPointStorage?.get(reportClass)
}


void getThenCacheDeviceReport(String command, Short ep = null )
{
	// A previously stored report has a command string represented as a hex string. Its corresponding report has a string, but the number is +1 to the command.
	// So, convert the command string to a number, add 1, then convert it back to a hex string to find the report's key.
	Integer reportNumber = hubitat.helper.HexUtils.hexStringToInt(command) + 1
	String reportHexString = hubitat.helper.HexUtils.integerToHexString(reportNumber, 2)
    if (cachedReportExists(reportHexString, ep))  
	{ 
		hubitat.zwave.Command cmd = getCachedReport(reportHexString, ep )
	
		log.debug "Using Stored Report to re-trigger event handler for command ${command} and report ${reportHexString} which is ${cmd}."
		log.warn "Using Stored Report to re-trigger event handler for command ${command} and report ${reportHexString} which is ${cmd}."

		if (cmd) { zwaveEvent(cmd, ep) }
		return 
	}
	log.debug "Sending to device ${device.displayName} the command string: ${command}."
	sendToDevice(secure(command, ep))
}

@Field static Semaphore versionGetSemaphor = new Semaphore(1)

synchronized void getSupportedReports(Short ep = null )
{
	//  Get the Version Report then everything else is retrieved from the Version Report handler by calling getOtherReports()
	versionGetSemaphor.tryAcquire(1, 10, TimeUnit.SECONDS)

	getThenCacheDeviceReport( zwave.versionV3.versionGet().CMD )
	
	Boolean success = versionGetSemaphor.tryAcquire(1, 10, TimeUnit.SECONDS)
	log.debug "Acquired firmware version success = ${success}."
	if (! success) // Try a second time.
	{
		getThenCacheDeviceReport( zwave.versionV3.versionGet().CMD )
		versionGetSemaphor.tryAcquire(1, 5, TimeUnit.SECONDS)
	}
	
	versionGetSemaphor.release(1)
}

@Field static Semaphore getClassVersionReportSemaphors = new Semaphore(32)

void 	getCommandClassVersionReports()
{
		List<Integer> 	deviceClasses = getDataValue("inClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Integer }
					deviceClasses += getDataValue("secureInClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Integer }
					if (!deviceClasses.contains(32)) deviceClasses += 32
		
		List<hubitat.zwave.Command> cmds = []
		deviceClasses.each{
			if (! implementsZwaveClass(it.toInteger())) 
				{
					cmds << secure(zwave.versionV3.versionCommandClassGet(requestedCommandClass:it.toInteger()))
				} else {
					log.debug "Already have command class information for class: ${it.toInteger()}."
				}
		}
		if (cmds) 
		{
			log.debug "TimeUnit.SECONDS is: " + TimeUnit.SECONDS
			getClassVersionReportSemaphors.tryAcquire(Math.min(cmds.size(), 32), 5, TimeUnit.SECONDS)
			sendToDevice(cmds)
		}
		getClassVersionReportSemaphors.tryAcquire(32, 5, TimeUnit.SECONDS)
		getClassVersionReportSemaphors.release(32)
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionCommandClassReport cmd) 
{	
	log.debug "Received a VersionCommandClassReport: ${cmd}."

	ConcurrentHashMap deviceStorage = reportCacheByFirmware.get(firmwareKey(), new ConcurrentHashMap<Short,ConcurrentHashMap>())
	ConcurrentHashMap endPointStorage = deviceStorage.get( 0 as Short, new ConcurrentHashMap<String, ConcurrentHashMap<Short, Short>>())
	ConcurrentHashMap commandClassMap = endPointStorage.get("8614", new ConcurrentHashMap<Short, Short>())
	
	if (! commandClassMap.containsKey(cmd.requestedCommandClass)) {
			commandClassMap.put(cmd.requestedCommandClass, cmd.commandClassVersion)		
		}
	getClassVersionReportSemaphors.release(1)
}
Map<Short, Short> getCachedVersionCommandClassReport() { return getCachedReportAsMap("8614") }


synchronized void getOtherReports(ep = null )
{
	getCommandClassVersionReports()
	// Call this from the firmware Report Handler
	
	// MultiChannelEndPointReport Get:0x6007  Report:0x6008
	if (implementsZwaveClass(0x60)) getThenCacheDeviceReport("6007") // Request is only sent to the parent. Don't add ep = null!
	
	// CentralSceneSupportedReport Get:0x5B01  Report:0x5B02
	// Per standards, Central Scene should never have an endpoint!
	if (implementsZwaveClass(0x5B)) getThenCacheDeviceReport("5B01")
		
	// CommandRecordsSupportedReport  Get:  Report:0x9B02
	//	if (implementsZwaveClass(0x9B, ep)) getThenCacheDeviceReport()
		
	// DcpListSupportedReport  Get:  Report:0x3A02
	//	if (implementsZwaveClass(0x3A, ep)) getThenCacheDeviceReport()
		
	// DoorLockLoggingRecordsSupportedReport  Get:  Report:0x4C02
	if (implementsZwaveClass(0x4C)) getThenCacheDeviceReport("4C01")
		

	// HrvControlModeSupportedReport  Get:  Report:0x390B
	//	if (implementsZwaveClass(0x39, ep)) getThenCacheDeviceReport()
		
	// HrvStatusSupportedReport  Get:  Report:0x3704
	//	if (implementsZwaveClass(0x37, ep)) getThenCacheDeviceReport()
		
	// IndicatorSupportedReport  Get:0x8704  Report:0x8705
	// Indicator not implemented -- too complex for now!
	// if (implementsZwaveClass(0x87, ep) && ( classLevel(0x87) > 1)) getThenCacheDeviceReport("XXXXX", ep)
		
	// MeterSupportedReport  Get:0x3203  Report:0x3204
	if (implementsZwaveClass(0x32, ep)) getThenCacheDeviceReport("3203", ep)
		
	// MeterTblStatusSupportedReport  Get:  Report:0x3D08
	//	if (implementsZwaveClass(0x3D, ep)) getThenCacheDeviceReport()
		
	// NotificationSupportedReport  Get:0x7107  Report:0x7108
	if (implementsZwaveClass(0x71, ep)) getThenCacheDeviceReport("7107", ep)
		
	// PrepaymentSupportedReport  Get:  Report:0x3F04
	if (implementsZwaveClass(0x3F, ep)) getThenCacheDeviceReport("3F03")
		
	// ProtectionSupportedReport  Get:0x7504  Report:0x7505
	if (implementsZwaveClass(0x75, ep) && ( classLevel(0x75) > 1)) getThenCacheDeviceReport("7504", ep)
		
	// RateTblSupportedReport  Get:  Report:0x4902
	//	if (implementsZwaveClass(0x49, ep)) getThenCacheDeviceReport()
		
	// ScheduleEntryTypeSupportedReport  Get:  Report:0x4E0A
	// Deprecated
	//	if (implementsZwaveClass(0x4E, ep)) getThenCacheDeviceReport()
		
	// ScheduleSupportedReport  Get:  Report:0x5302
	//	if (implementsZwaveClass(0x53, ep)) getThenCacheDeviceReport()
		
	// SecurityCommandsSupportedReport  Get:  Report:0x9803
	// Not necessary.Hubitat will provde this as secureInClusters
	// if (implementsZwaveClass(0x98, ep)) getThenCacheDeviceReport()
		
	// SecurityPanelModeSupportedReport   Get:  Report:0x2402
	//	if (implementsZwaveClass(0x24, ep)) getThenCacheDeviceReport ()
		
	// SecurityPanelZoneSupportedReport  Get:  Report:0x2E02
	//	if (implementsZwaveClass(0x2E, ep)) getThenCacheDeviceReport()
		
	// SensorAlarmSupportedReport  Get:  Report:0x9C04
	// Deprecated
	//	if (implementsZwaveClass(0x9C, ep)) getThenCacheDeviceReport()
	
	// SensorMultilevelSupportedSensorReport   Get:0x3101  Report:0x3101
	if (implementsZwaveClass(0x31, ep)) getThenCacheDeviceReport("3101", ep)	
		
	// SimpleAvControlSupportedReport  Get:  Report:0x9405
	if (implementsZwaveClass(0x94, ep)) getThenCacheDeviceReport("9404")
		
	// SwitchColorSupportedReport  Get:  Report:0x3302
	if (implementsZwaveClass(0x33, ep)) getThenCacheDeviceReport("3301")
		
	// SwitchMultilevelSupportedReport  Get:0x2606  Report:0x2607
	// Only supported by V3 or later devices!
	if (implementsZwaveClass(0x26, ep) && (classLevel(0x26) > 2)) getThenCacheDeviceReport("2606", ep)
		
	// ThermostatFanModeSupportedReport  Get:  Report:0x4405
	if (implementsZwaveClass(0x44)) getThenCacheDeviceReport("4404")
		
	// ThermostatModeSupportedReport  Get:  Report:0x4005
	//	if (implementsZwaveClass(0x40)) getThenCacheDeviceReport()
		
	// ThermostatOperatingLoggingSupportedReport  Get:  Report:0x4204
	//	if (implementsZwaveClass(0x42)) getThenCacheDeviceReport()
		
	// ThermostatSetpointSupportedReport  Get:  Report:0x4305
	//	if (implementsZwaveClass(0x43)) getThenCacheDeviceReport()
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
	versionGetSemaphor.release(1)
	getOtherReports()
}

hubitat.zwave.Command  getCachedVersionReport() { return versionReportCache.get( device.getDeviceNetworkId() )}

// //////////////////////////////////////////////////////////////////////
void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneSupportedReport  cmd)  						{ cacheReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.meterv5.MeterSupportedReport cmd, ep = null ) 							{ cacheReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.protectionv2.ProtectionSupportedReport  cmd, ep = null )   				{ cacheReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelSupportedReport  cmd, ep = null )   	{ cacheReport(cmd, ep) }

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelSupportedSensorReport  cmd, ep = null ) 				
{ 
	log.warn "SensorMultilevelSupportedSensorReport caching requires additional code to handle sub-reports!"
	cacheReport(cmd, ep) 
}

hubitat.zwave.Command  getCachedCentralSceneSupportedReport()			{ return (getCachedReport("5B02"))}
hubitat.zwave.Command  getCachedMeterSupportedReport()					{ return (getCachedReport("3204"))}
hubitat.zwave.Command  getCachedProtectionSupportedReport()				{ return (getCachedReport("7505"))}
hubitat.zwave.Command  getCachedSwitchMultilevelSupportedReport()		{ return (getCachedReport("2607"))}
hubitat.zwave.Command  getCachedSensorMultilevelSupportedSensorReport()	{ return (getCachedReport("3102"))}


////////////  Code to Cache and Retrieve Notification Reports - requires Special Handling!  /////////////
void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationSupportedReport cmd, ep = null ) 	
{ 
	cacheReport(cmd, ep) 

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

hubitat.zwave.Command  getCachedNotificationSupportedReport()			{ return (getCachedReport("7108"))}


void zwaveEvent(hubitat.zwave.commands.notificationv8.EventSupportedReport cmd, ep = null )   		
{ 

	log.warn "Received a EventSupportedReport: ${cmd} which is not yet cached properly!."
	// cacheReport(cmd, cmd.notificationType)
	log.debug "Cached EventSupportedReport Report is: " + getCachedEventSupportedReport(cmd.notificationType)
}

hubitat.zwave.Command  getCachedEventSupportedReport(Short notificationType){ 
	log.warn "Received a EventSupportedReport: ${cmd} which is not yet cached properly!."
	return null
	
	// fix this code!
	ConcurrentHashMap deviceStorage = reportCacheByFirmware.get(firmwareKey(), new ConcurrentHashMap())
	ConcurrentHashMap indexedMap = deviceStorage.get("7102", new ConcurrentHashMap())
	return indexedMap.get(notificationType)
}	
	

////////////  Code to Cache and Retrieve MultiChannel Reports - requires Special Handling!  /////////////
@Field static Semaphore multiChannelGetSemaphors = new Semaphore(32)

void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelEndPointReport  cmd)  			
{ 
	// Cache the original report, it is always cached without an endpoint!. Then get the endpoint information
	cacheReport(cmd) 
	
	List<hubitat.zwave.Command> cmds = []
	 
	log.warn "Add code to MultiChannelEndPointReport processing so it doesn't re-get the endpoint multiChannelCapabilityGet if endpoint report is already stored!"
	
	Integer neededReports = 0
	log.debug "Getting Semaphors in function multiChannelGetSemaphors. Number available: ${multiChannelGetSemaphors.availablePermits()}."
	Boolean gotLock = multiChannelGetSemaphors.tryAcquire(32, 10, TimeUnit.SECONDS)
	
	for( Short ep = 1; ep <= cmd.endPoints; ep++)
	{
		hubitat.zwave.Command capabilityReport = getCachedMultiChannelCapabilityReport(ep)
		
		if (capabilityReport) { 
				log.debug "already have capability report for endpoint ${ep}."
				zwaveEvent (capabilityReport, ep)
				continue 
			}
		// log.debug "Iterating through MultiChannelEndPointReport for endpoint: ${ep}."
		
		neededReports += 1
		cmds << secure(zwave.multiChannelV4.multiChannelCapabilityGet(endPoint: ep))
		cmds << "delay 500"
	}
	multiChannelGetSemaphors.release(32 - neededReports)

	if (cmds)
	{ 	
		sendToDevice(cmds)
		log.debug "Waiting to ReAcquire Semaphors in function multiChannelGetSemaphors. Number available: ${multiChannelGetSemaphors.availablePermits()}."
		Boolean success = multiChannelGetSemaphors.tryAcquire(32, 10, TimeUnit.SECONDS)
		if (!success) 
		{
			log.warn "Failed to ReAcquire All Semaphors in function multiChannelGetSemaphors. Number available: ${multiChannelGetSemaphors.availablePermits()}."
			if (gotLock) multiChannelGetSemaphors.release(neededReports)

		}
			log.debug "Released all semaphors!"
		}
	}
}

hubitat.zwave.Command  getCachedMultiChannelEndPointReport()			{ return (getCachedReport("6008"))}


void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCapabilityReport  cmd)  			
{ 
	log.debug "Received a MultiChannelCapabilityReport: ${cmd}."
	cacheReport(cmd, cmd.endPoint)
	multiChannelGetSemaphors.release(1)

	log.debug "Cached MultiChannelCapabilityReport is: " + getCachedMultiChannelCapabilityReport(cmd.endPoint)
}

hubitat.zwave.Command  getCachedMultiChannelCapabilityReport(Short ep) { return (getCachedReport("600A", ep))}


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

//// Catch Event Not Otherwise Handled! /////

void zwaveEvent(hubitat.zwave.Command cmd, ep = null) {
    log.debug "For ${device.displayName}, Received Z-Wave Message ${cmd} that is not handled by this driver. Endpoint: ${ep}."
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
	setLevelForDevice(level, transitionTime, cd)
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

// @Field static  ConcurrentHashMap<Long, Boolean> EventTypeIsDigital = new ConcurrentHashMap<Long, Boolean>()

Boolean isDigitalEvent() { return getDeviceMapByNetworkID().get("EventTypeIsDigital") as Boolean }
void setIsDigitalEvent(Boolean value) { 
	log.warn "setIsDigitalEvent is currently a stub function returning false!"
	// getDeviceMapByNetworkID().put("EventTypeIsDigital", value as Boolean)
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv2.SwitchBinaryReport cmd, ep = null)
{
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Integer) == ep}
	} else { targetDevice = device }	

	if (! targetDevice.hasAttribute("switch")) log.warn "For device ${targetDevice.displayName}, received a Switch Binary Report for a device that does not have a switch!"
	
	String priorSwitchState = targetDevice.currentValue("switch")
	String newSwitchState = ((cmd.value > 0) ? "on" : "off")
	
    if (priorSwitchState != newSwitchState) // Only send the state report if there is a change in switch state!
	{
		targetDevice.sendEvent(	name: "switch", value: newSwitchState, 
						descriptionText: "Device ${targetDevice.displayName} set to ${newSwitchState}.", 
						type: isDigitalEvent() ? "digital" : "physical" )
		if (txtEnable) log.info "Device ${targetDevice.displayName} set to ${newSwitchState}."
	}
	setIsDigitalEvent( false )
}


void zwaveEvent(hubitat.zwave.commands.basicv2.BasicReport cmd, ep = null) 							{ processDeviceReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv4.SwitchMultilevelReport cmd, ep = null)	{ processDeviceReport(cmd, ep) }
void processDeviceReport(cmd,  ep)
{
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Integer) == ep}
	} else { targetDevice = device }	

	Boolean hasSwitch = targetDevice.hasAttribute("switch")
	Boolean hasDimmer = targetDevice.hasAttribute("level")  || targetDevice.hasAttribute("position")
	Boolean turnedOn = false
	Integer newLevel = 0

	if ((! (cmd.duration.is( null ) || cmd.targetValue.is( null ) )) && ((cmd.duration as Integer) > (0 as Integer))) //  Consider duration and target, but only when both are present and in transition with duration > 0 
	{
		turnedOn = (cmd.targetValue as Integer) != (0 as Integer)
		newLevel = (cmd.targetValue as Integer)
	} else {
		turnedOn = (cmd.value as Integer) > (0 as Integer)
		newLevel = cmd.value as Integer
	}
	
	String priorSwitchState = targetDevice.currentValue("switch")
	String newSwitchState = (turnedOn ? "on" : "off")
	Integer priorLevel = targetDevice.currentValue("level")
	Integer targetLevel

	if (newLevel == 99)
	{
		if ( priorLevel == 100) targetLevel = 100
		if ( priorLevel == 99) targetLevel = 99
	} else targetLevel = newLevel
	
    if (hasSwitch && (priorSwitchState != newSwitchState))
	{
		targetDevice.sendEvent(	name: "switch", value: newSwitchState, 
						descriptionText: "Device ${targetDevice.displayName} set to ${newSwitchState}.", 
						type: isDigitalEvent() ? "digital" : "physical" )
		if (txtEnable) log.info "Device ${targetDevice.displayName} set to ${newSwitchState}."
	}
	if (hasDimmer && turnedOn) // If it was turned off, that would be handle in the "hasSwitch" block above.
	{
		// Don't send the event if the level doesn't change except if transitioning from off to on, always send!
		if ((priorLevel != targetLevel) || (priorSwitchState != newSwitchState))
		{
			targetDevice.sendEvent( 	name: "level", value: targetLevel, 
					descriptionText: "Device ${targetDevice.displayName} level set to ${targetLevel}%", 
					type: isDigitalEvent() ? "digital" : "physical" )
			if (txtEnable) log.info "Device ${targetDevice.displayName} level set to ${targetLevel}%"		
		}
	}

	if (!hasSwitch && !hasDimmer) log.warn "For device ${targetDevice.displayName} receive a report which wasn't processed. Need to check report handling code." + cmd
	setIsDigitalEvent( false )
}

void on(cd = null ) {
log.debug "called the On function with child device ${cd?.displayName}"
	def targetDevice = (cd ? cd : device)
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null

	if (implementsZwaveClass(0x26, ep)) // Multilevel  type device
	{ 
		Integer level = (targetDevice.currentValue("level") as Integer) ?: 100
        level = ((level < 1) || (level > 100)) ? 100 : level // If level got set to less than 1 somehow,then turn it on to 100%
		if (txtEnable) log.info "Device ${targetDevice.displayName}: Turned On at Level: ${level}."

		sendToDevice(secure(supervise(zwave.switchMultilevelV4.switchMultilevelSet(value: ((level > 99) ? 99 : level))), ep)	)	
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
		sendToDevice(secure(supervise(zwave.basicV1.basicSet(value: 255 )), ep))
		targetDevice.sendEvent(name: "switch", value: "on", descriptionText: "Device ${targetDevice.displayName} turned on", type: "digital")
	} else 
	{
		log.debug "Error in function on() - device ${targetDevice.displayName} does not implement a supported class"
	}
}

void off(cd = null ) {
	def targetDevice = (cd ? cd : device)
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	if (txtEnable) log.info "Device ${targetDevice.displayName}: Turning device to: Off."

	if (implementsZwaveClass(0x26, ep)) { // Multilevel  type device
		sendToDevice(secure(supervise(zwave.switchMultilevelV4.switchMultilevelSet(value: 0)), ep)	)	
	} else if (implementsZwaveClass(0x25, ep)) { // Switch Binary Type device
		sendToDevice(secure(supervise(zwave.switchBinaryV1.switchBinarySet(switchValue: 0 )), ep))
	} else if (implementsZwaveClass(0x20, ep)) { // Basic Set Type device
		log.warn "Using Basic Set to turn on device ${targetDevice.displayName}. A more specific command class should be used!"
		sendToDevice(secure(supervise(zwave.basicV1.basicSet(value: 0 )), ep))
	} else {
		log.debug "Error in function off() - device ${targetDevice.displayName} does not implement a supported class"
		return
	}
	targetDevice.sendEvent(name: "switch", value: "off", descriptionText: "Device ${targetDevice.displayName} turned off", type: "digital")			
}

void setLevel(level) 									{ setLevelForDevice(level, 0, 			null ) } 
void setLevel(level, duration) 							{ setLevelForDevice(level, duration, 	null ) } 
void setLevelForDevice(level, duration, cd)
{
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	
	if (logEnable) log.debug "Device ${targetDevice.displayName}: Executing function setlevel(level = ${level}, duration = ${duration})."
	if ( level < 0  ) level = 0
	if ( level > 100 ) level = 100
	if ( duration < 0 ) duration = 0
	if ( duration > 120 ) 
		{
			log.warn "Device ${targetDevice.displayName}: tried to set a dimming duration value greater than 120 seconds. To avoid excessive turn on / off delays, this driver only allows dimming duration values of up to 127."
			duration = 120
		}

	if (level == 0)
	{
		// Turn off the switch, but don't change level -- it gets used when turning back on!
		Boolean stateChange = ((targetDevice.currentValue("level") != 0) ? true : false)
		
		if (getZwaveClassVersionMap().get(38 as Integer) < 2)
		{
			sendToDevice(secure(supervise(zwave.switchMultilevelV1.switchMultilevelSet(value: 0)), ep))
			log.warn "${targetDevice.displayName} does not support dimming duration setting command. Defaulting to dimming duration set by device parameters."
		} else {
			sendToDevice(secure(supervise(zwave.switchMultilevelV2.switchMultilevelSet(value: 0, dimmingDuration: duration)), ep))
		}
		targetDevice.sendEvent(name: "switch", value: "off", descriptionText: "Device ${targetDevice.displayName} remains at off", type: "digital")
		// Return after sending the switch off
		return
	}
	// If turning the device on, then ...
	if (targetDevice.hasCapability("SwitchLevel")) {		// Device is a dimmer!
		if (getZwaveClassVersionMap().get(38 as Integer) < 2)
		{
			sendToDevice(secure(supervise(zwave.switchMultilevelV1.switchMultilevelSet(value: ((level > 99) ? 99 : level)   )), ep))
			if (logEnable) log.warn "${targetDevice.displayName} does not support dimming duration setting command. Defaulting to dimming duration set by device parameters."
		} else {
			sendToDevice(secure(supervise(zwave.switchMultilevelV2.switchMultilevelSet(value: ((level > 99) ? 99 : level), dimmingDuration: duration)), ep))
		}
	} else if (targetDevice.hasCapability("Switch")) {   // Device is a non-dimming switch, but can still send the Z-wave level value
		// To turn on a non-dimming switch in response to a setlevel command!"
		sendToDevice(secure(supervise(zwave.basicV1.basicSet(value: ((level > 99) ? 99 : level) ))), ep)
	} else {
		if (logEnable) log.debug "Received a setLevel command for device ${targetDevice.displayName}, but this is neither a switch or a dimmer device."
	return
	}
		
	if (logEnable) log.debug "For device ${targetDevice.displayName}, current switch value is ${targetDevice.currentValue("switch")}"
	if (targetDevice.currentValue("switch") == "off") 
	{	
		if (logEnable) log.debug "Turning switch from off to on in setlevel function"
		targetDevice.sendEvent(name: "switch", value: "on", descriptionText: "Device ${targetDevice.displayName} turned on", type: "digital")
	}
	targetDevice.sendEvent(name: "level", value: level, descriptionText: "Device ${targetDevice.displayName} set to ${level}%", type: "digital")
}

void startLevelChange(direction, cd = null ){
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
    Integer upDown = (direction == "down" ? 1 : 0)
    sendToDevice(secure(supervise(zwave.switchMultilevelV1.switchMultilevelStartLevelChange(upDown: upDown, ignoreStartLevel: 1, startLevel: 0)), ep))
}

void stopLevelChange(cd = null ){
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	List<hubitat.zwave.Command> cmds = []
		cmds.add(secure(supervise(zwave.switchMultilevelV1.switchMultilevelStopLevelChange()), ep))
		cmds.add(secure(zwave.basicV1.basicGet(), ep))
	sendToDevice(cmds)
}
