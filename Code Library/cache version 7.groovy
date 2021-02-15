import java.util.concurrent.* // Available (white-listed) concurrency classes: ConcurrentHashMap, ConcurrentLinkedQueue, Semaphore, SynchronousQueue
import groovy.transform.Field



metadata {
	definition (name: "[Cache Version 7] Driver Using SynchronousQueue Cached Reports",namespace: "jvm", author: "jvm") {
		capability "Configuration"
		capability "Initialize"
		capability "Refresh"
		capability "Switch"
		capability "SwitchLevel"
		// capability "ChangeLevel"
        capability "Lock"
		
        attribute "buttoonTripleTapped", "number"	
		attribute "buttonFourTaps", "number"	
		attribute "buttonFiveTaps", "number"	         
		attribute "multiTapButton", "number"		

		command "preCacheReports"
		command "getCachedVersionReport"
		command "getCachedNotificationSupportedReport"
		command "getCachedMultiChannelEndPointReport"
		command "logStoredReportCache"
		command "getInputControlsForDevice"
		command "getOpenSmartHouseData"
		command "getParameterValuesFromDevice"
		command "setInputControlParameterValuesToDeviceValue"
		command "getParameterValuesFromInputControls"
    }
	
    preferences 
	{
		input title:"Device Information Lookup", description: "<p> ${state.deviceInformation} </p>", type: "paragraph", element: "Get device information from the OpenSmartHouse database"
		
        input name: "advancedEnable", type: "bool", title: "Enable Advanced Configuration", defaultValue: false
        input name: "showParameterInputs", type: "bool", title: "Show Parameter Value Input Controls", defaultValue: false        
        if (advancedEnable)
        {
			input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
			input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: true
			input name: "superviseEnable", type: "bool", title: "Enable Command Supervision if supported", defaultValue: true
			// The following preferences are only for use while debugging. Remove them from final code
			input name: "remindEnable", type: "bool", title: "Enable Coding To-Do Reminders", defaultValue: false
		}
		if (showParameterInputs)
		{
			ConcurrentHashMap inputs = getInputControlsForDevice()
			List<Integer> keyset = inputs?.keySet().collect{ it as Integer}
			keyset?.sort().each{ input inputs.get(it) }
        }
    }	
}

///////////////////////////////////////////////////////////////////////
//////        Install, Configure, Initialize, and Refresh       ///////
///////////////////////////////////////////////////////////////////////
void clearLeftoverSettings()
{
	// Clean out any old settings names left behind by other driver versions!
	ConcurrentHashMap inputs = getInputControlsForDevice()
	List<String> allSettingNames = ["logEnable", "txtEnable", "superviseEnable", "remindEnable", "showParameterInputs"] + inputs.values().collect{it.name as String } 
	settings.each{k, v -> 
		if (allSettingNames.contains( k as String)) return
		device.removeSetting(k as String) 
		}
}

void installed() { 
	state.clear()
	clearLeftoverSettings()
}

void configure() { 
	if (txtEnable) log.info "Device ${device.displayName}: Executing configure routine."
	if (txtEnable) log.info "Device ${device.displayName}: clearing old state data."
	state.clear()
	clearLeftoverSettings()
	if (txtEnable) log.info "Device ${device.displayName}: Creating child devices (if supported)."
	deleteUnwantedChildDevices()
	createChildDevices()
	initialize() 
	if (txtEnable) log.info "Device ${device.displayName}: Configuration complete."
	
}

void initialize( )
{
	if (txtEnable) log.info "Device ${device.displayName}: Performing startup initialization routine."

	if (txtEnable) log.info "Device ${device.displayName}: Pre-Caching device information."
	preCacheReports()
	if (txtEnable) log.info "Device ${device.displayName}: Getting parameter values from device."
	setInputControlParameterValuesToDeviceValue()
	if (txtEnable) log.info "Device ${device.displayName}: Getting input controls for device."
	getInputControlsForDevice()
	if (txtEnable) log.info "Device ${device.displayName}: Initialization complete."
	
}

void refresh(Map params = [cd: null ])
{
	def targetDevice = (params.cd ? params.cd : device)
	Short ep = params.cd ? (params.cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	
	if (implementsZwaveClass(0x26, ep)) { // Multilevel  type device
		sendToDevice(secure(zwave.switchMultilevelV4.switchMultilevelGet(), ep) )
	} else if (implementsZwaveClass(0x25, ep)) { // Switch Binary Type device
		sendToDevice(secure(zwave.switchBinaryV1.switchBinaryGet(), ep))
	} else { // Basic Set Type device
		log.warn "Using Basic Set to turn on device ${targetDevice.displayName}. A more specific command class should be used!"
		sendToDevice(secure(zwave.basicV2.basicGet(), ep))
	}
	
	meterRefresh( ep )
}

/////////////////////////////////////////////////////////////////////////
//////        Create and Manage Child Devices for Endpoints       ///////
/////////////////////////////////////////////////////////////////////////

void deleteUnwantedChildDevices()
{
	// Delete child devices that don't use the proper network ID form (parent ID, followed by "-ep" followed by endpoint number).
	getChildDevices()?.each
	{ child ->	
	
		List childNetIdComponents = child.deviceNetworkId.split("-ep")
                    
		if (childNetIdComponents.size() != 2) {
			deleteChildDevice(child.deviceNetworkId)			
			
		} else {
            Boolean endPointInRange = ((0 as Short) < (childNetIdComponents[1] as Short)) && ((childNetIdComponents[1] as Short) <= numberOfEndPoints)
			Boolean parentNetIdMatches = (childNetIdComponents[0]  == device.deviceNetworkId)
			
            if (!(parentNetIdMatches && endPointInRange )) {
				deleteChildDevice(child.deviceNetworkId)	
			}
		}
	}
}

void createChildDevices()
{	
	Integer mfr = 	device.getDataValue("manufacturer").toInteger()
	Integer type = 	device.getDataValue("deviceType").toInteger()
	Integer id = 	device.getDataValue("deviceId").toInteger()
	
	hubitat.zwave.Command endPointReport = getCachedMultiChannelEndPointReport()
	if (!endPointReport) return
	
	Short numberOfEndPoints = endPointReport.endPoints
	log.debug "Device ${device.displayName}: has ${numberOfEndPoints} endpoints."
	
	Short thisKid = 1
	for ( thisKid; thisKid <= numberOfEndPoints; thisKid++)
	{
		String childNetworkId = "${device.deviceNetworkId}-ep${"${thisKid}".padLeft(3, "0") }"
		def cd = getChildDevice(childNetworkId)
		if (!cd) {
			log.info "Device ${device.displayName}: creating child device: ${childNetworkId}"
			String childDriver
			
			if (implementsZwaveClass(0x25, thisKid ) ){  // Binary Switch
				if (implementsZwaveClass(0x32, thisKid ) ){ // Meter Supported
					childDriver = "Generic Component Metering Switch"
				} else {
					childDriver = "Generic Component Switch"
				}
			} else  if (implementsZwaveClass(0x26, thisKid ) ){ // MultiLevel Switch
				childDriver = "Generic Component Dimmer"
			}

			addChildDevice("hubitat", childDriver, childNetworkId, [name: "${device.displayName}-ep${thisKid}", isComponent: false])
		} else {
			log.info "Device ${device.displayName}: Child device with network id ${childNetworkId} already exist. No need to re-create."
		}
	}
}

//////////////////////////////////////////////////////////////
//////                  Manage Metering                ///////
//////////////////////////////////////////////////////////////
void meterRefresh ( Short ep = null ) 
{
	if (!implementsZwaveClass(0x32, ep) ) return
	log.debug "Meter types supported are: " + getElectricMeterScalesSupportedMap( ep )
	
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Short) == ep}
	} else { targetDevice = device }

	if (implementsZwaveClass(0x32, ep) < 1)
	{
		log.warn "Called meterRefresh() for a Device ${targetDevice.displayName} that does not support metering. No Meter Refresh performed."
		return
	}

    if (txtEnable) log.info "Refreshing Energy Meter values for device: ${targetDevice.displayName}."
	
	if (implementsZwaveClass(0x32, ep) == 1)
	{
		if (logEnable) log.debug "Performing a Version 1 Meter Get for device ${targetDevice.displayName}."
		sendToDevice(secure(zwave.meterV1.meterGet(), ep))
	} else {
		Map<String, Boolean> metersSupported = getElectricMeterScalesSupportedMap( ep )
		
		if (logEnable) log.debug "Performing a Version 2+ Meter Get for device ${targetDevice.displayName}."
		List<hubitat.zwave.Command> cmds = []
			if (metersSupported.kWh) 			cmds << secure(zwave.meterV5.meterGet(scale: 0), ep)
			if (metersSupported.kVAh) 			cmds << secure(zwave.meterV5.meterGet(scale: 1), ep)
			if (metersSupported.Watts) 			cmds << secure(zwave.meterV5.meterGet(scale: 2), ep)
			if (metersSupported.PulseCount) 	cmds << secure(zwave.meterV5.meterGet(scale: 3), ep)
			if (metersSupported.Volts) 			cmds << secure(zwave.meterV5.meterGet(scale: 4), ep)
			if (metersSupported.Amps) 			cmds << secure(zwave.meterV5.meterGet(scale: 5), ep)
			if (metersSupported.PowerFactor) 	cmds << secure(zwave.meterV5.meterGet(scale: 6), ep)
			if (metersSupported.kVar) 			cmds << secure(zwave.meterV5.meterGet(scale: 7, scale2: 0), ep)
			if (metersSupported.kVarh) 			cmds << secure(zwave.meterV5.meterGet(scale: 7, scale2: 1), ep)
		if (cmds) sendToDevice(cmds)	
	}
}

Map<String, Boolean> getElectricMeterScalesSupportedMap(Short ep = null )
{
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Short) == ep}
	} else { targetDevice = device }	

	hubitat.zwave.Command report = getCachedMeterSupportedReport( ep )
	
	if (! report) return null

    if ((report.meterType as Integer) == 1 )
    {
		Map<String, Boolean> meterMap = [ 
			kWh: 		(( report.scaleSupported & 0b00000001 ) as Boolean ),
			kVAh:		(( report.scaleSupported & 0b00000010 ) as Boolean ),
			Watts:		(( report.scaleSupported & 0b00000100 ) as Boolean ),
			PulseCount:	(( report.scaleSupported & 0b00001000 ) as Boolean ),
			Volts:		(( report.scaleSupported & 0b00010000 ) as Boolean ),
			Amps:		(( report.scaleSupported & 0b00100000 ) as Boolean ),
			PowerFactor:(( report.scaleSupported & 0b01000000 ) as Boolean )
		]
		
        if ( report.hasProperty("moreScaleType") ) {
			meterMap.put("kVar"		, ( report.scaleSupportedBytes[1] & 0b00000001 ) as Boolean)
			meterMap.put("kVarh"	, ( report.scaleSupportedBytes[1] & 0b00000010 ) as Boolean)
        } else {
            meterMap.put("kVar"	, false )
			meterMap.put("kVarh", false )
        }
		return meterMap
    } else  {
		log.warn "Device ${targetDevice.displayName}: Received a meter support type of ${report.meterType} which is not processed by this code."
		return null
	}
}


void zwaveEvent(hubitat.zwave.commands.meterv5.MeterReport cmd, Short ep = null )
{
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Short) == ep}
	} else { targetDevice = device }	
	
	if (logEnable && cmd.hasProperty("rateType") && (cmd.rateType != 1)) log.warn "Device ${targetDevice.displayName}: Unexpected Meter rateType received. Value is: ${cmd.rateType}."
	
	if (cmd.meterType == 1)
	{
		switch (cmd.scale as Integer)
		{
			case 0: // kWh
				targetDevice.sendEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
				if (txtEnable) log.info "${targetDevice.displayName}: Energy report received with value of ${cmd.scaledMeterValue} kWh"
				break
				
			case 1: // kVAh
				if (txtEnable) log.info "Received a meter report with unsupported type: kVAh. This is not a Hubitat Supported meter value."
				break
				
			case 2: // W
				targetDevice.sendEvent(name: "power", value: cmd.scaledMeterValue, unit: "W")
				if (txtEnable) log.info "${targetDevice.displayName}: Power report received with value of ${cmd.scaledMeterValue} W"
				break	
				
			case 3: // Pulse Count
				if (txtEnable) log.info "Received a meter report with unsupported type: Pulse Count. This is not a Hubitat Supported meter value."
			   break
				
			case 4: // V
				targetDevice.sendEvent(name: "voltage", value: cmd.scaledMeterValue, unit: "V")
				if (txtEnable) log.info "${targetDevice.displayName}: Voltage report received with value of ${cmd.scaledMeterValue} V"
				break
				
			case 5: // A
				targetDevice.sendEvent(name: "amperage", value: cmd.scaledMeterValue, unit: "A")
				if (txtEnable) log.info "${targetDevice.displayName}: Amperage report received with value of ${cmd.scaledMeterValue} A"
				break
				
			case 6: // Power Factor
				if (txtEnable) log.info "Device ${targetDevice.displayName}: Received a meter report with unsupported type: Power Factor. This is not a Hubitat Supported meter value."
				break
				
			case 7: // M.S.T. - More Scale Types
				switch (cmd.scale2 as Integer)
				{
					case 0:
						log.warn "Device ${targetDevice.displayName}: Received a meter report with unsupported type: More Scale Types / KVar. Report was: ${cmd}."
					
						break
					case 1:
						log.warn "Device ${targetDevice.displayName}: Received a meter report with unsupported type: More Scale Types / KVarh. Report was: ${cmd}."
					
						break
					default:
						log.warn "Device ${targetDevice.displayName}: Received a meter report with unsupported type: More Scale Types. Report was: ${cmd}."
						break
				} 
			   break
		}

	} else {
		log.warn "Device ${targetDevice.displayName}: Received unexpected meter type for ${targetDevice.displayName}. Only type '1' (Electric Meter) is supported. Received type: ${cmd.meterType}"
	}
}
//////////////////////////////////////////////////////////////////////
//////        Handle  Multilevel Sensor       ///////
//////////////////////////////////////////////////////////////////////


void zwaveEvent(hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelReport cmd, Short ep = null )
{
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Short) == ep}
	} else { targetDevice = device }
	
	log.warn "Device ${device.displayName}: WARNING. MultiLevel Report code is currently incomplete. Sensor Multilevel Report is: " + cmd
	switch (cmd.sensorType as Integer)
	{
		case 1: // temperature
			String tempUnits = [0:"F", 1:"C"].get(cmd.scale as Integer)
			targetDevice.sendEvent(name: "temperature", value: cmd.scaledMeterValue, unit: tempUnits)
			break
		case 3: // Illuminance
			String lightUnits = [0:"%", 1:"Lux"].get(cmd.scale as Integer)
			targetDevice.sendEvent(name: "illuminance", value: cmd.scaledMeterValue, unit: lightUnits)
			break	
		case 4: // Power
			String powerUnits = [0:"Watts", 1:"BTU/h"].get(cmd.scale as Integer)
			targetDevice.sendEvent(name: "power", value: cmd.scaledMeterValue, unit: powerUnits)
			break		
		case 5: // Humidity
			String humidUnits = [0:"%", 1:"g/m3"].get(cmd.scale as Integer)
			targetDevice.sendEvent(name: "humidity", value: cmd.scaledMeterValue, unit: humidUnits)
			break		
		case 15: // voltage
			String voltUnits = [0:"V", 1:"mV"].get(cmd.scale as Integer)
			targetDevice.sendEvent(name: "voltage", value: cmd.scaledMeterValue, unit: humidUnits)
			break
		case 16: // Current
			String currentUnits = [0:"A", 1:"mA"].get(cmd.scale as Integer)
			targetDevice.sendEvent(name: "amperage", value: cmd.scaledMeterValue, unit: currentUnits)
			break				
		default :
			log.warn "Device ${targetDevice.displayName}: Received an unsupported SensorMultilevelReport: ${cmd}."
			break
	}
}

//////////////////////////////////////////////////////////////////////
//////        Handle Notifications        ///////
//////////////////////////////////////////////////////////////////////

void getSupportedNotificationEvents()
{
	List<hubitat.zwave.Command> report = getCachedNotificationSupportedReport(ep)		
	
	if (ep) log.warn "Device ${device.displayName}: Endpoint handling in report type NotificationSupportedReport is incomplete! Alert developer."

	if (logEnable) log.debug "Device ${device.displayName}: Received Notification Supported Report: " + cmd 
	List<hubitat.zwave.Command> cmds=[]
		
	if (report.smoke)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 1)) // Smoke
	if (report.co)				cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 2)) // CO Alarm
	if (report.co2)				cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 3)) // CO2 Alarm
	if (report.heat)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 4)) // Heat Alarm
	if (report.water)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 5)) // Water
	if (report.accessControl) 	cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 6)) // Access Control
	if (report.burglar)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 7)) // Burglar
	if (report.powerManagement)	cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 8)) // Power Management
	if (report.system)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 9)) // System
	if (report.emergency)		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 10)) // Emergency
	if (report.clock)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 11)) // Clock
	if (report.appliance)		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 12)) // Appliance
	if (report.homeHealth)		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 13)) // Home Health
	if (report.siren)			cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 14)) // Siren
	if (report.waterValve)		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 15)) // Water Valve
	if (report.weatherAlarm)	cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 16)) // Weather Alarm
	if (report.irrigation)		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 17)) // Irrigation
	if (report.gasAlarm)		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 18)) // Gas Alarm
	if (report.pestControl)		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 19)) // Pest Control
	if (report.lightSensor)		cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 20)) // Light Sensor
	if (report.waterQuality)	cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 21)) // Water Quality
	if (report.homeMonitoring)	cmds << secure(zwave.notificationV8.eventSupportedGet(notificationType: 22)) // Home Monitoring	

	if (cmds) sendToDevice(cmds)
}

void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd, ep = null)
{
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Short) == ep}
	} else { targetDevice = device }		
	
	List events =
		[ 	1:[ // Smoke
				0:[[name:"smoke" , value:"clear", descriptionText:"Smoke detector status Idle."]], 
				1:[[name:"smoke" , value:"detected", descriptionText:"Smoke detected (location provided)."]], 
				2:[[name:"smoke" , value:"detected", descriptionText:"Smoke detected."]]
				],
			2:[ // CO
				0:[[name:"carbonMonoxide" , value:"clear", descriptionText:"Smoke detector status Idle."]], 
				1:[[name:"carbonMonoxide" , value:"detected", descriptionText:"Smoke detected (location provided)."]], 
				2:[[name:"carbonMonoxide" , value:"detected", descriptionText:"Smoke detected."]]
				],
			5:[ // Water
				0:[[name:"water" , value:"dry", descriptionText:"Water Alarm Notification, Status Dry."]], 
				1:[[name:"water" , value:"wet", descriptionText:"Water leak detected (location provided)."]], 
				2:[[name:"water" , value:"wet", descriptionText:"Water leak detected."]]
				],
			6:[ // Access Control (Locks)
				0:[], 
				1:[[name:"lock" , value:"locked", descriptionText:"Manual lock operation"]], 
				2:[[name:"lock" , value:"unlocked", descriptionText:"Manual unlock operation"]], 
				3:[[name:"lock" , value:"locked", descriptionText:"RF lock operation"]], 
				4:[[name:"lock" , value:"unlocked", descriptionText:"RF unlock operation"]], 
				5:[[name:"lock" , value:"locked", descriptionText:"Keypad lock operation"]], 
				6:[[name:"lock" , value:"unlocked", descriptionText:"Keypad unlock operation"]], 
				11:[[name:"lock" , value:"unknown", descriptionText:"Lock jammed"]], 				
				254:[[name:"lock" , value:"unknown", descriptionText:"Lock in unknown state"]]
				],
			7:[ // Home Security
				0:[[name:"tamper" , value:"clear", descriptionText:"Tamper state cleared."],[name:"motion" , value:"inactive", descriptionText:"Motion Inactive."] ], 
				3:[[name:"tamper" , value:"detected", descriptionText:"Tampering, device cover removed"]], 
				4:[[name:"tamper" , value:"detected", descriptionText:"Tampering, invalid code."]], 
				7:[[name:"motion" , value:"active", descriptionText:"Motion detected (location provided)."]],
				8:[[name:"motion" , value:"active", descriptionText:"Motion detected."]],
				9:[[name:"tamper" , value:"detected", descriptionText:"Tampering, device moved"]]
				],
			14:[ // Siren
				0:[[name:"alarm" , value:"off", descriptionText:"Alarm Siren Off."]], 
				1:[[name:"alarm" , value:"siren", descriptionText:"Alarm Siren On."]]
				], 
			15:[ // Water Valve
				0:[[name:"valve" , value:( (cmd.event ) ? "open" : "closed"), descriptionText:"Valve Operation."]], 
				1:[[name:"valve" , value:( (cmd.event ) ? "open" : "closed"), descriptionText:"Master Valve Operation."]] 
				], 
			22:[ // Presence
				0:[[name:"presence" , value:"not present", descriptionText:"Home not occupied"]], 
				1:[[name:"presence" , value:"present", descriptionText:"Home occupied (location provided)"]],  
				2:[[name:"presence" , value:"present", descriptionText:"Home occupied"]]
				]
				
		].get(cmd.notificationType as Integer)?.get(cmd.event as Integer)
	
	if ( ! events ) { 
		log.warn "Device ${device.displayName}: Received an unhandled notifiation event ${cmd} for endpoint ${ep}." 
	} else { 
		events?.each{ 
			if (targetDevice.hasAttribute(it.name)) { 
				targetDevice.sendEvent(it) 
			} else {
				log.warn "Device ${targetDevice.displayName}: Device missing attribute for notification event ${it}, notification report: ${cmd}."
			}
		}
	}
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
Integer getManufacturerNumber() { device.getDataValue("manufacturer").toInteger() }
Integer getDeviceTypeNumber() { device.getDataValue("deviceType").toInteger() }
Integer getDeviceIdNumber() {device.getDataValue("deviceId").toInteger() }

String getManufacturerHexString() { return hubitat.helper.HexUtils.integerToHexString( getManufacturerNumber(), 2) }
String getDeviceTypeHexString() { return hubitat.helper.HexUtils.integerToHexString( getDeviceTypeNumber(), 2) }
String getDeviceIdHexString() { return hubitat.helper.HexUtils.integerToHexString( getDeviceIdNumber(), 2) }

String productKey() // Generates a key based on manufacturer / device / firmware. Data is shared among all similar end-devices.
{
	if (remindEnable) log.warn "productKey function should be updated with a hash based on inclusters as some devices may remove change their inclusters depending on pairing state. for example, Zooz Zen 18 motion sensor may or may not show with a battery!"
	
	String key = "${getManufacturerHexString()}:${getDeviceTypeHexString()}:${getDeviceIdHexString()}:"
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
		sendToDevice(secure(zwave.versionV1.versionCommandClassGet(requestedCommandClass: requestedCommandClass )))
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
			// if (logEnable) log.debug "Command class ${getCmd.commandClassId} not implemented by this device for endpoint ${ep ?: 0}: " + getCmd
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
		// if (logEnable) log.debug "Device ${device.displayName}: In function getReportCachedByProductId, getting report using command ${getCmd} for endpoint ${ep}."
	} else {
		// if (logEnable) log.debug "Device ${device.displayName}: sending to device a command : ${getCmd} to get report ${reportClass} for subIndex ${subIndex}."
		sendToDevice(secure(getCmd, ep))
		Map transferredData = myReportQueue(reportClass).poll(10, TimeUnit.SECONDS)
		// if (logEnable) log.debug "Device ${device.displayName}: Transferred data for report ${getCmd} is: " + transferredData
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
	log.debug "OpenSmartHouseRecords are: " + OpenSmartHouseRecords
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
	if (transferredReport) { if (logEnable) log.debug "Successfully transferred version report to waiting receiver." }
	else { log.warn "Device ${device.displayName} Failed to transfer report ${cmd}" }
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
		if (report.commandClass.contains(commandClass)) return getCachedVersionCommandClassReport(commandClass)?.commandClassVersion
		} else {
			log.warn "Device ${device.displayName}: called function implementsZwaveClass(commandClass = ${commandClass}, ep = ${ep}). Maximum endpoints supported by this device is: ${numberOfEndPoints ? numberOfEndPoints : 0}" 
			return null
		}
		
	} else  if (deviceClasses.contains(commandClass)) {
		Integer returnClassVersion = getCachedVersionCommandClassReport(commandClass)?.commandClassVersion
		
		if (! returnClassVersion) { 
				log.warn "Device ${device.displayName}: In implementsZwaveClass function, Failed to retrieve a command class version for command class ${commandClass} even though class is supported. Forcing a return of 1."
				returnClassVersion = 1 
			} 
		return returnClassVersion
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
	if (logEnable && previouslyRejected) log.debug "Device ${device.displayName}: Attempted to supervise a class ${cmd.CMD} which was previously rejected as not supervisable."
	return previouslyRejected 
}

void markSupervisionNotSupported(cmd) {	
	supervisionRejected.get(firmwareKey(), new ConcurrentHashMap<String, Boolean>() ).put(cmd.CMD, true )
}

def supervise(hubitat.zwave.Command command)
{
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
		return command
	}
}

// This handles a supervised message (a "get") received from the Z-Wave device //
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, Short ep = null ) {
    if (logEnable) log.debug "Received a SupervisionGet message from ${device.displayName}. The SupervisionGet message is: ${cmd}"

    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(parseMap, defaultParseMap)
	
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand, ep)
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
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	log.warn "setSpeed function not implemented yet!"
	log.debug "Device ${targetDevice.displayName}: called setSpeed. Child device: ${ (cd) ? true : false }"
}

void setPosition(Map params = [position: null , cd: null ], position )
{
	def targetDevice = (cd ? cd : device)
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	log.warn "setPosition function not implemented yet!"
	log.debug "Device ${targetDevice.displayName}: called setPosition. Child device: ${ (cd) ? true : false }"

}

void close( cd = null ) {
	def targetDevice = (cd ? cd : device)
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	log.warn "Device ${targetDevice.displayName}: called close(). Function not implemented."
}

void open( cd = null ) {
	def targetDevice = (cd ? cd : device)
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Short) : null
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
	if (logEnable) log.debug "Device ${device.displayName}: Received a SwitchBinaryReport ${cmd} for endpoint ${ep}."

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
	if (logEnable) log.debug "Device ${device.displayName}: Received a SwitchMultilevelReport ${cmd} for endpoint ${ep}."

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
	Short ep = params.cd ? (params.cd.deviceNetworkId.split("-ep")[-1] as Short) : null
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
	Short ep = params.cd ? (params.cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	
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
/*
//////////////////////////////////////////////////////////////////////
//////      Get Device's Database Information Version          ///////
////////////////////////////////////////////////////////////////////// 
The function getDeviceDataFromDatabase() accesses the Z-Wave device database at www.opensmarthouse.org to
retrieve a database record that contains a detailed description of the device.
Since the database records are firmware-dependent, This function 
should be called AFTER retrieving the device's firmware version using getFirmwareVersionFromDevice().
*/
@Field static ConcurrentHashMap<String, ConcurrentHashMap> OpenSmartHouseRecords = new ConcurrentHashMap<String, ConcurrentHashMap>()

Map getInputControlsForDevice()
{
	ConcurrentHashMap inputControls = OpenSmartHouseRecords.get(firmwareKey(), new ConcurrentHashMap())
	if (inputControls?.size() > 0) 
	{
		if (!state.parameterInputs?.containsKey(firmwareKey()))
		{
			state.remove("parameterInputs")
			state.parameterInputs = [(firmwareKey()):inputControls]
		} else { log.debug "already storing state for inputControls" }
		
	} else if (state.parameterInputs?.containsKey(firmwareKey())) {
		state.parameterInputs.get(firmwareKey()).each{ k, v -> inputControls.put( k as Integer, v) }
		if (logEnable) log.debug "Device ${device.displayName}: Loaded Input Controls from saved state data. Controls are ${inputControls}"
	} else {
		if (logEnable) log.debug "Retrieving input control date from opensmarthouse.org for device ${device.displayName}."
		try {
			List parameterData = getOpenSmartHouseData()
			inputControls = createInputControls(parameterData)
			if (inputControls) state.parameterInputs = [(firmwareKey()):inputControls]
		} catch (ex) {
			log.warn "Device ${device.displayName}: An Error occurred when attempting to get input controls. Error: ${ex}. Stack trace is: ${getStackTrace(ex)}. Exception message is ${getExceptionMessageWithLine(ex)}"
			return null
		}
	}
	return inputControls
}

// List getOpenSmartHouseData()
List getOpenSmartHouseData()
{
	if (txtEnable) log.info "Getting data from OpenSmartHouse for device ${device.displayName}."
	String manufacturer = 	getManufacturerHexString()
	String deviceType = 	getDeviceTypeHexString()
	String deviceID = 		getDeviceIdHexString()

    String DeviceInfoURI = "http://www.opensmarthouse.org/dmxConnect/api/zwavedatabase/device/list.php?filter=manufacturer:0x${manufacturer}%20${deviceType}:${deviceID}"

    Map thisDeviceData
    
	hubitat.zwave.Command   versionReport = getCachedVersionReport() 
	if (!versionReport) { return null }
	
	Float deviceFWVersion = (versionReport.firmware0Version as Integer) + ((versionReport.firmware0SubVersion as Float) / 1000)
	
    httpGet([uri:DeviceInfoURI])
    { 
		resp ->
			thisDeviceData = resp.data.devices.find 
			{ element ->
	 
				Minimum_Version = element.version_min.split("\\.")
				Maximum_Version = element.version_max.split("\\.")
				
				Float minimumSupported = Minimum_Version[0].toFloat() + (Minimum_Version[1].toFloat() / 1000)
				Float maximumSupported = Maximum_Version[0].toFloat() + (Maximum_Version[1].toFloat() / 1000)
    
				log.debug "Device firmware version is ${deviceFWVersion}. Record firmware min version is ${minimumSupported}.  Record firmware max version is ${maximumSupported}."

				Boolean isCorrectRecord = (deviceFWVersion >= minimumSupported) && (deviceFWVersion <= maximumSupported)
			
				return isCorrectRecord
			}
	}
    if (! thisDeviceData?.id) 
	{
		log.warn "Device ${device.displayName}: No database entry found for manufacturer: ${manufacturer}, deviceType: ${deviceType}, deviceID: ${deviceID}"
		return null
	}
    state.deviceInformation = "<a href=\"https://www.opensmarthouse.org/zwavedatabase/${thisDeviceData?.id}/\" target=\"_blank\" >Click Here to get your Device Info.</a>"
    String queryByDatabaseID= "http://www.opensmarthouse.org/dmxConnect/api/zwavedatabase/device/read.php?device_id=${thisDeviceData.id}"    
    
	httpGet([uri:queryByDatabaseID]) { resp-> 
				return resp?.data?.parameters
			}
}

Map createInputControls(data)
{
	if (!data) return null
	if (logEnable) log.debug "Device ${device.displayName}: Creating Input Controls"
	
	Map inputControls = [:]	
	data.each
	{
		if (it.read_only as Integer)
			{
				log.info "Device ${device.displayName}: Parameter ${it.param_id}-${it.label} is read-only. No input control created."
				return
			}
	
		if (it.bitmask.toInteger())
		{
			if (!(inputControls?.get(it.param_id)))
			{
				log.warn "Device ${device.displayName}: Parameter ${it.param_id} is a bitmap field. This is poorly supported. Treating as an integer - rely on your user manual for proper values!"
				Map newInput = [name: "configParam${"${it.param_id}".padLeft(3, "0")}", type:"number", title: "(${it.param_id}) ${it.label} - bitmap", size:it.size]
				if ((it.description.size() != 0) && (it.description != it.label)) newInput.description = it.description
				
				inputControls.put(it.param_id, newInput)
			}
		} else {
			Map newInput = [name: "configParam${"${it.param_id}".padLeft(3, "0")}", title: "(${it.param_id}) ${it.label}", size:it.size]
			if ((it.description.size() != 0) && (it.description != it.label)) newInput.description = it.description

			def deviceOptions = [:]
			it.options.each { deviceOptions.put(it.value, it.label) }
			
			// Set input type. Should be one of: bool, date, decimal, email, enum, number, password, time, text. See: https://docs.hubitat.com/index.php?title=Device_Preferences
			
			// use enum but only if it covers all of the choices!
			Integer numberOfValues = (it.maximum - it.minimum) +1
			if (deviceOptions && (deviceOptions.size() == numberOfValues) )
			{
				newInput.type = "enum"
				newInput.options = deviceOptions
			} else {
				newInput.type = "number"
				newInput.range = "${it.minimum}..${it.maximum}"
			}
			inputControls[it.param_id] = newInput
		}
	}
	return inputControls
}
////////////////////////////////////////////////////////////////////////
/////////////      Parameter Updating and Management      /////////////
////////////////////////////////////////////////////////////////////////

@Field static  ConcurrentHashMap<String, ConcurrentHashMap> allPendingParameterChanges = new ConcurrentHashMap<String, ConcurrentHashMap>()
@Field static  ConcurrentHashMap<String, ConcurrentHashMap> allParameterReports = new ConcurrentHashMap<String, ConcurrentHashMap>()

ConcurrentHashMap<Short, BigInteger> getPendingChangeMap()
{
	return allPendingParameterChanges.get(device.deviceNetworkId, new ConcurrentHashMap<Short, BigInteger>())
}

Map<Short, BigInteger> getParameterValuesFromDevice(Map options = [useCache: true ])
{
	Map<Short,hubitat.zwave.Command> parameterReports = allParameterReports.get(device.deviceNetworkId, new ConcurrentHashMap<Short, hubitat.zwave.Command>())
	
	ConcurrentHashMap inputs = getInputControlsForDevice()	
			
	Map<Short, BigInteger> parameterValues = [:]
	
	// Check if anything is missing from the cache!
	Boolean haveAllConfigurationReports = true
	if ((parameterReports.size() as Integer) != (inputs.size() as Integer)) {
			haveAllConfigurationReports = false
		}
		
	if (haveAllConfigurationReports && options.useCache && (parameterReports.size() > 0) ) 
	{
		parameterReports.each{ key, report ->
			parameterValues.put(key as Short, report.scaledConfigurationValue as BigInteger)
			}
	} else {
		hubitat.zwave.Command report = null
		inputs.each 
			{ k, v ->
				sendToDevice(secure(zwave.configurationV1.configurationGet(parameterNumber: k as Short)))
				report = myReportQueue("7006").poll(10, TimeUnit.SECONDS)
				if (! report) return
				// Single-byte return values > 127 get turned into negative numbers when using scaledConfigurationValue, so don't use cmd.scaledConfiguraiton if cmd.size == 1!
				BigInteger newValue = (report.size == 1) ? report.configurationValue[0] : report.scaledConfigurationValue			
				if (newValue < 0) log.warn "Device ${device.displayName}: Negative configuration value reported for configuration paramater ${k}."
				
				if (report) parameterValues.put(report.parameterNumber, newValue )
			}
	}
	if (logEnable) log.debug "Device ${device.displayName}: Map of Parameter Values reported by device is: ${ parameterValues}."
	return parameterValues
}

void setInputControlParameterValuesToDeviceValue()
{
	Map<Short, BigInteger> parameterValues =  getParameterValuesFromDevice(useCache: true )
	ConcurrentHashMap<Short, BigInteger> pendingChanges = getPendingChangeMap()
	log.debug "setting values of input controls"
	parameterValues.each{ key, value ->
		String configName = "configParam${"${key}".padLeft(3,"0")}"
		if (logEnable) log.debug "Device ${device.displayName}: updating settings data for ${configName} to new value ${value}!"
		device.updateSetting("${configName}", value as Integer)
		pendingChanges.remove(key as Short)
	}
}
void zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport  cmd)
{ 
	parameterReports = allParameterReports.get(device.deviceNetworkId, new ConcurrentHashMap<Short, hubitat.zwave.Command>())
	parameterReports.put(cmd.parameterNumber, cmd)
	Boolean transferredReport = myReportQueue(cmd.CMD).offer(cmd)
	if (transferredReport) { if (logEnable) log.debug "Successfully transferred Configuration Report to waiting receiver."}
	else log.warn "Device ${device.displayName}: Failed to transfer Configuration Report."
}

///////////////////////////////////////////////////////////////////////////////////////
///////      Handle Update(), and Set, Get, and Process Parameter Values       ////////
/////////////////////////////////////////////////////////////////////////////////////// 
Map<Short, BigInteger> getParameterValuesFromInputControls()
{
	ConcurrentHashMap inputs = getInputControlsForDevice()
	Map<Short, BigInteger> settingValues = [:]
	
	inputs.each { PKey , PData -> 
	log.debug "Input is: ${PKey}:${PData}."
	log.debug "settings are: " + settings
	
			BigInteger newValue = 0
			log.debug "settings.get(PData.name as String) is ${settings.get(PData.name as String)}."
			// if the setting returns an array, then it is a bitmap control, and add together the values.
			if (settings.get(PData.name as String) instanceof ArrayList) {
				def test = settings.get(PData.name as String)
				log.debug "ArrayList Variable ${PData.name} is of class ${test.class} and contents ${test}."
				settings.get(PData.name as String).each{ newValue += it as BigInteger }
			} else  {   
				def test = settings.get(PData.name as String)
				log.debug "Variable ${PData.name} is of class ${test.class}"
				newValue = settings.get(PData.name as String) as BigInteger  
			}
			settingValues.put(PKey as Short, newValue)
		}
	if (logEnable) log.debug "Current Setting Values are: " + settingValues
	return settingValues
}

void logsOff(){
    log.warn "Device ${device.displayName}: debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void updated()
{
	if (txtEnable) log.info "Device ${device.displayName}: Updating changed parameters (if any) . . ."
	if (logEnable) runIn(1800,logsOff)
	
	ConcurrentHashMap<Short, BigInteger> parameterValueMap = getParameterValuesFromDevice()
	ConcurrentHashMap<Short, BigInteger> pendingChanges = getPendingChangeMap()
	Map<Short, BigInteger>  settingValueMap = getParameterValuesFromInputControls()
		
	// Find what changed
	settingValueMap.each {k, v ->
			if (v.is( null )) return
		
			Boolean changedValue = (v as BigInteger) != (parameterValueMap.get(k as Short) as BigInteger)
			if (changedValue) pendingChanges.put(k as Short, v as BigInteger)
			else pendingChanges.remove(k as Short)
		}

	if (logEnable) log.debug "Device ${device.displayName}: Pending changes are: ${pendingChanges}"
	
	state.pendingChanges = pendingChanges
	processPendingChanges()
	state.pendingChanges = pendingChanges
}

void processPendingChanges()
{
	getPendingChangeMap()?.each{ k, v ->
		setParameter(parameterNumber: k , value: v)
	}
}

void setParameter(Map params = [parameterNumber: null , value: null ] ){
    if (params.parameterNumber.is( null ) || params.value.is( null ) ) {
		log.warn "Device ${device.displayName}: Can't set parameter ${parameterNumber}, Incomplete parameter list supplied... syntax: setParameter(parameterNumber,size,value), received: setParameter(${parameterNumber}, ${size}, ${value})."
    } else {
		parameterReports = allParameterReports.get(device.deviceNetworkId, new ConcurrentHashMap<Short, hubitat.zwave.Command>())

		Short PSize = parameterReports.get(params.parameterNumber as Short).size

		List<hubitat.zwave.Command> cmds = []
	    cmds << secure(supervise(zwave.configurationV1.configurationSet(scaledConfigurationValue: params.value as BigInteger, parameterNumber: params.parameterNumber as Short, size: PSize)))
	    cmds << secure(zwave.configurationV1.configurationGet(parameterNumber: params.parameterNumber))
		sendToDevice(cmds)
		hubitat.zwave.Command report = myReportQueue("7006").poll(10, TimeUnit.SECONDS)
		if (report)
		{
			if ((report.scaledConfigurationValue) == (params.value as BigInteger)) {
				log.info "Device ${device.displayName}: Successfully set parameter #: ${params.parameterNumber} to value ${params.value}."
			} else {
				log.warn "Device ${device.displayName}: Failed to set parameter #: ${params.parameterNumber} to value ${params.value}. Value of parameter is set to ${report.scaledConfigurationValue} instead."
			}
		}
    }
}

//////////////////////////////////////////////////////////////////////
//////        Locks        ///////
//////////////////////////////////////////////////////////////////////

void lockInitialize()
{
	sendToDevice (secure( zwave.userCodeV1.usersNumberGet() ))
}

void zwaveEvent(hubitat.zwave.commands.usercodev1.UserCodeReport cmd) { 
	log.warn "Lock code is still under development. Functions may not be fully implemented."
	if (logEnable) log.debug "For device ${device.displayName}, received User Code Report: " + cmd
}

void zwaveEvent(hubitat.zwave.commands.usercodev1.UsersNumberReport cmd) { 
	log.warn "Lock code is still under development. Functions may not be fully implemented."

	if (logEnable) log.debug "For device ${device.displayName}, received Users Number Report: " + cmd
	sendEvent(name:"maxCodes", value: cmd.supportedUsers)
}

void lockrefresh()
{
	sendToDevice (secure(zwave.doorLockV1.doorLockOperationGet()))
}
void lock()
{
	List<hubitat.zwave.Command> cmds=[]
	cmds << secure( zwave.doorLockV1.doorLockOperationSet(doorLockMode: 0xFF) )
	cmds << "delay 4500"
	cmds << secure( zwave.doorLockV1.doorLockOperationGet() )
	sendToDevice(cmds)
}

void unlock()
{
	List<hubitat.zwave.Command> cmds=[]
	cmds << secure( zwave.doorLockV1.doorLockOperationSet(doorLockMode: 0x00) )
	cmds << "delay 4500"
	cmds << secure( zwave.doorLockV1.doorLockOperationGet() )
	sendToDevice(cmds)
}

void deleteCode(codeposition)
{
    if (logEnable) log.debug "Device ${device.displayName}:  deleting code at position ${codeNumber}."
	// userIDStatus of 0 corresponds to Z-Wave  "Available (not set)" status.
	sendToDevice (secure( zwave.userCodeV1.userCodeSet(userIdentifier:codeNumber, userIdStatus:0) ))
	sendToDevice (secure( zwave.userCodeV1.userCodeGet(userIdentifier:codeNumber) ))
}

void getCodes()
{
	log.warn "Device ${device.displayName}: Lock code is still under development. getCodes function is not be fully implemented."

	List<hubitat.zwave.Command> cmds=[]
		cmds << secure(zwave.userCodeV1.usersNumberGet())
		
		// need to first get the usersNumber and then get the code for each of those users!
		cmds << secure(zwave.userCodeV1.userCodeGet(userIdentifier: 1))
	sendToDevice(cmds)
}

void setCode(codeposition, pincode, name)
{
	log.warn "Device ${device.displayName}: Lock code is still under development. setCode function does not check for code length. You must ensure you use a permitted length!."

	String userCode = pincode as String
	if (logEnable) log.debug "Device ${device.displayName}: setting code at position ${codeposition} to ${pincode}."
	assert (userCode instanceof String) 

	List<hubitat.zwave.Command> cmds=[]
	cmds << secure( zwave.userCodeV1.userCodeSet(userIdentifier:codeNumber, userIdStatus:0) )
	cmds << secure(zwave.userCodeV1.userCodeSet(userIdentifier:codeposition, userIdStatus:1, userCode:userCode ))
	cmds << secure(zwave.userCodeV1.userCodeGet(userIdentifier:codeposition))
	sendToDevice(cmds)
}

void setCodeLength(pincodelength)
{
	log.warn "Device ${device.displayName}: Code Length command not supported. If your device supports code length settings, you may be able to set the code length using Z-Wave Parameter Settings controls."
}

// This is another form of door lock reporting. I believe its obsolete, but I've included it just in case some lock requires it.  
// Modes 2-4 are not implemented by Hubitat.
void zwaveEvent(hubitat.zwave.commands.doorlockv1.DoorLockOperationReport cmd) 
{
	if (logEnable) log.debug "Received Door Lock Operation Report: " + cmd  
	
	Map lockEvent =
		[
			0:[name:"lock" , value:"unlocked", descriptionText:"Door unsecured"], 
			1:[name:"lock" , value:"unlocked with timeout", descriptionText:"Door Unsecured with timeout"], 
			16:[name:"lock" , value:"unlocked", descriptionText:"Door Unsecured for inside Door Handles"], 
			17:[name:"lock" , value:"unlocked with timeout", descriptionText:"Door Unsecured for inside Door Handles with timeout"], 
			32:[name:"lock" , value:"unlocked", descriptionText:"Door Unsecured for outside Door Handles"], 
			33:[name:"lock" , value:"unlocked with timeout", descriptionText:"Door Unsecured for outside Door Handles with timeout"],
			254:[name:"lock" , value:"unknown", descriptionText:"Lock in unknown state"],
			255:[name:"lock" , value:"locked", descriptionText:"Door Secured"]
		].get(cmd.doorLockMode as Integer)

	if (lockEvent) sendEvent(lockEvent)
}
