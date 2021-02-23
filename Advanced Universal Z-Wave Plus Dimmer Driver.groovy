import java.util.concurrent.* // Available (white-listed) concurrency classes: ConcurrentHashMap, ConcurrentLinkedQueue, Semaphore, SynchronousQueue
import groovy.transform.Field

metadata {
	definition (name: "[Beta 0.1.0] Advanced Just About Anything Zwave Plus Dimmer Driver",namespace: "jvm", author: "jvm") {
		// capability "Configuration"
		capability "Initialize"
		capability "Refresh"
		capability "Switch"
		capability "SwitchLevel"
		capability "ChangeLevel"
		// capability "WindowShade"
		
		capability "PowerMeter"
		capability "VoltageMeasurement"
		// capability "Battery"
		
		capability "PushableButton"
		command "push", ["NUMBER"]	
		capability "HoldableButton"
		command "hold", ["NUMBER"]
		capability "ReleasableButton"
		command "release", ["NUMBER"]
		capability "DoubleTapableButton"
		command "doubleTap", ["NUMBER"]
        command "multiTap", [[name:"button",type:"NUMBER", description:"Button Number", constraints:["NUMBER"]],
					[name:"taps",type:"NUMBER", description:"Tap count", constraints:["NUMBER"]]]

        // attribute "buttonTripleTapped", "number"	
		// attribute "buttonFourTaps", "number"	
		// attribute "buttonFiveTaps", "number"	         
		attribute "multiTapButton", "number"	
		
		command "setParameter",[[name:"parameterNumber",type:"NUMBER", description:"Parameter Number", constraints:["NUMBER"]],
					[name:"value",type:"NUMBER", description:"Parameter Value", constraints:["NUMBER"]]
					]	
		
		command "resetState"
        
        // Following commands are for debugging purposes
		// command "indicatorSupportedGet", [[name:"indicatorId",type:"NUMBER", description:"indicatorId Number", constraints:["NUMBER"]]]	
		// command "preCacheReports"
		// command "getCachedVersionReport"
		// command "getCachedNotificationSupportedReport"
		// command "getCachedMultiChannelEndPointReport"
		// command "logStoredReportCache"
		// command "getInputControlsForDevice"
		// command "getOpenSmartHouseData"
		// command "getParameterValuesFromDevice"
		// command "setInputControlsToDeviceValue"
		// command "getParameterValuesFromInputControls"
		// command "clearLeftoverDeviceData"
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
			// input name: "remindEnable", type: "bool", title: "Enable Coding To-Do Reminders", defaultValue: false
		}
		if (showParameterInputs)
		{
			setInputControlsToDeviceValue()
			ConcurrentHashMap inputs = getInputControlsForDevice()
			List<Integer> keyset = inputs?.keySet().collect{ it as Integer}
			keyset?.sort().each{ input inputs.get(it) }
        }
    }	
}

///////////////////////////////////////////////////////////////////////
//////        Install, Configure, Initialize, and Refresh       ///////
///////////////////////////////////////////////////////////////////////
void resetState()
{
	state.clear()
}

void clearLeftoverSettings()
{
	// Clean out any old settings names left behind by other driver versions!
	ConcurrentHashMap inputs = getInputControlsForDevice()
	if (!inputs) return 
	
	List<String> allSettingNames = ["logEnable", "txtEnable", "superviseEnable", "remindEnable", "showParameterInputs"] + inputs.values().collect{it.name as String } 
	settings?.each{k, v -> 
		if (allSettingNames.contains( k as String)) return
		device.removeSetting(k as String) 
		}
}

void clearLeftoverStates()
{
	// Clean out any old state variables left behind by other driver versions!
	List<String> allowedStateVariables = ["pendingChanges", "parameterInputs", "deviceInformation"] 
	
	// Can't modify state from within state.each{}, so first collect what is unwanted, then remove in a separate unwanted.each
	List<String> unwanted = state.collect{ 
			if (allowedStateVariables.contains( it.key as String)) return
			return it.key
		}
	unwanted.each{state.remove( it ) } 
}

void clearLeftoverDeviceData()
{
    List<String> deleteOldJunk = ["storedFirmwareReport", "storedEndpointReport", "zwaveAssociationG1", "protocolVersion", "hardwareVersion", "firmwareVersion", "associationGroup1", "associationGroup2", "associationGroup3", "associationGroup4", "associationGroup5", "associationGroup6", "associationGroup7", "MSR"]
    deleteOldJunk.each{device.removeDataValue( it) }
/*
	// Clean out any old state variables left behind by other driver versions!
	List<String> allowedDataVariables = ["deviceId", "deviceType", "manufacturer", "inClusters", "zwNodeInfo", "secureInClusters"] 
	
	// Can't modify data from within device.each{}, so first collect what is unwanted, then remove in a separate unwanted.each
	List<String> unwanted = device.collect{ 
			if (allowedDataVariables.contains( it.key as String)) return
			return it.key
		}
	unwanted.each{device.removeDataValue( it ) } 
	*/
}

void installed() { 
    
	clearLeftoverStates()
	clearLeftoverSettings()
}

void configure() { 
	if (txtEnable) log.info "Device ${device.displayName}: Executing configure routine."
	
	initialize() 
    
	if (txtEnable) log.info "Device ${device.displayName}: Configuration complete."
}

synchronized void initialize( )
{
	if (txtEnable) log.info "Device ${device.displayName}: Performing startup initialization routine."
    
	if (txtEnable) log.info "Device ${device.displayName}: clearing old device. data."    
        clearLeftoverDeviceData()
		
	if (txtEnable) log.info "Device ${device.displayName}: clearing old state data."
	    clearLeftoverStates()
		
	if (txtEnable) log.info "Device ${device.displayName}: clearing old settings data."
	
	    clearLeftoverSettings()
		
	if (txtEnable) log.info "Device ${device.displayName}: Pre-Caching device information."
	preCacheReports()
	
	if (txtEnable) log.info "Device ${device.displayName}: Configuring child devices (if needed)."
	    deleteUnwantedChildDevices()
	    createChildDevices()
    
	if (txtEnable) log.info "Device ${device.displayName}: Getting input controls for device."
		getInputControlsForDevice()
    
	// if (txtEnable) log.info "Device ${device.displayName}: Getting parameter values from device."
	// setInputControlsToDeviceValue()
	
	if (txtEnable) log.info "Device ${device.displayName}: Refreshing device data."
	    refresh()    
    
	if (txtEnable) log.info "Device ${device.displayName}: Initialization complete."

}

void refresh()
{
	refreshEndpoint(ep: null )
	
	hubitat.zwave.Command endPointReport = getCachedMultiChannelEndPointReport()

	for (Short thisEndpoint = 1; thisEndpoint < endPointReport?.endPoints; thisEndpoint++) {
			refreshEndpoint(ep:thisEndpoint)
		}
}

void refreshEndpoint(Map params = [cd: null, ep: null ])
{
	com.hubitat.app.DeviceWrapper targetDevice = (params.cd ?: device)
	if (txtEnable) log.info "Device ${targetDevice.displayName}: refresing data values"
	
	Short ep = null 
	if (params.ep) ep = params.ep as Short
	if (params.cd) ep = params.cd ? (params.cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	
	if (implementsZwaveClass(0x25, ep)) { // Switch Binary Type device
		sendToDevice(zwave.switchBinaryV1.switchBinaryGet(), ep)
	}	
	if (implementsZwaveClass(0x26, ep)) { // Multilevel  type device
		sendToDevice(zwave.switchMultilevelV4.switchMultilevelGet(), ep)
	} 
 	if (implementsZwaveClass(0x62, ep)) {
			sendToDevice(zwave.doorLockV1.doorLockOperationGet() )
	} 
	if (implementsZwaveClass(0x32, ep)) {
		meterRefresh( ep )
	} 
}

//////////////////////////////////////////////////////////////
//////                  Manage Metering                ///////
//////////////////////////////////////////////////////////////
void meterRefresh ( Short ep = null ) 
{
	if (!implementsZwaveClass(0x32, ep) ) return
	if (logEnable) log.debug "Meter types supported are: " + getElectricMeterScalesSupportedMap( ep )
	
	com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)

	if (implementsZwaveClass(0x32, ep) < 1) {
		log.warn "Called meterRefresh() for a Device ${targetDevice.displayName} that does not support metering. No Meter Refresh performed."
		return
	}

    if (txtEnable) log.info "Refreshing Energy Meter values for device: ${targetDevice.displayName}."
	
	if (implementsZwaveClass(0x32, ep) == 1)
	{
		sendToDevice(zwave.meterV1.meterGet(), ep)
	} else {
		Map<String, Boolean> metersSupported = getElectricMeterScalesSupportedMap( ep )
		
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
	com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)

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
	com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)
	
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
	com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)
	
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
			targetDevice.sendEvent(name: "voltage", value: cmd.scaledMeterValue, unit: voltUnits)
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
//////        Handle Indications        ///////
//////////////////////////////////////////////////////////////////////

void indicatorSupportedGet(Short indicatorId)
{
	List<hubitat.zwave.Command> cmds=[]
	
	cmds << secure(zwave.indicatorV3.indicatorSupportedGet(indicatorId: indicatorId)) // Home Monitoring	

	if (cmds) sendToDevice(cmds)
}

def zwaveEvent(hubitat.zwave.commands.indicatorv3.IndicatorSupportedReport cmd, Short ep = null ) {
	if (logEnable) log.debug "Device ${device.displayName}: Indicator Supported Report is ${cmd}"
}

//////////////////////////////////////////////////////////////////////
//////        Handle Notifications        ///////
//////////////////////////////////////////////////////////////////////

void getSupportedNotificationEvents(Short ep = null )
{
	if (ep) log.warn "Device ${device.displayName}: Endpoint handling in report type NotificationSupportedReport is incomplete! Alert developer."

	List<hubitat.zwave.Command> report = getCachedNotificationSupportedReport(ep)		

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

void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd, Short ep = null )
{
	com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)
	
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
				0:[[name:"valve" , value:( (cmd.event > 0 ) ? "open" : "closed"), descriptionText:"Valve Operation."]], 
				1:[[name:"valve" , value:( (cmd.event > 0 ) ? "open" : "closed"), descriptionText:"Master Valve Operation."]] 
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
//////        Hubitat Event Handling Helper Functions        ///////
//////////////////////////////////////////////////////////////////////

////    Hubitat Event Message Sending on Event Stream (not Z-Wave!)  ////
void sendEventToAll(Map event)
{
	if (device.hasAttribute(event.name as String)) sendEvent(event)

	getChildDevices()?.each{ child ->
			if (child.hasAttribute(event.name as String)) child.sendEvent(event)
		}
}

////////////////////////////////////////////////////////
//////                Handle Fans                ///////
////////////////////////////////////////////////////////

String levelToSpeed(Integer level)
{
// 	Map speeds = [(0..0):"off", (1..20):"low", (21..40):"medium-low", (41-60):"medium", (61..80):"medium-high", (81..100):"high"]
//	return (speeds.find{ key, value -> key.contains(level) }).value
	switch (level)
	{
	case 0 : 		return "off" ; break
	case 1..20: 	return "low" ; break
	case 21..40: 	return "medium-low" ; break
	case 41..60: 	return "medium" ; break
	case 61..80: 	return "medium-high" ; break
	case 81..100: 	return "high" ; break
	default : return null
	}
}

Integer speedToLevel(String speed) {
	return ["off": 0, "low":20, "medium-low":40, "medium":60, "medium-high":80, "high":100].get(speed)
}

void setSpeed(speed, com.hubitat.app.DeviceWrapper cd = null ) { setSpeed(speed:speed, cd:cd) }
void setSpeed(Map params = [speed: null , level: null , cd: null ])
{
	com.hubitat.app.DeviceWrapper targetDevice = params.cd ?: device
	Short ep = params.cd ? (targetDevice.deviceNetworkId.split("-ep")[-1] as Short) : null
	
	if (params.speed.is( null ) ) {
		log.error "Device ${targetDevice.deviceName}: setSpeed command received without a valid speed setting. Speed setting was ${params.speed}. Returning without doing anything!"
		return
	}
	
    if (logEnable) log.info "Device ${device.displayName}: received setSpeed(${params.speed}) request from child ${targetDevice.displayName}"

	String currentOnState = targetDevice.currentValue("switch")
	Integer currentLevel = targetDevice.currentValue("level") // Null if attribute isn't supported.
	Integer targetLevel
	
	if (params.speed == "on")
	{
		if (currentOnState == "on") return // If already on, and receive on, do nothing!

		currentLevel = currentLevel ?: 100 // If it was a a level of 0, turn to 100%. Level should never be 0 -- except it might be 0 or null on first startup!
		targetDevice.sendEvent(name: "switch", value: "on", descriptionText: "Fan turned on", type: "digital")

		targetDevice.sendEvent(name: "level", value: currentLevel, descriptionText: "Fan level set", unit:"%", type: "digital")
				
		targetDevice.sendEvent(name: "speed", value: levelToSpeed(currentLevel), descriptionText: "Fan speed set", type: "digital")
		
		sendZwaveValue(value: currentLevel, duration: 0, ep: ep)

	} else if (params.speed == "off")
	{ 
		if (currentOnState == "off") return // if already off, and receive off, do nothing.
		
		targetDevice.sendEvent(name: "switch", value: "off", descriptionText: "Fan switched off", type: "digital")

		targetDevice.sendEvent(name: "speed", value: "off", descriptionText: "Fan speed set", type: "digital")	 
		
		sendZwaveValue(value: 0, duration: 0, ep: ep)
		
	} else {
		targetLevel = (params.level as Integer) ?: speedToLevel(params.speed) ?: currentLevel

		targetDevice.sendEvent(name: "switch", value: "on", descriptionText: "Fan turned on", type: "digital")

		targetDevice.sendEvent(name: "speed", value: params.speed, descriptionText: "Fan speed set", type: "digital")

		targetDevice.sendEvent(name: "level", value: targetLevel, descriptionText: "Fan level set", unit:"%", type: "digital")
		
		sendZwaveValue(value: targetLevel, duration: 0, ep: ep)
	}
	

}

// void setPosition(position) { setPosition(position:position, cd: null )}
void setPosition(Map params = [position: null , cd: null ], position)
{
	com.hubitat.app.DeviceWrapper targetDevice = (params.cd ?: cd ?: device)
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	
	Integer newPosition = Math.min(Math.max((params.position ?: position) as Integer, 0), 99) as Integer
	if (txtEnable) log.info "Device ${targetDevice.displayName}: Setting Position to ${newPosition}."
	
	if (newPosition == 0) 	{ close(cd:params.cd)}
	else { open(cd:params.cd, position:newPosition)}
}


void close(Map params = [cd: null ]) {
	// calling 'open' with a position = 0 causes a close event to occur!
	open(cd:params.cd, position:0) 
}

void open(Map params = [cd: null , position: 100 ])
{
	com.hubitat.app.DeviceWrapper targetDevice = (params.cd ?: device)
	Short ep = params.cd ? (params.cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	
	Integer newPosition = Math.min(Math.max((params.position as Integer), 0), 100)
	Integer currentPosition = targetDevice.getDataValue("position") as Integer
	
	if (logEnable) log.debug "In open / close functions, there should be both a opening before the open or partially-open event, and a closing before a close event. Currently, only open, close, or partially-open is sent."
	
	switch (newPosition)
	{
		case 0:		shadePosition = "closed"; break
		case 1..99:	shadePosition = "partially open"; break
		case 100:	shadePosition = "open"; break
		default : 	shadePosition = "unknown"; break
	}

	targetDevice.sendEvent(name: "windowShade", value: shadePosition, descriptionText: "Window Shade state", type: "digital")
	targetDevice.sendEvent(name: "position", value: newPosition, descriptionText: "Window Shade position", unit:"%", type: "digital")
	sendZwaveValue(value: newPosition, duration: 0, ep: ep)	
}

//////////////////////////////////////////////////////////////////////
//////        Handle Battery Reports and Device Functions        ///////
////////////////////////////////////////////////////////////////////// 
void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd, Short ep = null ) 
{
	com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)
	
	if (cmd.batteryLevel == 0xFF) {
		targetDevice.sendEvent ( name: "battery", value:1, unit: "%", descriptionText: "Low Battery Alert. Change now!")
	} else {
		targetDevice.sendEvent ( name: "battery", value:cmd.batteryLevel, unit: "%", descriptionText: "Battery level report.")
	}
}

void batteryGet() {
	sendToDevice(zwave.batteryV1.batteryGet())
}
//////////////////////////////////////////////////////////////////////
//////        Handle Basic Reports and Device Functions        ///////
////////////////////////////////////////////////////////////////////// 
com.hubitat.app.DeviceWrapper getTargetDeviceByEndPoint(Short ep = null )
{
	if (ep) { return getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Short) == (ep as Short)}
	} else { return device }
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv2.SwitchBinaryReport cmd, Short ep = null)
{
	com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)

	if (! targetDevice.hasAttribute("switch")) log.error "Device ${targetDevice.displayName}: received a Switch Binary Report for a device that does not have a switch attribute."
	
	String priorSwitchState = targetDevice.currentValue("switch")
	String newSwitchState = ((cmd.value > 0) ? "on" : "off")
	
    if (priorSwitchState != newSwitchState) // Only send the state report if there is a change in switch state!
	{
		targetDevice.sendEvent(	name: "switch", value: newSwitchState, descriptionText: "Switch set", type: "physical")
		if (txtEnable) log.info "Device ${targetDevice.displayName} set to ${newSwitchState}."
	}
}

void zwaveEvent(hubitat.zwave.commands.switchmultilevelv4.SwitchMultilevelReport cmd, Short ep = null)
{
	com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)

	if (targetDevice.hasAttribute("position")) 
	{ 
		targetDevice.sendEvent( name: "position", value: (cmd.value == 99 ? 100 : cmd.value) , unit: "%", descriptionText: "Position set", type: "physical" )
	}
	if (targetDevice.hasAttribute("windowShade"))
	{
		String positionDescription
		switch (cmd.value as Integer)
		{
			case 0:  positionDescription = "closed" ; break
			case 99:  positionDescription = "open" ; break
			default : positionDescription = "partially open" ; break
		}
		targetDevice.sendEvent( name: "windowShade", value: positionDescription, descriptionText: "Window Shade position set.", type: "physical" )	
	}

	if (targetDevice.hasAttribute("level") || targetDevice.hasAttribute("switch") ) // Switch or a fan
	{
		Integer targetLevel = 0

		if (cmd.hasProperty("targetValue")) //  Consider duration and target, but only when both are present and in transition with duration > 0 
		{
			targetLevel = cmd.targetValue ?: cmd.value
		} else {
			targetLevel = cmd.value
		}

		String priorSwitchState = targetDevice.currentValue("switch")
		String newSwitchState = ((targetLevel != 0) ? "on" : "off")
		Short priorLevel = targetDevice.currentValue("level")

		if ((targetLevel == 99) && (priorLevel == 100)) targetLevel = 100

		if (targetDevice.hasAttribute("switch"))
		{
			targetDevice.sendEvent(	name: "switch", value: newSwitchState, descriptionText: "Switch state set", type: "physical" )
			if (txtEnable) log.info "Device ${targetDevice.displayName} set to ${newSwitchState}."
		}
		if (targetDevice.hasAttribute("speed")) 
		{
			targetDevice.sendEvent( name: "speed", value: levelToSpeed(targetLevel), descriptionText: "Speed set", type: "physical" )
		}
		if (targetDevice.hasAttribute("level") && (targetLevel != 0 )) // Only handle on values 1-99 here. If device was turned off, that would be handle in the switch state block above.
		{
			targetDevice.sendEvent( name: "level", value: targetLevel, descriptionText: "Level set", unit:"%", type: "physical" )
			if (txtEnable) log.info "Device ${targetDevice.displayName} level set to ${targetLevel}%"		
		}
	}
}

void on(Map params = [cd: null , duration: null , level: null ])
{
	com.hubitat.app.DeviceWrapper targetDevice = (params.cd ?: device)
	Short ep = params.cd ? (params.cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	
	Integer targetLevel = 100
	if (targetDevice.hasAttribute("switch")) {	
		targetDevice.sendEvent(name: "switch", value: "on", descriptionText: "Device turned on", type: "digital")
	} else {
		log.error "Device ${targetDevice.displayName}: Error in function on(). Device does not have a switch attribute."
	}
	
	if (targetDevice.hasAttribute("level")) {
		targetLevel = params.level ?: (targetDevice.currentValue("level") as Integer) ?: 100
		targetLevel = Math.max(Math.min(targetLevel, 100), 0)
		targetDevice.sendEvent(name: "level", value: targetLevel, descriptionText: "Device level set", unit:"%", type: "digital")
	}
	
	sendZwaveValue(value: targetLevel, duration: params.duration, ep: ep)
}


void off(Map params = [cd: null , duration: null ]) {
	com.hubitat.app.DeviceWrapper targetDevice = (params.cd ?: device)
	Short ep = params.cd ? (params.cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	
	if (txtEnable) log.info "Device ${targetDevice.displayName}: Turning device to: Off."

	if (targetDevice.hasAttribute("switch")) {	
		targetDevice.sendEvent(name: "switch", value: "off", descriptionText: "Device turned off", type: "digital")
		
		sendZwaveValue(value: 0, duration: params.duration, ep: ep)
	} else {
		log.error "Device ${targetDevice.displayName}: Error in function off(). Device does not have a switch attribute."
	}
}

void setLevel(level, duration = null ) {
	setLevel(level:level, duration:duration)
}
	
void setLevel(Map params = [cd: null , level: null , duration: null ])
{
	if ( (params.level as Integer) <= 0) {
		off(cd:params.cd, duration:params.duration)
	} else {
		on(cd:params.cd, level:params.level, duration:params.duration)
	}
}

void startLevelChange(direction, cd = null ){
	com.hubitat.app.DeviceWrapper targetDevice = (cd ? cd : device)
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	
    Integer upDown = (direction == "down" ? 1 : 0)
    sendSupervised(zwave.switchMultilevelV4.switchMultilevelStartLevelChange(upDown: upDown, ignoreStartLevel: 1, startLevel: 0), ep)
}

void stopLevelChange(cd = null ){
	com.hubitat.app.DeviceWrapper targetDevice = (cd ? cd : device)
	Short ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Short) : null
	
	sendSupervised(zwave.switchMultilevelV4.switchMultilevelStopLevelChange(), ep)
	sendToDevice(zwave.basicV1.basicGet(), ep)

}



///////////////////////////////////////////////////////////////////////////////////////////////
///////////////                  Central Scene Processing          ////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////

// Use a concurrentHashMap to hold the last reported state. This is used for "held" state checking
// In a "held" state, the device will send "held down refresh" messages at either 200 mSecond or 55 second intervals.
// Hubitat should not generated repreated "held" messages in response to a refresh, so inhibit those
// Since the concurrentHashMap is @Field static -- its data structure is common to all devices using this
// Driver, therefore you have to key it using the device.deviceNetworkId to get the value for a particuarl device.
@Field static  ConcurrentHashMap centralSceneButtonState = new ConcurrentHashMap<String, String>(128)

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
	// Not currently used.
	sendToDevice(zwave.centralSceneV3.centralSceneSupportedGet() )
}

void multiTap(button, taps) {
    sendEvent(name:"multiTapButton", value:("${button}.${taps}" as Float), type:"physical", unit:"Button #.Tap Count", isStateChange:true )		
}

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd)
{

	// Check if central scene is already in a held state, if so, and you get another held message, its a refresh, so don't send a sendEvent
	if ((getCentralSceneButtonState(cmd.sceneNumber as Integer) == "held") && (cmd.keyAttributes == 2)) return

	// Central scene events should be sent with isStateChange:true since it is valid to send two of the same events in a row (except held, whcih is handled in previous line)
    Map event = [value:cmd.sceneNumber, type:"physical", unit:"button#", isStateChange:true]
	
	event.name = [	0:"pushed", 1:"released", 2:"held",  3:"doubleTapped", 
					4:"buttonTripleTapped", 5:"buttonFourTaps", 6:"buttonFiveTaps"].get(cmd.keyAttributes as Integer)
	
	String tapDescription = [	0:"Pushed", 1:"Released", 2:"Held",  3:"Double-Tapped", 
								4:"Three Taps", 5:"Four Taps", 6:"Five Taps"].get(cmd.keyAttributes as Integer)
    
	// Save the event name for event that is about to be sent using sendEvent. This is important for 'held' state refresh checking
	setCentralSceneButtonState(cmd.sceneNumber, event.name)	
	
	event.descriptionText="${device.displayName}: Button #${cmd.sceneNumber}: ${tapDescription}"

	if (device.hasAttribute( event.name )) sendEvent(event)
	
	// Next code is for the custom attribute "multiTapButton".
	Integer taps = [0:1, 3:2, 4:3, 5:4, 6:5].get(cmd.keyAttributes as Integer)
	if ( taps && device.hasAttribute("multiTapButton") )
	{
		event.name = "multiTapButton"
		event.unit = "Button #.Tap Count"
		event.value = ("${cmd.sceneNumber}.${taps}" as Float)
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
@Field static ConcurrentHashMap<String, ConcurrentHashMap> OpenSmartHouseRecords = new ConcurrentHashMap<String, ConcurrentHashMap>(64)
@Field static ConcurrentHashMap<String, ConcurrentHashMap> deviceDataPageLinks = new ConcurrentHashMap<String, String>(64)

Map getInputControlsForDevice()
{
	if (state.parameterInputs?.get(firmwareKey()) == "none" ) return null
	
	ConcurrentHashMap inputControls = OpenSmartHouseRecords.get(firmwareKey(), new ConcurrentHashMap(64))
	String deviceWebPage = deviceDataPageLinks.get(firmwareKey())
	if (deviceWebPage && (inputControls?.size() > 0)) 
	{
		if (!state.parameterInputs?.containsKey(firmwareKey()))
		{
			state.remove("parameterInputs")
			state.parameterInputs = [(firmwareKey()):inputControls]
		}
		if (state.deviceInformation.is( null ) ) state.deviceInformation = deviceWebPage

	} else if (state.deviceInformation && state.parameterInputs?.containsKey(firmwareKey())) {
		state.parameterInputs.get(firmwareKey()).each{ k, v -> inputControls.put( k as Integer, v) }
		deviceDataPageLinks.put(firmwareKey(), state.deviceInformation)		
	} else {
		try {
			List parameterData = getOpenSmartHouseData()
			if (!parameterData) return null
			if (parameterData.size() == 0)
				{
					state.parameterInputs = [(firmwareKey()):"none"]
					return null
				}
			inputControls = createInputControls(parameterData)
			if (inputControls) {
					state.parameterInputs = [(firmwareKey()):inputControls]
				}
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
	deviceDataPageLinks.put(firmwareKey(), state.deviceInformation)
    String queryByDatabaseID= "http://www.opensmarthouse.org/dmxConnect/api/zwavedatabase/device/read.php?device_id=${thisDeviceData.id}"    
    
	httpGet([uri:queryByDatabaseID]) { resp-> 
				return resp?.data?.parameters
			}
}

Map createInputControls(data)
{
	if (!data) return null
	
	Map inputControls = [:]	
	data?.each
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


void logsOff(){
    log.warn "Device ${device.displayName}: debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}


///////////////////////////////////////////////////////////////////////////////////////
///////      Handle Update(), and Set, Get, and Process Parameter Values       ////////
/////////////////////////////////////////////////////////////////////////////////////// 



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

	if (txtEnable) log.info "Device ${device.displayName}: Pending changes are: ${pendingChanges}"
	
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

void setParameter(parameterNumber,value) {
	setParameter(parameterNumber:parameterNumber, value:value)
}


void setParameter(Map params = [parameterNumber: null , value: null ] ){
    if (params.parameterNumber.is( null ) || params.value.is( null ) ) {
		log.warn "Device ${device.displayName}: Can't set parameter ${parameterNumber}, Incomplete parameter list supplied... syntax: setParameter(parameterNumber,size,value), received: setParameter(${parameterNumber}, ${size}, ${value})."
    } else {
		parameterReports = allParameterReports.get(device.deviceNetworkId, new ConcurrentHashMap<Short, hubitat.zwave.Command>(32))

		Short PSize = parameterReports.get(params.parameterNumber as Short).size

		List<hubitat.zwave.Command> cmds = []
	    cmds << secure(supervise(zwave.configurationV1.configurationSet(scaledConfigurationValue: params.value as BigInteger, parameterNumber: params.parameterNumber as Short, size: PSize)))
	    cmds << secure(zwave.configurationV1.configurationGet(parameterNumber: params.parameterNumber))
		sendToDevice(cmds)
		
		// Wait for the report that is returned after the configurationGet, and then update the input controls so they display the updated value.
		hubitat.zwave.Command report = myReportQueue("7006").poll(10, TimeUnit.SECONDS)
		if (report)
		{
			if ((report.scaledConfigurationValue) == (params.value as BigInteger)) {
				log.info "Device ${device.displayName}: Successfully set parameter #: ${params.parameterNumber} to value ${params.value}."

				String configName = "configParam${"${report.parameterNumber}".padLeft(3,"0")}"
				if (txtEnable) log.info "Device ${device.displayName}: updating settings data for parameter ${report.parameterNumber} to new value ${report.scaledConfigurationValue}!"
				device.updateSetting("${configName}", report.scaledConfigurationValue as Integer)				
	
			} else {
				log.warn "Device ${device.displayName}: Failed to set parameter #: ${params.parameterNumber} to value ${params.value}. Value of parameter is set to ${report.scaledConfigurationValue} instead."
			}
		}
    }
}

// Gets a map of all the values currently stored in the input controls.
Map<Short, BigInteger> getParameterValuesFromInputControls()
{
	ConcurrentHashMap inputs = getInputControlsForDevice()
	
	if (!inputs) return
	
	Map<Short, BigInteger> settingValues = [:]
	
	inputs.each 
		{ PKey , PData -> 
			BigInteger newValue = 0
			// if the setting returns an array, then it is a bitmap control, and add together the values.
			if (settings.get(PData.name as String) instanceof ArrayList) {
				settings.get(PData.name as String).each{ newValue += it as BigInteger }
			} else  {   
				newValue = settings.get(PData.name as String) as BigInteger  
			}
			settingValues.put(PKey as Short, newValue)
		}
	if (txtEnable) log.info "Device ${device.displayName}: Current Parameter Setting Values are: " + settingValues
	return settingValues
}
////////////////////////////////////////////////////////////////////////
/////////////      Parameter Updating and Management      /////////////
////////////////////////////////////////////////////////////////////////

@Field static  ConcurrentHashMap<String, ConcurrentHashMap> allPendingParameterChanges = new ConcurrentHashMap<String, ConcurrentHashMap>(128)
@Field static  ConcurrentHashMap<String, ConcurrentHashMap> allParameterReports = new ConcurrentHashMap<String, ConcurrentHashMap>(128)

ConcurrentHashMap<Short, BigInteger> getPendingChangeMap()
{
	return allPendingParameterChanges.get(device.deviceNetworkId, new ConcurrentHashMap<Short, BigInteger>(32))
}

Map<Short, BigInteger> getParameterValuesFromDevice(Map options = [useCache: true ])
{
	Map<Short,hubitat.zwave.Command> parameterReports = allParameterReports.get(device.deviceNetworkId, new ConcurrentHashMap<Short, hubitat.zwave.Command>(32))
	
	ConcurrentHashMap inputs = getInputControlsForDevice()	
	if (!inputs) return null
			
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
				sendToDevice(zwave.configurationV1.configurationGet(parameterNumber: k as Short))
				report = myReportQueue("7006").poll(10, TimeUnit.SECONDS)
				if (! report) return
				// Single-byte return values > 127 get turned into negative numbers when using scaledConfigurationValue, so don't use cmd.scaledConfiguraiton if cmd.size == 1!
				BigInteger newValue = (report.size == 1) ? report.configurationValue[0] : report.scaledConfigurationValue			
				if (newValue < 0) log.warn "Device ${device.displayName}: Negative configuration value reported for configuration paramater ${k}."
				
				if (report) parameterValues.put(report.parameterNumber, newValue )
			}
	}
	return parameterValues
}

@Field static ConcurrentHashMap<String, Boolean> alreadyRetrievedParameters = new ConcurrentHashMap<String,Boolean>(128)
void setInputControlsToDeviceValue()
{
	Boolean alreadyRetrieved = alreadyRetrievedParameters.get(device.deviceNetworkId, false )
	
	if (alreadyRetrieved) return
	
	Map<Short, BigInteger> parameterValues =  getParameterValuesFromDevice(useCache: true )
	ConcurrentHashMap<Short, BigInteger> pendingChanges = getPendingChangeMap()
	parameterValues?.each{ key, value ->
		String configName = "configParam${"${key}".padLeft(3,"0")}"
		if (txtEnable) log.info "Device ${device.displayName}: updating settings data for parameter ${key} to new value ${value}!"
		device.updateSetting("${configName}", value as Integer)
		pendingChanges.remove(key as Short)
	}
	alreadyRetrievedParameters.put(device.deviceNetworkId, true )
}

void zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport  cmd)
{ 
	parameterReports = allParameterReports.get(device.deviceNetworkId, new ConcurrentHashMap<Short, hubitat.zwave.Command>(32))
	parameterReports.put(cmd.parameterNumber, cmd)
	Boolean transferredReport = myReportQueue(cmd.CMD).offer(cmd)
	if (!transferredReport) log.warn "Device ${device.displayName}: Failed to transfer Configuration Report."
}

//////////////////////////////////////////////////////////////////////
//////        Locks        ///////
//////////////////////////////////////////////////////////////////////

void lockInitialize()
{
	sendToDevice (secure( zwave.userCodeV1.usersNumberGet() ))
}

void zwaveEvent(hubitat.zwave.commands.usercodev1.UserCodeReport cmd, Short ep = null ) { 
	com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)
	
	log.warn "Lock code is still under development. Functions may not be fully implemented."
	if (logEnable) log.debug "Device ${targetDevice.displayName}: received User Code Report: " + cmd
}

void zwaveEvent(hubitat.zwave.commands.usercodev1.UsersNumberReport cmd, Short ep = null ) { 
	com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)
	
	log.warn "Lock code is still under development. Functions may not be fully implemented."

	if (logEnable) log.debug "Device ${targetDevice.displayName}: received Users Number Report: " + cmd
	sendEvent(name:"maxCodes", value: cmd.supportedUsers)
}

void lockrefresh(Short ep = null )
{
	sendToDevice (secure(zwave.doorLockV1.doorLockOperationGet(), ep))
}
void lock(Short ep = null )
{
	List<hubitat.zwave.Command> cmds=[]
	cmds << secure( zwave.doorLockV1.doorLockOperationSet(doorLockMode: 0xFF), ep )
	cmds << "delay 4500"
	cmds << secure( zwave.doorLockV1.doorLockOperationGet(), ep )
	sendToDevice(cmds)
}

void unlock(Short ep = null )
{
	List<hubitat.zwave.Command> cmds=[]
	cmds << secure( zwave.doorLockV1.doorLockOperationSet(doorLockMode: 0x00), ep )
	cmds << "delay 4500"
	cmds << secure( zwave.doorLockV1.doorLockOperationGet(), ep )
	sendToDevice(cmds)
}

void deleteCode(codeposition, Short ep = null )
{
    if (logEnable) log.debug "Device ${device.displayName}:  deleting code at position ${codeNumber}."
	// userIDStatus of 0 corresponds to Z-Wave  "Available (not set)" status.
	sendToDevice (secure( zwave.userCodeV1.userCodeSet(userIdentifier:codeNumber, userIdStatus:0), ep ))
	sendToDevice (secure( zwave.userCodeV1.userCodeGet(userIdentifier:codeNumber), ep ))
}

void getCodes(Short ep = null )
{
	log.warn "Device ${device.displayName}: Lock code is still under development. getCodes function is not be fully implemented."

	List<hubitat.zwave.Command> cmds=[]
		cmds << secure(zwave.userCodeV1.usersNumberGet(), ep)
		
		// need to first get the usersNumber and then get the code for each of those users!
		cmds << secure(zwave.userCodeV1.userCodeGet(userIdentifier: 1), ep)
	sendToDevice(cmds)
}

void setCode(codeposition, pincode, name, Short ep = null )
{
	log.warn "Device ${device.displayName}: Lock code is still under development. setCode function does not check for code length. You must ensure you use a permitted length!."

	String userCode = pincode as String
	if (logEnable) log.debug "Device ${device.displayName}: setting code at position ${codeposition} to ${pincode}."
	assert (userCode instanceof String) 

	List<hubitat.zwave.Command> cmds=[]
	cmds << secure( zwave.userCodeV1.userCodeSet(userIdentifier:codeNumber, userIdStatus:0), ep )
	cmds << secure(zwave.userCodeV1.userCodeSet(userIdentifier:codeposition, userIdStatus:1, userCode:userCode ), ep)
	cmds << secure(zwave.userCodeV1.userCodeGet(userIdentifier:codeposition), ep)
	sendToDevice(cmds)
}

void setCodeLength(pincodelength)
{
	log.warn "Device ${device.displayName}: Code Length command not supported. If your device supports code length settings, you may be able to set the code length using Z-Wave Parameter Settings controls."
}

// This is another form of door lock reporting. I believe its obsolete, but I've included it just in case some lock requires it.  
// Modes 2-4 are not implemented by Hubitat.
void zwaveEvent(hubitat.zwave.commands.doorlockv1.DoorLockOperationReport cmd, Short ep = null ) 
{
	com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)
	
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

	if (lockEvent) targetDevice.sendEvent(lockEvent)
}

/////////////////////////////////////////////////////////////////////////
//////        Create and Manage Child Devices for Endpoints       ///////
/////////////////////////////////////////////////////////////////////////

void deleteUnwantedChildDevices()
{	
	hubitat.zwave.Command endPointReport = getCachedMultiChannelEndPointReport()
	if (!endPointReport) return
	
	Short numberOfEndPoints = endPointReport.endPoints
	
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
	
	Short thisKid = 1
	for ( thisKid; thisKid <= numberOfEndPoints; thisKid++)
	{
		String childNetworkId = "${device.deviceNetworkId}-ep${"${thisKid}".padLeft(3, "0") }"
		com.hubitat.app.DeviceWrapper cd = getChildDevice(childNetworkId)

		if (cd.is( null )) {
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

//////////////////////////////////////////////////////////////////////
//////        Child Device Methods        ///////
////////////////////////////////////////////////////////////////////// 

Short getEndpoint(com.hubitat.app.DeviceWrapper thisDevice) {
	if (thisDevice.is( null )) return null 
	return thisDevice.deviceNetworkId.split("-ep")[-1] as Short
}

void componentRefresh(com.hubitat.app.DeviceWrapper cd){
	refreshEndpoint(cd:cd)
}

void componentOn(com.hubitat.app.DeviceWrapper cd){
	on(cd:cd)
}

void componentOff(com.hubitat.app.DeviceWrapper cd){
	off(cd:cd)
}

void componentSetLevel(com.hubitat.app.DeviceWrapper cd, level, transitionTime = null) {
	if (cd.hasCapability("FanControl") ) {
			setSpeed(cd:cd, level:level, speed:levelToSpeed(level as Integer))
		} else { 
			setLevel(level:level, duration:transitionTime, cd:cd) 
		}
}

void componentStartLevelChange(com.hubitat.app.DeviceWrapper cd, direction) {
	startLevelChange(direction:direction, cd:cd)
}

void componentStopLevelChange(com.hubitat.app.DeviceWrapper cd) {
	stopLevelChange(cd:cd)
}

void componentSetSpeed(com.hubitat.app.DeviceWrapper cd, speed) {
	setSpeed(speed:speed, cd:cd)
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
								getReportCachedByProductId(zwave.switchMultilevelV4.switchMultilevelSupportedGet(), ep) 
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
		deviceClasses.each{ it ->  implementsZwaveClass(it) }
		getCachedCentralSceneSupportedReport()		
		getCachedNotificationSupportedReport()										
		getCachedMeterSupportedReport()																		
		getCachedProtectionSupportedReport()										
		getCachedSwitchMultilevelSupportedReport()				
		getCachedSensorMultilevelSupportedSensorReport()	
		
		if (implementsZwaveClass(0x60))
		{
			hubitat.zwave.Command endPointReport = getCachedMultiChannelEndPointReport()
			for (Short endPoint = 1; endPoint < (endPointReport.endPoints as Short); endPoint++)
			{
				hubitat.zwave.Command report = getCachedMultiChannelCapabilityReport(endPoint as Short)
				getCachedNotificationSupportedReport(endPoint)										
				getCachedMeterSupportedReport(endPoint)																		
				getCachedProtectionSupportedReport(endPoint)										
				getCachedSwitchMultilevelSupportedReport(endPoint)				
				getCachedSensorMultilevelSupportedSensorReport(endPoint)			
			}
		}
}


@Field static ConcurrentHashMap<String, ConcurrentHashMap> reportsCachedByProductId = new ConcurrentHashMap<String, ConcurrentHashMap>(32)
@Field static ConcurrentHashMap<String, ConcurrentHashMap> reportsCachedByNetworkId = new ConcurrentHashMap<String, ConcurrentHashMap>(128)

// reportQueues stores a map of SynchronousQueues. When requesting a report from a device, the report handler communicates the report back to the requesting function using a queue. This makes programming more like "synchronous" programming, rather than asynchronous handling.
// This is a map within a map. The first level map is by deviceNetworkId. Since @Field static variables are shared among all devices using the same driver code, this ensures that you get a unique second-level map for a particular device. The second map is keyed by the report class hex string. For example, if you want to wait for the configurationGet report, wait for "7006".
@Field static reportQueues = new ConcurrentHashMap<String, ConcurrentHashMap>(128)

SynchronousQueue myReportQueue(String reportClass)
{
	ConcurrentHashMap thisDeviceQueues = reportQueues.get(device.deviceNetworkId, new ConcurrentHashMap<String,SynchronousQueue>(32))
	
	// Get the queue if it exists, create (new) it if it does not.
	SynchronousQueue thisReportQueue = thisDeviceQueues.get(reportClass, new SynchronousQueue())
	return thisReportQueue
}

//////////////////////////////////////////////////////////////////////
//////       @Field staic Hash Map Management Functions            ///////
////////////////////////////////////////////////////////////////////// 

// This driver uses a number of @Field static variables. Since these are shared among all devices that use this driver file, they must be keyed to ensure they are unique to a device.
// Two types of keys are used. productKey() generates a key that is unique to a particular model and firmware. The data keyed by this key is shared among all devices of the same model. This is useful where each device will have the exact same data at all times (e.g., the set of parameter inputs that it supports).  A second key is device.deviceNetworkId. This is used where the data is unique to a particular device

///   Functions to generate keys used to access the concurrent Hash Maps and to store into the hash maps ///
Integer getManufacturerNumber() { device.getDataValue("manufacturer").toInteger() }
Integer getDeviceTypeNumber() { device.getDataValue("deviceType").toInteger() }
Integer getDeviceIdNumber() {device.getDataValue("deviceId").toInteger() }

String getManufacturerHexString() { return hubitat.helper.HexUtils.integerToHexString( getManufacturerNumber(), 2) }
String getDeviceTypeHexString() { return hubitat.helper.HexUtils.integerToHexString( getDeviceTypeNumber(), 2) }
String getDeviceIdHexString() { return hubitat.helper.HexUtils.integerToHexString( getDeviceIdNumber(), 2) }

String productKey() // Generates a key based on manufacturer / device / firmware. Data is shared among all similar end-devices.
{
	// if (remindEnable) log.warn "productKey function should be updated with a hash based on inclusters as some devices may remove change their inclusters depending on pairing state. for example, Zooz Zen 18 motion sensor may or may not show with a battery!"
	
	String key = "${getManufacturerHexString()}:${getDeviceTypeHexString()}:${getDeviceIdHexString()}:"
	return key
}

String firmwareKey()
{
	hubitat.zwave.Command   versionReport = getCachedVersionReport() 
	if (!versionReport)  {
			log.warn "Device ${device.displayName} called firmwareKey function but firmware version is not cached! Device may not be operating correctly. Using previously stored value."
			String versionFormatString = device.getDataValue("versionReport") 
			if (versionFormatString)  {
				return productKey() + versionFormatString
			} else {
				return null
			}
		}
	return productKey() + versionReport?.format()
}

///////////////////////////////////////////
@Field static ConcurrentHashMap<String, ConcurrentHashMap> CommandClassVersionReportsByProductID = new ConcurrentHashMap<String, ConcurrentHashMap>(64)

hubitat.zwave.Command  getCachedVersionCommandClassReport(Short requestedCommandClass)		
{
	ConcurrentHashMap ClassReports = CommandClassVersionReportsByProductID.get(firmwareKey(), new ConcurrentHashMap<Short,ConcurrentHashMap>(32))

	hubitat.zwave.Command cmd = ClassReports?.get(requestedCommandClass)
	
	if (cmd) { 
		return cmd
	} else {
		sendToDevice(zwave.versionV1.versionCommandClassGet(requestedCommandClass: requestedCommandClass ))
		cmd = myReportQueue("8614").poll(10, TimeUnit.SECONDS)
		if ( cmd.is( null ) ) {
				log.warn "Device ${device.displayName}: failed to retrieve a requested command class ${requestedCommandClass}."
				return null 
			}
		ClassReports.put(cmd.requestedCommandClass, cmd)
	}
	return ClassReports?.get(requestedCommandClass)
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionCommandClassReport cmd, Short ep = null ) { 
		myReportQueue(cmd.CMD).offer(cmd) 
	}
///////////////////////////////////////////

String commandHexStringToReportHexString(String commandClass)
{
	Integer getReportNumber = hubitat.helper.HexUtils.hexStringToInt(commandClass) + 1
	return hubitat.helper.HexUtils.integerToHexString(getReportNumber, 2)
}

hubitat.zwave.Command   getReportCachedByNetworkId(Map options = [:], hubitat.zwave.Command getCmd, ep )  
{
	ConcurrentHashMap cacheForThisNetId = reportsCachedByNetworkId.get(device.deviceNetworkId, new ConcurrentHashMap<String,SynchronousQueue>(128))
	ConcurrentHashMap cacheForThisEndpoint = cacheForThisNetId?.get((ep ?: 0) as Short, new ConcurrentHashMap<String,Object>(16) )	
	
	String reportClass = commandHexStringToReportHexString(getCmd.CMD)		
	hubitat.zwave.Command cmd = cacheForThisEndpoint?.get(reportClass)
	
	if (cmd) { 
		return cmd
	} else {
		Map transferredData;
		Integer tries = 0
		while( transferredData.is ( null ) && (tries <= 5) )
		{
			tries++
			sendToDevice(getCmd, ep)
			transferredData = myReportQueue(reportClass).poll(10, TimeUnit.SECONDS)
		}
		cmd =  transferredData.report
		if (cmd.CMD == "8612") {
				device.updateDataValue("versionReport", cmd.format())
			}
			
		cacheForThisEndpoint.put(cmd.CMD, cmd)
	}
	return cacheForThisEndpoint?.get(reportClass)
}


hubitat.zwave.Command   getReportCachedByProductId(Map options = [:], hubitat.zwave.Command getCmd, Short ep)  
{
	if (!implementsZwaveClass(getCmd.commandClassId, ep)) {
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
	ConcurrentHashMap cacheForThisProductId = reportsCachedByProductId.get(firmwareKey(), new ConcurrentHashMap<String,SynchronousQueue>(64))
	ConcurrentHashMap cacheForThisSubIndex = cacheForThisProductId?.get(subIndex, new ConcurrentHashMap<String,Object>(32) )	

	String reportClass = commandHexStringToReportHexString(getCmd.CMD)
	hubitat.zwave.Command cmd = cacheForThisSubIndex?.get(reportClass)
	
	if (cmd) { 
		return cmd
	} else {
		Map transferredData;
		Integer tries = 0
		while( transferredData.is ( null ) && (tries <= 3) )
		{
			tries++
			sendToDevice(getCmd, ep)
			transferredData = myReportQueue(reportClass).poll(10, TimeUnit.SECONDS)
		}
		cmd =  transferredData?.report
		if (cmd) cacheForThisSubIndex.put(cmd.CMD, cmd)
	}
	return cacheForThisSubIndex?.get(reportClass)
}

// For testing and debugging
void logStoredReportCache()
{
	log.info "report cache for items stored based on ProductId and firmware version is: " + reportsCachedByProductId
	log.info "reportsCachedByNetworkId stored based on NetworkId is: " + reportsCachedByNetworkId
	log.info "CommandClassVersionReportsByProductID is: " + CommandClassVersionReportsByProductID
	log.info "OpenSmartHouseRecords are: " + OpenSmartHouseRecords
}

/////////////////  Caching Functions To return Reports! //////////////////////////////

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneSupportedReport  cmd, Short ep = null )  				{ transferReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv5.MeterSupportedReport cmd, Short ep = null ) 								{ transferReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelEndPointReport  cmd, Short ep = null )  				{ transferReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationSupportedReport cmd, Short ep = null )    	 			{ transferReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.notificationv8.EventSupportedReport cmd, Short ep = null )    	 					{ transferReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.protectionv2.ProtectionSupportedReport  cmd, Short ep = null )   					{ transferReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv4.SwitchMultilevelSupportedReport  cmd, Short ep = null )   		{ transferReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelSupportedSensorReport  cmd, Short ep = null ) 	{ transferReport(cmd, ep) }		
void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd, Short ep = null )  									{ transferReport(cmd, ep) }
Boolean transferReport(cmd, ep)
{ 	
	Boolean transferredReport = myReportQueue(cmd.CMD).offer([report:cmd, endPoint:ep])
	
	if (!transferredReport) { 
		com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)
		log.warn "Device ${targetDevice.displayName}: Failed to transfer version report for command ${cmd}." 
	}
}

void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCapabilityReport  cmd)
{ 
	// This requires special processing. Endpoint is in the message, not passed as a parameter, but want to store it based on endpoint!
	Boolean transferredReport = myReportQueue(cmd.CMD).offer([report:cmd, endPoint:cmd.endPoint])
	if (!transferredReport) { log.warn "Device ${device.displayName}: Failed to transfer version report for command ${cmd}." }
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
Integer implementsZwaveClass(commandClass, Short ep = null ) {implementsZwaveClass(commandClass as Short, ep as Short )}
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
			if (getCachedMultiChannelCapabilityReport(ep)?.commandClass.contains(commandClass)) {
				return getCachedVersionCommandClassReport(commandClass)?.commandClassVersion
			} else {
				return null
			}
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
    if (superviseEnable && (!commandSupervisionNotSupported(command)) && implementsZwaveClass(0x6C))
	{
		// Get the next session ID mod 32, but if there is no stored session ID, initialize it with a random value.
		Short nextSessionID = supervisionSessionIDs.get(device.getDeviceNetworkId() as String,((Math.random() * 32) % 32) as Short )
		nextSessionID = (nextSessionID + 1) % 32 // increment and then mod with 32
		supervisionSessionIDs.replace(device.getDeviceNetworkId(), nextSessionID)
		
		// Store the command that is being sent so that you can log.debug it and resend in case of failure!
		supervisionSentCommands.get(device.getDeviceNetworkId() as String, new ConcurrentHashMap<Short, hubitat.zwave.Command>(128)).put(nextSessionID, command)

		if (logEnable) log.debug "Supervising a command: ${command} with session ID: ${nextSessionID}."
		return zwave.supervisionV1.supervisionGet(sessionID: nextSessionID, statusUpdates: false ).encapsulate(command)
	} else {
		return command
	}
}

// This handles a supervised message (a "get") received from the Z-Wave device //
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd, Short ep = null ) {
	com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)
	
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(parseMap, defaultParseMap)
	
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand, ep)
    }
    sendToDevice((new hubitat.zwave.commands.supervisionv1.SupervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0)), ep)
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionReport cmd, Short ep = null ) {
	com.hubitat.app.DeviceWrapper targetDevice = getTargetDeviceByEndPoint(ep)
	
	hubitat.zwave.Command whatWasSent = supervisionSentCommands?.get(device.getDeviceNetworkId() as String)?.get(cmd.sessionID)

	switch (cmd.status)
	{
		case 0x00:
			log.warn "Device ${targetDevice.displayName}: Z-Wave Command supervision not supported for: ${whatWasSent}. Re-sending without supervision."
			markSupervisionNotSupported(whatWasSent)
			sendToDevice(whatWasSent, ep )
			break
		case 0x01:
			if (txtEnable) log.info "Device ${targetDevice.displayName}: Still processing command: ${whatWasSent}."
		case 0x02:
			log.warn "Device ${targetDevice.displayName}: Z-Wave supervised command reported failure. Failed command: ${whatWasSent}. Re-sending without supervision."
			markSupervisionNotSupported(whatWasSent)
			sendToDevice(whatWasSent, ep )
			break
		case 0xFF:
			if (txtEnable) log.info "Device ${targetDevice.displayName}: Device successfully processed supervised command ${whatWasSent}."
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

void sendSupervised(hubitat.zwave.Command cmd, Short ep = null ) { 
		String sendThis = secure(supervise(cmd), ep)
		if (logEnable) {
			log.debug "Device ${getTargetDeviceByEndPoint(ep).displayName}: In sendSupervised, Sending supervised command: " + sendThis
		}
		sendHubCommand(new hubitat.device.HubAction( sendThis, hubitat.device.Protocol.ZWAVE)) 
	}
void sendToDevice(String cmd) { sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE)) }

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) { return delayBetween(cmds.collect{ it }, delay) }

////    Send Simple Z-Wave Commands to Device  ////	
void sendZwaveValue(Map params = [value: null , duration: null , ep: null ] )
{
	Integer newValue = Math.max(Math.min(params.value, 99),0)

	if ( !(0..100).contains(params.value) ) {
	log.warn "Device ${}: in function sendZwaveValue() received a value ${params.value} that is out of range. Valid range 0..100. Using value of ${newValue}."
	}
	
	if (implementsZwaveClass(0x26, params.ep)) { // Multilevel  type device
		sendSupervised(zwave.switchMultilevelV4.switchMultilevelSet(value: newValue, dimmingDuration:params.duration as Short), params.ep)	
	} else if (implementsZwaveClass(0x25, params.ep)) { // Switch Binary Type device
		sendSupervised(zwave.switchBinaryV1.switchBinarySet(switchValue: newValue ), params.ep)
	} else if (implementsZwaveClass(0x20, params.ep)) { // Basic Set Type device
		log.warn "Device ${targetDevice.displayName}: Using Basic Set to turn on device. A more specific command class should be used!"
		sendSupervised(zwave.basicV2.basicSet(value: newValue ), params.ep)
	} else {
		log.error "Device ${device.displayName}: Error in function sendZwaveValue(). Device does not implement a supported class to control the device!.${ep ? " Endpoint # is: ${params.ep}." : ""}"
		return
	}
}
