@Field static  Integer driverVersion = 1
import java.util.concurrent.*;
import groovy.transform.Field

metadata {
	definition (name: "[Beta] Advanced Zwave Plus Metering Switch",namespace: "jvm", author: "jvm") {
		capability "Initialize"
		// capability "Configuration" // Does the same as Initialize, so don't show the separate control!
		capability "Refresh"
		
 		// Pick one of the following 5 Capabilities. Comment out the remainder.
			// capability "Bulb"
			// capability "Light"
			// capability "Outlet"		
			// capability "RelaySwitch"
			capability "Switch"	
			
			capability "EnergyMeter"
			capability "PowerMeter"
			capability "VoltageMeasurement"
			
		capability "Battery"
		
		// Include the following for dimmable devices.
			// capability "SwitchLevel"
			// capability "ChangeLevel"
		
		// Central Scene functions. Include the "commands" if you want to generate central scene actions from the web interface. If they are not included, central scene will still be generated from the device.
			
			capability "PushableButton"
			command "push", ["NUMBER"]	
			capability "HoldableButton"
			command "hold", ["NUMBER"]
			capability "ReleasableButton"
			command "release", ["NUMBER"]
			capability "DoubleTapableButton"
			command "doubleTap", ["NUMBER"]
			
			command "meterRefresh"
			// command "batteryGet"
			
			// capability "Lock"
			// capability "Lock Codes"
			// command "lockrefresh"
			// command "getSupportedNotifications"
		
		command "getAllParameterValues"
		// capability "Sensor"
		// capability "MotionSensor"
		// capability "ContactSensor"
		// capability "RelativeHumidityMeasurement"
		// capability "SmokeDetector"
		capability "TamperAlert"
		// capability "TemperatureMeasurement"
		// capability "WaterSensor"
			
			// command "test"
			// command "getFirmwareVersion"
        // The following is for debugging. In final code, it can be removed!
    	// command "getDeviceDataFromDatabase"
		
		/** The "ResetDriverStateData" command deletes all state data stored by the driver. 
		*/
		command "ResetDriverStateData"
		
		/**
			setParameter is a generalized function for setting parameters.	
		*/
			command "setParameter",[
					[name:"parameterNumber",type:"NUMBER", description:"Parameter Number", constraints:["NUMBER"]],
					[name:"size",type:"NUMBER", description:"Parameter Size", constraints:["NUMBER"]],
					[name:"value",type:"NUMBER", description:"Parameter Value", constraints:["NUMBER"]]
					]		
		
		//	fingerprint inClusters:"0x5E,0x25,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x5B,0x6C,0x9F,0x7A", deviceJoinName: "ZWave Plus CentralScene Dimmer" //US
    }
    preferences 
	{
        input name: "advancedEnable", type: "bool", title: "Enable Advanced Configuration", defaultValue: true
        
        if (advancedEnable)
        {
			input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
			input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: true
			input name: "confirmSend", type: "bool", title: "Always confirm new value after sending to device (reduces performance)", defaultValue: false
			state.parameterInputs?.each { input it.value }
        }
    }
}
//////////////////////////////////////////////////////////////////////////////////////////////////////
////////  Utilities for storing data in a global Hash Map shared across driver instances  ////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////

// The following ConcurrentHashMap is used to store data with a device key consisting of manufacturer/ device ID / device type / firmware main / firmware sub
@Field static  ConcurrentHashMap<Long, Map> deviceSpecificData = new ConcurrentHashMap<String, Map>()

/**
getDeviceMapForProduct returns the main Map data structure containing all the data gathered for the particular Product and firmware version. The data may have been gathered by any of the drivers!
*/
synchronized Map getDeviceMapForProduct()
{
	String manufacturer = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("manufacturer").toInteger(), 2)
	String deviceType = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceType").toInteger(), 2)
	String deviceID = 		hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceId").toInteger(), 2) 
	Map deviceFirmware = getFirmwareVersion()
	Integer firmwareMain = 	 	deviceFirmware.main as Integer
	Integer firmwareSub =  	 	deviceFirmware.sub as Integer

	String key = "${manufacturer}:${deviceType}:${deviceID}:${firmwareMain}:${firmwareSub}"

	if (deviceSpecificData.containsKey(key)) 
	{ 
		return deviceSpecificData.get(key)
	} else {
		// Had been using a Semaphore to prevent duplicate writes, but synchronized keyword should do. Commented out Semaphore code!
		// Lock before write and then re-check to be sure another process didn't lock / write first!
		// createDeviceEntryMutex.tryAcquire(1, 5, TimeUnit.SECONDS )
		if (!deviceSpecificData.containsKey(key)) deviceSpecificData.put(key, [:])
		// createDeviceEntryMutex.release()
		return deviceSpecificData.get(key)
	}
}

synchronized Map getDeviceMapByNetworkID()
{
	String netID = device.getDeviceNetworkId()

	if (deviceSpecificData.containsKey(netID)) 
	{
		return deviceSpecificData.get(netID)
	} else {
		// Had been using a Semaphore to prevent duplicate writes, but synchronized keyword should do. Commented out Semaphore code!
		// Lock before write and then re-check to be sure another process didn't lock / write first!
		// createDeviceEntryMutex.tryAcquire(1, 5, TimeUnit.SECONDS )
		if (!deviceSpecificData.containsKey(netID)) deviceSpecificData.put(netID, [:])
		// createDeviceEntryMutex.release()
		return deviceSpecificData.get(netID)
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

synchronized Map getInputControlsForDevice()
{
	Map inputControls = getDeviceMapForProduct().get("inputControls", [:])
	if (inputControls?.size() > 0) 
	{
		// if (logEnable) log.debug "Already have input controls for device ${device.displayName}."
		if (state.parameterInputs.is(null)) state.parameterInputs = inputControls
		return inputControls
	} else if (state.parameterInputs) {
		if (logEnable) log.debug "Loading Input Controls from saved state data."
		state.parameterInputs.each{ k, v -> inputControls.put( k as Integer, v) }
		return inputControls
		
	} else {
		if (logEnable) log.debug "Retrieving input control date from opensmarthouse.org for device ${device.displayName}."

		try
		{
			List parameterData = getOpenSmartHouseData()
			inputControls = createInputControls(allParameterData)
			getDeviceMapForProduct().put("inputControls", inputControls)
		}
		catch (Exception ex)
		{
			log.warn "An Error occurred when attempting to get input controls. Error: ${ex}."
		}
		finally
		{
			state.parameterInputs = inputControls
			return inputControls
		}
	}
}

List getOpenSmartHouseData()
{
	log.info "Getting data from OpenSmartHouse for device ${device.displayName}."
	String manufacturer = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("manufacturer").toInteger(), 2)
	String deviceType = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceType").toInteger(), 2)
	String deviceID = 		hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceId").toInteger(), 2)

    String DeviceInfoURI = "http://www.opensmarthouse.org/dmxConnect/api/zwavedatabase/device/list.php?filter=manufacturer:0x${manufacturer}%20${deviceType}:${deviceID}"

    def mydevice
    
	Map deviceFirmwareVersion = getFirmwareVersion()
	
    httpGet([uri:DeviceInfoURI])
    { 
		resp ->
			mydevice = resp.data.devices.find 
			{ element ->
	 
				Minimum_Version = element.version_min.split("\\.")
				Maximum_Version = element.version_max.split("\\.")
				Integer minMainVersion = Minimum_Version[0].toInteger()
				Integer minSubVersion = Minimum_Version[1].toInteger()
				Integer maxMainVersion = Maximum_Version[0].toInteger()
				Integer maxSubVersion =   Maximum_Version[1].toInteger()        
				if(logEnable) log.debug "Device firmware version in getDeviceDataFromDatabase httpGet is ${deviceFirmwareVersion}"

				Boolean aboveMinimumVersion = (deviceFirmwareVersion.main > minMainVersion) || ((deviceFirmwareVersion.main == minMainVersion) && (deviceFirmwareVersion.sub >= minSubVersion))
			
				Boolean belowMaximumVersion = (deviceFirmwareVersion.main < maxMainVersion) || ((deviceFirmwareVersion.main == maxMainVersion) && (deviceFirmwareVersion.sub <= maxSubVersion))
			
				aboveMinimumVersion && belowMaximumVersion
			}
	}
    if (! mydevice.id) 
	{
	log.warn "No database entry found for manufacturer: ${manufacturer}, deviceType: ${deviceType}, deviceID: ${deviceID}"
	return null
	}
    
    String queryByDatabaseID= "http://www.opensmarthouse.org/dmxConnect/api/zwavedatabase/device/read.php?device_id=${mydevice.id}"    
    
	httpGet([uri:queryByDatabaseID]) { resp-> allParameterData = resp.data.parameters }

	return allParameterData
}

Map createInputControls(data)
{
	Map inputControls = [:]

	if (logEnable) log.debug "Creating Input Controls"
	
	data.each
	{
		if (it.bitmask.toInteger())
		{
			if (!(inputControls?.get(it.param_id)))
			{
				Map newInput = [name: "configParam${"${it.param_id}".padLeft(3, "0")}", title: "(${it.param_id}) Choose Multiple", type:"enum", multiple: true, size:it.size, options: [:]]

                newInput.options.put(it.bitmask.toInteger(), "${it.description}")
				
				inputControls.put(it.param_id, newInput)
			} else { // add to the existing bitmap control
                Map Options = inputControls[it.param_id].options
                Options.put(it.bitmask.toInteger(), "${it.label} - ${it.options[1].label}")
                Options = Options.sort()
                if (logEnable) log.debug "Sorted bitmap Options: ${Options}"
             
                inputControls[it.param_id].options = Options
			}
		} else {
			Map newInput = [name: "configParam${"${it.param_id}".padLeft(3, "0")}", title: "(${it.param_id}) ${it.label}", description: it.description, size:it.size, defaultValue: it.default]
			
			def deviceOptions = [:]
			it.options.each { deviceOptions.put(it.value, it.label) }
			
			// Set input type. Should be one of: bool, date, decimal, email, enum, number, password, time, text. See: https://docs.hubitat.com/index.php?title=Device_Preferences
			if (deviceOptions)
			{
				newInput.type = "enum"
				newInput.options = deviceOptions
			} else {
				newInput.type = "integer"
			}

			inputControls[it.param_id] = newInput
		}
	}
	return inputControls
}

//////////////////////////////////////////////////////////////////////
//////      Initialization, update, and uninstall sequence          ///////
////////////////////////////////////////////////////////////////////// 
void refresh() 
{
	if (txtEnable) "Refreshing device ${device.displayName} status .."
    sendToDevice(secure(zwave.basicV1.basicGet()))
	meterRefresh()
}

void installed() { initialize() }

void configure() { initialize() }

void ResetDriverStateData() { state.clear()}

void initialize()
{
	log.info "Initializing device ${device.displayName}."

	if (state.driverVersion != driverVersion)
	{
		log.info "Driver version updated for device ${device.displayName}, resetting all state data."
		ResetDriverStateData()
		state.driverVersion = driverVersion
	}
	
	getFirmwareVersion()

	log.info "Device ${device.displayName} has firmware version: " + getFirmwareVersion()
	getZwaveClassVersionMap()
	getInputControlsForDevice()	

	
	state.firmwareVersion = getFirmwareVersion()
	state.ZwaveClassVersions = getZwaveClassVersionMap()
	state.parameterInputs = getInputControlsForDevice()

	getAllParameterValues()
	setIsDigitalEvent( false )
	
	getCentralSceneInfo()
	
	if (meterSupportedGet() ) 	runIn(3, refresh)
	else refresh()
	
	log.info "Completed initializing device ${device.displayName}."
}

/** Miscellaneous state and device data cleanup tool used during debugging and development
*/
void cleanup()
{
	device.removeDataValue("firmwareVersion")
	device.removeDataValue("hardwareVersion")
	device.removeDataValue("protocolVersion")
	device.removeDataValue("zwaveAssociationG1")
    device.removeDataValue("zwNodeInfo")
}

void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

////////////////////////////////////////////////////////////////////////
/////////////      Parameter Updating and Management      /////////////
////////////////////////////////////////////////////////////////////////

// Need to use ConcurrentHashMap to process received parameters to ensure there isn't a conflict in the update!
@Field static  ConcurrentHashMap<String, Map> allParameterDataStorage = new ConcurrentHashMap<String, Map>()

Map getPendingChangeMap()
{
	String key = "${device.getDeviceNetworkId()}:pendingChanges"
	if (!allParameterDataStorage.containsKey(key)) { 
		allParameterDataStorage.put(key, [:])
	}
	return  allParameterDataStorage.get(key)
}


Map getCurrentParameterValueMap()
{
	String key = "${device.getDeviceNetworkId()}:currentValues"
	if (!allParameterDataStorage.containsKey(key)) {
		Map parameterValues = [:]
		if (state.parameterValues) {
			state.parameterValues.each{ k, v -> parameterValues.put(k as Integer, v as Integer)}
		}
		allParameterDataStorage.put(key, parameterValues)
	}
	return  allParameterDataStorage.get(key)
}


void updated()
{
	if (txtEnable) log.info "Updating changed parameters . . ."
	if (logEnable) runIn(1800,logsOff)
	
	Map parameterValueMap = getCurrentParameterValueMap()
	Map pendingChangeMap = 	getPendingChangeMap()
		
	if (logEnable) log.debug "Updating paramameter values. Last retrieved values are: " + parameterValueMap

	// Collect the settings values from the input controls
	Map settingValueMap = [:]	
	getInputControlsForDevice().each { PKey , PData -> 
			Integer newValue = 0
			// if the setting returne an array, then its a bitmap control, and add together the values.
			if (settings[PData.name] instanceof ArrayList) 
			{
				settings[PData.name].each{ newValue += it as Integer }
			} else  {   
				newValue = settings[PData.name] as Integer  
			}
			settingValueMap.put(PKey as Integer, newValue)
		}
	if (logEnable) log.debug "Updating paramameter values. Settings control values are: " + settingValueMap

	// Find what change

	settingValueMap.each {k, v ->
		if (parameterValueMap?.get(k as Integer).is( null) ) 
		{
			if (logEnable) log.debug "parameterValueMap ${k} is null." + pendingChangeMap

			pendingChangeMap.put(k as Integer, v as Integer)
		} else {
		Boolean changedValue = (v as Integer) != (parameterValueMap.get(k as Integer) as Integer)
			if (changedValue) pendingChangeMap.put(k as Integer, v as Integer)
		}
	}
	
	if (logEnable) log.debug "Pending changes are: " + pendingChangeMap
	if (logEnable) log.debug "Pending changes in ConcurrentHashMap are: " + getPendingChangeMap()
	state.pendingChanges = pendingChangeMap

	processPendingChanges()
}

void processPendingChanges()
{
	// Hubitat state storage seems to convert integer keys to strings. Convert them back!
	Map parameterValueMap = getCurrentParameterValueMap()
	Map pendingChangeMap = getPendingChangeMap()
	Map parameterSizeMap = state.parameterInputs?.collectEntries{k, v -> [(k as Integer):(v.size as Short)]}

	if (logEnable) log.debug "Processing pending parameter changes.  Pending Change Data is: " + pendingChangeMap
	if (parameterValueMap.is( null)) 
		{
			log.warn "Error: tried to process parameter data, but missing state.parameterValues map!"
			return
		}
	pendingChangeMap?.each{ k, v ->
		Short PSize = parameterSizeMap?.get(k as Integer)
		if (logEnable) log.debug "Parameters for setParameter are: parameterNumber: ${k as Short}, size: ${PSize}, value: ${v}."
		setParameter((k as Short), (PSize as Short), (v as BigInteger) ) 

	}
}

//////////////////////////////////////////////////////////////////////
///////        Set, Get, and Process Parameter Values         ////////
////////////////////////////////////////////////////////////////////// 

void getParameterValue(parameterNumber)
{
	sendToDevice(secure(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber as Integer)))
}

void getAllParameterValues()
{
    List<hubitat.zwave.Command> cmds=[]	
	getInputControlsForDevice().each{k, v ->
			cmds << secure(zwave.configurationV1.configurationGet(parameterNumber: k as Integer))
		}
	if (cmds) {
		log.info "Sending commands to device ${device.displayName} to get all parameter values."
		sendToDevice(cmds)
	} else {
		log.info "No parameter values to retrieve for ${device.displayName}."
	}
}


void setParameter(Short parameterNumber = null, Short size = null, BigInteger value = null){
    if (parameterNumber.is( null ) || size.is( null ) || value.is( null ) ) {
		log.warn "Can't set parameter ${parameterNumber}, Incomplete parameter list supplied... syntax: setParameter(parameterNumber,size,value), received: setParameter(${parameterNumber}, ${size}, ${value})."
    } else {
		List<hubitat.zwave.Command> cmds = []
	    cmds << secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: parameterNumber, size: size))
	    cmds << secure(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber))
		sendToDevice(cmds)
    }
}


void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd)	{ processConfigurationReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd)	{ processConfigurationReport(cmd) }

void processConfigurationReport(cmd) { 
	Map parameterValueMap = getCurrentParameterValueMap()
	Map pendingChangeMap = getPendingChangeMap()
	Map parameterInputs = getInputControlsForDevice()
	
	parameterValueMap.put(cmd.parameterNumber as Integer, cmd.scaledConfigurationValue)
	pendingChangeMap.remove(cmd.parameterNumber as Integer)
	state.parameterValues = parameterValueMap
	state.pendingChanges = pendingChangeMap
	
	
	if (parameterInputs.get(cmd.parameterNumber as Integer)?.multiple as Boolean)
	{
		log.warn "Code incomplete - Parameter ${cmd.parameterNumber} is a bitmap type which is not fully processed!"
	} else {
		device.updateSetting("configParam${"${cmd.parameterNumber as Integer}".padLeft(3,"0")}" ,[value: (cmd.parameterNumber as Integer)])
	}

}

//////////////////////////////////////////////////////////////////////
//////                  Handle Supervision request            ///////
////////////////////////////////////////////////////////////////////// 
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    if (logEnable) log.debug "For ${device.displayName}, Supervision get: ${cmd}"
	
	Map parseMap = state.ZwaveClassVersions.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(parseMap)
	
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
    sendToDevice(secure((new hubitat.zwave.commands.supervisionv1.SupervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0))))
}
//////////////////////////////////////////////////////////////////////
//////                  Get Device Firmware Version            ///////
////////////////////////////////////////////////////////////////////// 
@Field static Semaphore firmwareMutex = new Semaphore(1)
@Field static  ConcurrentHashMap<String, Map> firmwareStore = new ConcurrentHashMap<String, Map>()


Map getFirmwareVersion()
{
	if (firmwareStore.containsKey("${device.getDeviceNetworkId()}")) {
		return firmwareStore.get("${device.getDeviceNetworkId()}")
	} else if (state.firmwareVersion) {
		log.info "For device ${device.displayName}, Loading firmware version from state.firmwareVersion which has value: ${state.firmwareVersion}."
		firmwareStore.put("${device.getDeviceNetworkId()}", [main: (state.firmwareVersion.main as Integer), sub: (state.firmwareVersion.sub as Integer)])
		return firmwareStore.get("${device.getDeviceNetworkId()}")
	} else {
		Boolean locked = firmwareMutex.tryAcquire(1, 20, TimeUnit.SECONDS )
		
		if (locked == false) {
			log.warn "Timed out getting lock to retrieve firmware version for device ${device.displayName}. Try restarting Hubitat."
		}		
		sendToDevice(secure(zwave.versionV1.versionGet()))
		
		// When the firmware report handler is done it will release firmwareMutex lock
		// Thus, this next acquire causes effects a wait 10 seconds until the report is received and processed
		Boolean locked2 = firmwareMutex.tryAcquire(1, 15, TimeUnit.SECONDS )
		if (locked2 == false) {
			log.warn "Possible processing error getting firmware report for device ${device.displayName}. Didn't get a response in time. Try restarting Hubitat."
		}
		firmwareMutex.release()
			
		return firmwareStore.get("${device.getDeviceNetworkId()}")
	}
}

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	if (logEnable) log.debug "For device ${device.displayName}, Network id: ${"${device.getDeviceNetworkId()}"}, Received firmware version V1 report: ${cmd}"
	if (firmwareStore.containsKey("${device.getDeviceNetworkId()}"))  {
		firmwareStore.remove("${device.getDeviceNetworkId()}")
	}
	firmwareStore.put("${device.getDeviceNetworkId()}", [main:cmd.applicationVersion as Integer, sub:cmd.applicationSubVersion as Integer] )
	log.info "Retrieved firmware update for device ${device.displayName}, firmware value is: ${firmwareStore.get("${device.getDeviceNetworkId()}")}."
	firmwareMutex.release()
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {processFirmwareReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {processFirmwareReport(cmd) }
void processFirmwareReport(cmd)
{
	if (logEnable) log.debug "For device ${device.displayName}, Network id: ${"${device.getDeviceNetworkId()}"}, Received firmware version report: ${cmd}"
	if (firmwareStore.containsKey("${device.getDeviceNetworkId()}"))  {
		firmwareStore.remove("${device.getDeviceNetworkId()}")
	}
	firmwareStore.put("${device.getDeviceNetworkId()}", [main:cmd.firmware0Version as Integer, sub:cmd.firmware0SubVersion as Integer] )
	log.info "Retrieved firmware update for device ${device.displayName}, firmware value is: ${firmwareStore.get("${device.getDeviceNetworkId()}")}."
	firmwareMutex.release()
}

//////////////////////////////////////////////////////////////////////
//////                  Z-Wave Helper Functions                ///////
//////   Format messages, Send to Device, secure Messages      ///////
////////////////////////////////////////////////////////////////////// 


void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {

	Map parseMap = state.ZwaveClassVersions?.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
        
	// The following lines should only impact firmware gets that occur before the classes are obtained.
	if (parseMap.is( null )) {
		parseMap = [:]
	}
	if (!parseMap.containsKey(0x86 as Integer)) {
		parseMap.put(0x86 as Integer,  1 as Integer)
	}
    
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(parseMap)
	
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void parse(String description) {
    // if (logEnable) log.debug "For ${device.displayName}, parse:${description}"
	Map parseMap = state.ZwaveClassVersions?.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
 
	// The following 2 lines should only impact firmware gets that occur before the classes are obtained.
	if (parseMap.is( null )) parseMap = [:]
	if(!parseMap.containsKey(0x86 as Integer)) parseMap.put(0x86 as Integer,  1 as Integer)
    
    hubitat.zwave.Command cmd = zwave.parse(description, parseMap)

    if (cmd) {
        zwaveEvent(cmd)
    }
}

void sendToDevice(List<hubitat.zwave.Command> cmds) { sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds), hubitat.device.Protocol.ZWAVE)) }
void sendToDevice(hubitat.zwave.Command cmd) { sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE)) }
void sendToDevice(String cmd) { sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE)) }

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) { return delayBetween(cmds.collect{ it }, delay) }

String secure(String cmd){ return zwaveSecureEncap(cmd) }
String secure(hubitat.zwave.Command cmd){ return zwaveSecureEncap(cmd) }

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "For ${device.displayName}, skipping command: ${cmd}"
}

//////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////
////////////        Learn the Z-Wave Class Versions Actually Implemented        ////////////////
/////////////////////////////////////////////////////////////////////////////////////////////// 

/*	
	0x20:2  (32) // Basic
	0x25:   (37)	//  Switch Binary
	0x26:	(38) // Switch Multilevel
	0x5B:3, (91) // Central Scene, Max is 3
	0x6C	(108)// supervision
	0x70:1, (112)// Configuration. Max is 2
	0x86:3, (134) // version V1, Max is 3
*/
@Field static  ConcurrentHashMap<String, Map> deviceClasses = new ConcurrentHashMap<String, Map>()

@Field static Semaphore classVersionMutex = new Semaphore(2)

String productKey()
{
	String manufacturer = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("manufacturer").toInteger(), 2)
	String deviceType = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceType").toInteger(), 2)
	String deviceID = 		hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceId").toInteger(), 2) 
	Map deviceFirmware = getFirmwareVersion() ?: [main:255 as Integer, sub:255 as Integer]
	if (logEnable) log.debug "deviceFirmware in product key function is: ${deviceFirmware}"
	Integer firmwareMain = 	 	deviceFirmware.get("main") as Integer
	Integer firmwareSub =  	 	deviceFirmware.get("sub") as Integer
	String key = "${manufacturer}:${deviceType}:${deviceID}:${firmwareMain}:${firmwareSub}"
	if (logEnable) log.debug "Product key in function productKey is set to: ${key}."
	return key
}

Map getClasses() { 
	String key = productKey()
	if (!deviceClasses.containsKey(key)) deviceClasses.put(key, [:])
	return deviceClasses.get(key)
}

Map   getZwaveClassVersionMap(){
	// All the inclusters supported by the device
	List<Integer> 	deviceInclusters = getDataValue("inClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Integer }
					deviceInclusters += getDataValue("secureInClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Integer }
					if (!deviceInclusters.contains(32)) deviceInclusters += 32
	
	if ( getClasses().is( null) || (getClasses().size()) == 0)
	{
		if (logEnable) log.debug "For device ${device.displayName}, product: ${productKey()}, initialize class versions using state.ZwaveClassVersions which is ${state.ZwaveClassVersions}"
		state.ZwaveClassVersions?.each{
			getClasses().put(it.key as Integer, it.value as Integer)
		}
	}
	if (logEnable) log.debug "Current classes for product key ${productKey()} are ${getClasses()}."
	
	List<Integer> neededClasses = []
	
	deviceInclusters.each { if (!getClasses().containsKey(it as Integer)) (neededClasses << it ) }
	
	neededClasses = neededClasses.unique().sort()
		
	if (neededClasses.size() == 0)
	{
		if (logEnable) log.debug "Already collected all classes for device ${device.displayName}, getClasses()?.size() is: ${getClasses()?.size()}, deviceInclusters.size() is ${deviceInclusters.size()}. Classes are: " + getClasses()
		return getClasses()
	} else {
		if (logEnable) log.debug "Retrieving class versions for device ${device.displayName}. Need: ${deviceInclusters.size()}, Have: ${getClasses()?.size()}, Missing Classes: ${neededClasses}."

		try
		{
			neededClasses.each {
				classVersionMutex.tryAcquire(1, 5, TimeUnit.SECONDS )

				if (logEnable) log.debug "Getting version information for Zwave command class: " + it
				sendToDevice(secure(zwave.versionV3.versionCommandClassGet(requestedCommandClass:it.toInteger())))
			}
			classVersionMutex.tryAcquire(2, 5, TimeUnit.SECONDS )
			if (logEnable) log.debug "Full set of command class versions for device ${device.displayName} is: " + getClasses()
			// classVersionMutex.release(2)
		}
		catch (Exception ex)
		{
			log.warn "An Error occurred when attempting to get input controls. Error: ${ex}."
		}
		finally
		{
			classVersionMutex.release(2)
			return getClasses()
		}
	}
}


// There are 3 versions of command class reports - could just include only the highest and let Groovy resolve!
void zwaveEvent(hubitat.zwave.commands.versionv1.VersionCommandClassReport cmd) { processVersionCommandClassReport (cmd) }
void zwaveEvent(hubitat.zwave.commands.versionv2.VersionCommandClassReport cmd) { processVersionCommandClassReport (cmd) }
void zwaveEvent(hubitat.zwave.commands.versionv3.VersionCommandClassReport cmd) { processVersionCommandClassReport (cmd) }

void processVersionCommandClassReport (cmd) {
	if (logEnable) log.debug "Device Mfr, Type, ID is: ${productKey()}"
	log.info "Initializing device ${device.displayName}, Adding command class info with class: ${cmd.requestedCommandClass}, version: ${cmd.commandClassVersion}"
	if ( getClasses().containsKey(cmd.requestedCommandClass as Integer)) getClasses().remove(cmd.requestedCommandClass as Integer)
	getClasses().put(cmd.requestedCommandClass as Integer, cmd.commandClassVersion as Integer)

	classVersionMutex.release(1)
}

///////////////////////////////////////////////////////////////////////////////////////////////
///////////////                  Central Scene Processing          ////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////

@Field static Map<String, String> CCButtonState = [:]

String getCCButtonState(Integer button) { 
 	String key = "${device.getDeviceNetworkId()}.Button.${button}"
	return CCButtonState.get(key)
}

String putCCButtonState(Integer button, String state)
{
 	String key = "${device.getDeviceNetworkId()}.Button.${button}"
	CCButtonState.put(key, state)
	return CCButtonState.get(key)
}


// The 'get" is the same in all versions of command class so just use the highest version supported!
void getCentralSceneInfo() {
	sendToDevice(secure( zwave.centralSceneV3.centralSceneSupportedGet() ))
}

// ====================
void zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneSupportedReport  cmd) {
    if(logEnable) log.debug "Central Scene V1 Supported Report Info ${cmd}"	
	sendEvent(name: "numberOfButtons", value: cmd.supportedScenes, isStateChange:true)
}

void zwaveEvent(hubitat.zwave.commands.centralscenev2.CentralSceneSupportedReport  cmd) {
    if(logEnable) log.debug "Central Scene V2 Supported Report Info ${cmd}"	
	sendEvent(name: "numberOfButtons", value: cmd.supportedScenes, isStateChange:true)
}

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneSupportedReport  cmd) {
    if(logEnable) log.debug "Central Scene V3 Supported Report Info ${cmd}"	
	sendEvent(name: "numberOfButtons", value: cmd.supportedScenes, isStateChange:true)
}

// This next 2 functions operates as a backup in case a release report was lost on the network
// It will force a release to be sent if there has been a hold event and then
// a release has not occurred within the central scene hold button refresh period.
// The central scene hold button refresh period is 200 mSec for old devices (state.slowRefresh == false), else it is 55 seconds.

void forceReleaseMessage(button)
{
	// only need to force a release hold if the button state is "held" when the timer expires
    log.warn "Central Scene Release message for button ${button} not received before timeout - Faking a release message!"
    sendEvent(name:"released", value:button , type:"digital", isStateChange:true, descriptionText:"${device.displayName} button ${button} forced release")
	putCCButtonState(button as Integer, "released")
}

void forceReleaseHold01(){ forceReleaseMessage(1)}
void forceReleaseHold02(){ forceReleaseMessage(2)}
void forceReleaseHold03(){ forceReleaseMessage(3)}
void forceReleaseHold04(){ forceReleaseMessage(4)}
void forceReleaseHold05(){ forceReleaseMessage(5)}
void forceReleaseHold06(){ forceReleaseMessage(6)}
void forceReleaseHold07(){ forceReleaseMessage(7)}
void forceReleaseHold08(){ forceReleaseMessage(8)}

void cancelLostReleaseTimer(button)
{
    try {
		switch (button)
	    {
			case 1: unschedule(forceReleaseHold01); break
			case 2: unschedule(forceReleaseHold02); break
			case 3: unschedule(forceReleaseHold03); break
			case 4: unschedule(forceReleaseHold04); break
			case 5: unschedule(forceReleaseHold05); break
			case 6: unschedule(forceReleaseHold06); break
			case 7: unschedule(forceReleaseHold07); break
			case 8: unschedule(forceReleaseHold08); break
			default : log.warn "Attempted to process lost release message code for button ${button}, but this is an error as code handles a maximum of 8 buttons."
	    }
    }
    catch (Exception ex) { log.debug "Exception in function cancelLostReleaseTimer: ${ex}"}
}

void setReleaseGuardTimer(button)
{
	// The code starts a release hold timer which will force a "release" to be issued
	// if a refresh isn't received within the slow refresh period!
	// If you get a refresh, executing again restarts the timer!
	// Timer is canceled by the cancelLostReleaseTimer if a "real" release is received.
	switch (button)
	{
		case 1: runIn(60, forceReleaseHold01); break
		case 2: runIn(60, forceReleaseHold02); break
		case 3: runIn(60, forceReleaseHold03); break
		case 4: runIn(60, forceReleaseHold04); break
		case 5: runIn(60, forceReleaseHold05); break
		case 6: runIn(60, forceReleaseHold06); break
		case 7: runIn(60, forceReleaseHold07); break
		case 8: runIn(60, forceReleaseHold08); break
		default : log.warn "Attempted to process lost release message code for button ${button}, but this is an error as code handles a maximum of 8 buttons."
	}
}

// ==================  End of code to help handle a missing "Released" messages =====================

int tapCount(attribute)
{
	// Converts a Central Scene command.keyAttributes value into a tap count
	// Returns negative numbers for special values of Released (-1) and Held (-2).
	switch (attribute)
	{
		case 0:
			return 1
			break
		case 1: // Released
			return -1
			break
		case 2: // Held
			return -2
			break
		default :  // For 3 or grater, subtract 1 from the attribute to get # of taps.
			return (attribute - 1)
			break
	}
}
void zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) { ProcessCCReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.centralscenev2.CentralSceneNotification cmd) { ProcessCCReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd) { ProcessCCReport(cmd) }

synchronized void ProcessCCReport(cmd) {

    Map event = [type:"physical", isStateChange:true]
	if(logEnable) log.debug "Received Central Scene Notification ${cmd}"
	
	def taps = tapCount(cmd.keyAttributes)
	
	if (getCCButtonState(cmd.sceneNumber as Integer) == "held")
	{
		// if currently holding, and receive anything except another hold or a release, 
		// then cancel any outstanding lost "release" message timer ...
		if ((taps != (-2)) && (taps != (-1))) 
		{
			// If you receive anything other than a release event, it means
			// that the prior release event from the device was lost, so Hubitat
			// is still in held state. Fix this by forcing a release message to be sent
			// before doing anything else
			forceReleaseMessage(cmd.sceneNumber)
		}
		// And cancel any timer that may be running for this held button.
		cancelLostReleaseTimer(cmd.sceneNumber)
	}

	switch (taps)
	{
		case -1:
			cancelLostReleaseTimer(cmd.sceneNumber)
			event.name = "released" 
			event.value = cmd.sceneNumber
			event.descriptionText="${device.displayName} button ${event.value} released"
			if (txtEnable) log.info event.descriptionText
			putCCButtonState(cmd.sceneNumber as Integer, event.name)
			sendEvent(event)
			break

		case -2:	
			event.name = "held" 
			event.value = cmd.sceneNumber

			if (getCCButtonState(cmd.sceneNumber as Integer) == "held")
			{
				// If currently holding and receive a refresh, don't send another hold message, Just report that still holding
				// Refresh received every 55 seconds if slowRefresh is enabled by the device, else its received every 200 mSeconds.
				if (txtEnable) log.info "Still Holding button ${cmd.sceneNumber}"
			} else {
				event.descriptionText="${device.displayName} button ${event.value} held"
				if (txtEnable) log.info event.descriptionText
				putCCButtonState(cmd.sceneNumber as Integer, event.name)
				sendEvent(event)
			}
			
			// The following starts a guard timer to force a release hold if you don't get a refresh within the slow refresh period!
			// If you get a refresh, executing again restarts the timer!
			setReleaseGuardTimer(cmd.sceneNumber)
			break
			
		case 1:
			event.name = "pushed"
			event.value= cmd.sceneNumber
			event.descriptionText="${device.displayName} button ${event.value} pushed"
			if (txtEnable) log.info event.descriptionText
			putCCButtonState(cmd.sceneNumber as Integer, event.name)
			sendEvent(event)
			break				
 
		case 2:
			event.name = "doubleTapped" 
			event.value=cmd.sceneNumber
			event.descriptionText="${device.displayName} button ${cmd.sceneNumber} doubleTapped"
			if (txtEnable) log.info event.descriptionText
			putCCButtonState(cmd.sceneNumber as Integer, event.name)
			sendEvent(event)			
			break
		
		case 3: // Key Pressed 3 times
		case 4: // Key Pressed 4 times
		case 5: // Key Pressed 5 times
			log.warn "Received and Ignored key tapped ${taps} times on button number ${cmd.sceneNumber}. Maximum button taps supported is 2"
			break
	}
}

//////////////////////////////////////////////////////////////////////
//////        Handle Meter Reports and Related Functions        ///////
////////////////////////////////////////////////////////////////////// 

Boolean meterSupportedGet()
{
	if (getZwaveClassVersionMap().containsKey(0x32) && (getZwaveClassVersionMap().get(0x32 as Integer) != 1) )
	{
		if (state.meterTypesSupported.is( null ))
		{
			if(logEnable) log.debug "Getting meter supported report information."
			sendToDevice(secure(zwave.meterV2.meterSupportedGet()))
		} else log.info "Supported meter types for ${device.displayName} are ${state.meterTypesSupported}."
		return true
	}
	else
	{
	if (logEnable) log.debug "Device ${device.displayName} does not support energy meter function."
	return false
	}
}
void meterReset() {
    if (txtEnable) log.info "${device.label?device.label:device.name}: Resetting energy statistics"
	sendToDevice(secure(zwave.meterV2.meterReset()))
}

void meterRefresh() {

	if (getZwaveClassVersionMap()?.get(50 as Integer).is( null ))
	{
		if (logEnable) log.debug "Device ${device.displayName} does not support metering. No Meter Refresh performed."
		return
	}

    if (txtEnable) log.info "Refreshing Energy Meter values for device: ${device.label?device.label:device.name}."
	
	if (getZwaveClassVersionMap()?.get(50 as Integer) == 1)
	{
		sendToDevice(secure(zwave.meterV1.meterGet()))
	} else {
		List<hubitat.zwave.Command> cmds = []
			if (state.meterTypesSupported.kWh ) cmds << secure(zwave.meterV3.meterGet(scale: 0))
			if (state.meterTypesSupported.kVAh ) cmds << secure(zwave.meterV3.meterGet(scale: 1))
			if (state.meterTypesSupported.Watts ) cmds << secure(zwave.meterV3.meterGet(scale: 2))
			if (state.meterTypesSupported.PulseCount ) cmds << secure(zwave.meterV3.meterGet(scale: 3))
			if (state.meterTypesSupported.Volts ) cmds << secure(zwave.meterV3.meterGet(scale: 4))
			if (state.meterTypesSupported.Amps ) cmds << secure(zwave.meterV3.meterGet(scale: 5))
			if (state.meterTypesSupported.PowerFactor ) cmds << secure(zwave.meterV3.meterGet(scale: 6))
		if (cmds) sendToDevice(cmds)	
	}
}

void zwaveEvent(hubitat.zwave.commands.meterv2.MeterSupportedReport cmd) { ProcessMeterSupportedReport (cmd) }
void zwaveEvent(hubitat.zwave.commands.meterv3.MeterSupportedReport cmd) { ProcessMeterSupportedReport (cmd) }
void zwaveEvent(hubitat.zwave.commands.meterv4.MeterSupportedReport cmd) { ProcessMeterSupportedReport (cmd) }
void zwaveEvent(hubitat.zwave.commands.meterv5.MeterSupportedReport cmd) { ProcessMeterSupportedReport (cmd) }

void ProcessMeterSupportedReport (cmd) {
	Map meterTypesSupported = [:]
    if (cmd.meterType.toInteger() == 1 )
    {
		meterTypesSupported = 
        [
			"kWh"   		: ( cmd.scaleSupported & 0b00000001 ) as Boolean ,
			"kVAh"   		: ( cmd.scaleSupported & 0b00000010 ) as Boolean ,
			"Watts"   		: ( cmd.scaleSupported & 0b00000100 ) as Boolean ,
			"PulseCount" 	: ( cmd.scaleSupported & 0b00001000 ) as Boolean ,
			"Volts"     	: ( cmd.scaleSupported & 0b00010000 ) as Boolean ,
			"Amps"     		: ( cmd.scaleSupported & 0b00100000 ) as Boolean ,
			"PowerFactor" 	: ( cmd.scaleSupported & 0b01000000 ) as Boolean 
		]

        if ( cmd.hasProperty("moreScaleType") )
		{
			meterTypesSuported.put("kWh",	( cmd.scaleSupportedBytes[1] & 0b00000001 ) as Boolean)
			meterTypesSuported.put("kVAh",	( cmd.scaleSupportedBytes[1] & 0b00000010 ) as Boolean)
		}
		state.put("meterTypesSupported", meterTypesSupported)
    } else  {
		log.warn "Received a meter support type of ${cmd.meterType}. Only value '1' (Electric meter) is supported"
	}
}

void zwaveEvent(hubitat.zwave.commands.meterv1.MeterReport cmd) { processMeterReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.meterv2.MeterReport cmd) { processMeterReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd) { processMeterReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.meterv4.MeterReport cmd) { processMeterReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.meterv5.MeterReport cmd) { processMeterReport(cmd) }

void processMeterReport( cmd) {
    if (logEnable) log.debug "Meter Report V3 for ${device.label?device.label:device.name} full contents are: ${cmd}"
	if (logEnable && cmd.hasProperty("rateType") && (cmd.rateType != 1)) log.warn "Unexpected Meter rateType received. Value is: ${cmd.rateType}."
	
	if (cmd.meterType == 1)
	{
		Boolean stateChange = true
		
		if (cmd.hasProperty("scaledPreviousMeterValue") && (cmd.scaledMeterValue != cmd.scaledPreviousMeterValue) )
		{
			stateChange = true
		} else {
			stateChange = false
		}
		
		switch (cmd.scale as Integer)
		{
		case 0: // kWh
			sendEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh", isStateChange: stateChange )
			if (txtEnable) log.info "${device.label?device.label:device.name}: Energy report received with value of ${cmd.scaledMeterValue} kWh"
			break
            
		case 1: // kVAh
			log.info "Received a meter report with unsupported type: kVAh. This is not a Hubitat Supported meter value."
            break
            
		case 2: // W
			sendEvent(name: "power", value: cmd.scaledMeterValue, unit: "W", isStateChange: stateChange )
			if (txtEnable) log.info "${device.label?device.label:device.name}: Power report received with value of ${cmd.scaledMeterValue} W"
			break	
            
		case 3: // Pulse Count
 			log.info "Received a meter report with unsupported type: Pulse Count. This is not a Hubitat Supported meter value."
           break
            
		case 4: // V
			sendEvent(name: "voltage", value: cmd.scaledMeterValue, unit: "V", isStateChange: stateChange )
			if (txtEnable) log.info "${device.label?device.label:device.name}: Voltage report received with value of ${cmd.scaledMeterValue} V"
			break
            
		case 5: // A
			sendEvent(name: "amperage", value: cmd.scaledMeterValue, unit: "A", isStateChange: stateChange )
			if (txtEnable) log.info "${device.label?device.label:device.name}: Amperage report received with value of ${cmd.scaledMeterValue} A"
			break
            
		case 6: // Power Factor
			log.info "Received a meter report with unsupported type: Power Factor. This is not a Hubitat Supported meter value."
            break
            
		case 7: // M.S.T. - More Scale Types
 			log.warn "Received a meter report with unsupported type: M.S.T."
           break
		}
	} else {
		log.warn "Received unexpected meter type for ${device.label?device.label:device.name}. Only type '1' (Electric Meter) is supported. Received type: ${cmd.meterType}"
	}
}
//////////////////////////////////////////////////////////////////////
//////        Handle Battery Reports and Device Functions        ///////
////////////////////////////////////////////////////////////////////// 
void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) 
{
	if (cmd.batteryLevel == 0xFF) 
	{
		log.warn "Device ${device.displayName}, low battery warning!"
		eventProcess ( name: "battery", value:1, unit: "%", descriptionText: "Device ${device.displayName}, Low Battery Alert. Change now!")
	
	} else {
		eventProcess ( name: "battery", value:cmd.batteryLevel, unit: "%", descriptionText: "Device ${device.displayName} battery level is ${cmd.batteryLevel}.")
	}
}

void batteryGet() {
	sendToDevice(secure(zwave.batteryV1.batteryGet()))
}

//////////////////////////////////////////////////////////////////////
//////        Handle Basic Reports and Device Functions        ///////
////////////////////////////////////////////////////////////////////// 

@Field static  ConcurrentHashMap<Long, Boolean> EventTypeIsDigital = new ConcurrentHashMap<Long, Boolean>()

Boolean isDigitalEvent() { return getDeviceMapByNetworkID().get("EventTypeIsDigital") as Boolean }
void setIsDigitalEvent(Boolean value) { getDeviceMapByNetworkID().put("EventTypeIsDigital", value as Boolean)}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (logEnable) log.debug "Received SwitchBinaryReport v1 containing: ${cmd}"
    if (device.hasAttribute("switch") || device.hasCapability("Switch")) 
	{
		if (logEnable) log.debug  "${device.displayName} Sending a switch ${(cmd.value ? "on" : "off")} event"

		eventProcess(	name: "switch", value: (cmd.value ? "on" : "off"), 
						descriptionText: "Device ${device.displayName} set to ${(cmd.value ? "on" : "off")}.", type: "physical" )
	}
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv2.SwitchBinaryReport cmd) {
    if (logEnable) log.debug "Received SwitchBinaryReport v1 containing: ${cmd}"
    if (device.hasAttribute("switch") || device.hasCapability("Switch")) 
	{
		if (logEnable) log.debug  "${device.displayName} Sending a switch ${(cmd.value ? "on" : "off")} event"
		eventProcess(	name: "switch", value: (cmd.value ? "on" : "off"), 
						descriptionText: "Device ${device.displayName} set to ${(cmd.value ? "on" : "off")}.", type: "physical" )
	}
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (logEnable) log.debug "Received BasicReport v1 containing: $cmd}"
    if (device.hasAttribute("switch") || device.hasCapability("Switch")) 
	{
		eventProcess(	name: "switch", value: (cmd.value ? "on" : "off"), 
						descriptionText: "Device ${device.displayName} set to ${cmd.value}.", 
						type: isDigitalEvent() ? "digital" : "physical" )
	}

    if ((device.hasAttribute("level")  || device.hasCapability("SwitchLevel") ) && (cmd.value != 0))
	{
		eventProcess( 	name: "level", value: cmd.value, 
						descriptionText: "Device ${device.displayName} level set to ${cmd.value}%", 
						type: isDigitalEvent() ? "digital" : "physical" )
	}
	setIsDigitalEvent( false )
}


void zwaveEvent(hubitat.zwave.commands.basicv2.BasicReport cmd) {

    if (logEnable) log.debug "Received BasicReport v2 containing: ${cmd}"
	if ((cmd.value != cmd.targetValue) && (cmd.duration == 0)) log.warn "Received a V2 Basic Report with mismatched value and targetValue and non-zero duration: ${cmd}."
    if (device.hasAttribute("switch") || device.hasCapability("Switch")) 
	{
		eventProcess(	name: "switch", value: (cmd.targetValue ? "on" : "off"), 
						descriptionText: "Device ${device.displayName} set to ${cmd.value}.", 
						type: isDigitalEvent() ? "digital" : "physical" )
	}

    if ((device.hasAttribute("level")  || device.hasCapability("SwitchLevel") ) && (cmd.targetValue != 0))
	{
		eventProcess( 	name: "level", value: cmd.targetValue, 
						descriptionText: "Device ${device.displayName} level set to ${cmd.value}%", 
						type: isDigitalEvent() ? "digital" : "physical" )
	}
	setIsDigitalEvent( false )
}


void zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd){ processMultilevelReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelReport cmd){ processMultilevelReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd){ processMultilevelReport(cmd) }

void processMultilevelReport(cmd)
{
	if (logEnable) log.debug "Received and processing multilevel report: ${cmd}."
	
    if (device.hasAttribute("switch") || device.hasCapability("Switch")) 
	{
		eventProcess(	name: "switch", value: (cmd.value ? "on" : "off"), 
						descriptionText: "Device ${device.displayName} set to ${(cmd.value ? "on" : "off")}", type: "physical" )
	}

    if ((device.hasAttribute("level")  || device.hasCapability("SwitchLevel") ) && (cmd.value != 0))
	{
		// z-wave level values only go to 99. Treat 99 as a 100%
		eventProcess( 	name: "level", value: (cmd.value == 99) ? 100: cmd.value, 
						descriptionText: "Device ${device.displayName} level set to ${cmd.value}%", type: "physical" )
	}
	setIsDigitalEvent( false )
}

void eventProcess(Map event) {
    if (device.currentValue(event.name).toString() != event.value.toString() ) {
	if (logEnable) log.debug "${device.displayName} has current value ${device.currentValue(event.name).toString()} and new event value ${event.value.toString()}."
        event.isStateChange=true
        sendEvent(event)
    }
}

void on() {
	List<hubitat.zwave.Command> cmds=[]	
	if (device.hasCapability("SwitchLevel")) {
		Integer levelValue = (device.currentValue("level") as Integer) ?: 99
		if (txtEnable) log.info "Turning device ${device.displayName} On to Level: ${levelValue}."

		cmds << secure(zwave.basicV1.basicSet(value: levelValue ))		
	} else {
		if (txtEnable) log.info "Turning device ${device.displayName} to: On."
		cmds << secure(zwave.basicV1.basicSet(value: 255 ))
	}
	
	if (confirmSend ) 
	{
		setIsDigitalEvent( true )
		cmds <<  secure(zwave.basicV1.basicGet())
	} else {
		sendEvent(name: "switch", value: "on", descriptionText: "Device ${device.displayName} turned on", 
			type: "digital", isStateChange: (device.currentValue("switch") == "on") ? false : true )
	}
	sendToDevice(cmds)
}

void off() {
	if (txtEnable) log.info "Turning device ${device.displayName} to: Off."
	
	sendToDevice (secure(zwave.basicV1.basicSet(value: 0 )))
	if (confirmSend ) 
	{
		setIsDigitalEvent( true )

		sendToDevice (secure(zwave.basicV1.basicGet()))
	} else {
		sendEvent(name: "switch", value: "off", descriptionText: "Device ${device.displayName} turned off", 
				type: "digital",  isStateChange: (device.currentValue("switch") == "off") ? false : true )
	}
}

void setLevel(level, duration = 0)
{
	log.warn "To-do: add confirmsend capability in setLevel to be consistent with on / off functions. Implement code in setlevel() function to turn on a non-dimming switch in response to a setlevel command"
	List<hubitat.zwave.Command> cmds=[]	
	setIsDigitalEvent( true )
	
	if (logEnable) log.debug "Executing function setlevel(level = ${level}, duration = ${duration})."
	if ( level < 0  ) level = 0
	if ( level > 100 ) level = 100
	if ( duration < 0 ) duration = 0
	if ( duration > 120 ) 
		{
			log.warn "For device ${device.displayName}, tried to set a dimming duration value greater than 120 seconds. To avoid excessive turn on / off delays, this driver only allows dimming duration values of up to 127."
			duration = 120
		}

	if (level == 0)
	{
		Boolean stateChange = ((device.currentValue("level") != 0) ? true : false)
		sendEvent(name: "switch", value: "off", descriptionText: "Device ${device.displayName} remains at off", type: "digital", isStateChange: stateChange )
		
			if (getZwaveClassVersionMap().get(38 as Integer) == 1)
			{
				cmds << secure(zwave.switchMultilevelV1.switchMultilevelSet(value: 0))
				log.warn "${device.displayName} does not support dimming duration setting command. Defaulting to dimming duration set by device parameters."
			} else {
				cmds << secure(zwave.switchMultilevelV2.switchMultilevelSet(value: 0, dimmingDuration: duration))
			}
	} else if (device.hasCapability("SwitchLevel")) {		
		if (getZwaveClassVersionMap().get(38 as Integer) < 1)
		{
			cmds << secure(zwave.switchMultilevelV1.switchMultilevelSet(value: ((level > 99) ? 99 : level)   ))
			log.warn "${device.displayName} does not support dimming duration setting command. Defaulting to dimming duration set by device parameters."
		} else {
			cmds << secure(zwave.switchMultilevelV2.switchMultilevelSet(value: ((level > 99) ? 99 : level), dimmingDuration: duration))
		}
	} else if (device.hasCapability("Switch")) {
		// To turn on a non-dimming switch in response to a setlevel command!"
		cmds << secure(zwave.basicV1.basicSet(value: ((level > 99) ? 99 : level) ))
	}
		
	if (logEnable) log.debug "Current switch value is ${device.currentValue("switch")}"
	if (device.currentValue("switch") == "off") 
	{	
		if (logEnable) log.debug "Turning switch on in setlevel function"
		sendEvent(name: "switch", value: "on", descriptionText: "Device ${device.displayName} turned on", type: "digital", isStateChange: true)
	}
	if (cmds)	sendToDevice(cmds)	
	sendEvent(name: "level", value: level, descriptionText: "Device ${device.displayName} set to ${level}%", type: "digital", isStateChange: true)
}
void startLevelChange(direction){
    Integer upDown = (direction == "down" ? 1 : 0)
    sendToDevice(secure(zwave.switchMultilevelV1.switchMultilevelStartLevelChange(upDown: upDown, ignoreStartLevel: 1, startLevel: 0)))
}

void stopLevelChange(){
	List<hubitat.zwave.Command> cmds = []
		cmds.add(secure(zwave.switchMultilevelV1.switchMultilevelStopLevelChange()))
		cmds.add(secure(zwave.basicV1.basicGet()))
	sendToDevice(cmds)
}
////////////////  Send Button Events Resulting from Capabilities Processing /////////////

void sendButtonEvent(action, button, type){
    String descriptionText = "${device.displayName} button ${button} was ${action} [${type}]"
    if (txtEnable) log.info descriptionText
    sendEvent(name:action, value:button, descriptionText:descriptionText, isStateChange:true, type:type)
}

void push(button){ sendButtonEvent("pushed", button, "digital") }
void hold(button){ sendButtonEvent("held", button, "digital") }
void release(button){ sendButtonEvent("released", button, "digital") }
void doubleTap(button){ sendButtonEvent("doubleTapped", button, "digital") }

//////////////////////////////////////////////////////////////////////
//////        Handle  Multilevel Sensor       ///////
//////////////////////////////////////////////////////////////////////

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelReport cmd)  { processNotificationSupportedReport(cmd) }
void processSensorMultilevelReport(cmd)
{
	log.debug "WARNING. MultiLevel Report code is currently incomplete. Sensor Multilevel Report is: " + cmd
	switch (cmd.sensorType)
	{
	case 0x01: // temperature
		if (scale == 0x00) // Celcius
		{
			if (logEnable) log.debug "For device ${device.displayName}, received outside temperature report in celsius: ${cmd}."

		} else if (scale == 0x01) // Fahrenheit
		{
			if (logEnable) log.debug "For device ${device.displayName}, received temperature report in fahrenheit: ${cmd}."
		}
		break
	case 0x03: // Illuminance
		if (scale == 0x00) // Percentage value
		{
			if (logEnable) log.debug "For device ${device.displayName}, received illuminance report in %: ${cmd}."
		} else if (scale == 0x01) // Lux
		{
			if (logEnable) log.debug "For device ${device.displayName}, received illuminance report in Lux: ${cmd}."
		}
		break	
	case 0x04: // Power
		if (scale == 0x00) // Watt(W)
		{
			if (logEnable) log.debug "For device ${device.displayName}, received power report in watts: ${cmd}."
		} else if (scale == 0x01) // BTU/h
		{
			if (logEnable) log.debug "For device ${device.displayName}, received power report in BTU/h: ${cmd}."
		}
		break		
	case 0x05: // Humidity
		if (scale == 0x00) // Percentage
		{
			if (logEnable) log.debug "For device ${device.displayName}, received Humidity report in percentage: ${cmd}."
		} else if (scale == 0x01) // Absolute (g/m3)
		{
			if (logEnable) log.debug "For device ${device.displayName}, received Humidity report in g/m3: ${cmd}."
		}
		break		
	case 0x0F: // voltage
		if (scale == 0x00) // Volt
		{
			if (logEnable) log.debug "For device ${device.displayName}, received voltage report in Volts: ${cmd}."
		} else if (scale == 0x01) // milliVolt
		{
			if (logEnable) log.debug "For device ${device.displayName}, received voltage report in milliVolts: ${cmd}."
		}
		break		
	case 0x40: // outside temperature
		if (scale == 0x00) // Celcius
		{
			if (logEnable) log.debug "For device ${device.displayName}, received outside temperature report in celsius: ${cmd}."
		} else if (scale == 0x01) // Fahrenheit
		{
			if (logEnable) log.debug "For device ${device.displayName}, received outside temperature report in fahrenheit: ${cmd}."
		}
		break
	default :
		break
	}
}

//////////////////////////////////////////////////////////////////////
//////        Handle Notifications        ///////
//////////////////////////////////////////////////////////////////////

void SetupNotifications()
{
	sendToDevice (secure(zwave.notificationV3.notificationSupportedGet()))

}
void getSupportedNotifications()
{
	sendToDevice (secure(zwave.notificationV3.notificationSupportedGet()))

}
// v1 and v2 are not implemented in Hubitat. 
void zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationSupportedReport cmd)  { processNotificationSupportedReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv4.NotificationSupportedReport cmd)  { processNotificationSupportedReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv5.NotificationSupportedReport cmd)  { processNotificationSupportedReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv6.NotificationSupportedReport cmd)  { processNotificationSupportedReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv7.NotificationSupportedReport cmd)  { processNotificationSupportedReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationSupportedReport cmd)  { processNotificationSupportedReport(cmd) }
void processNotificationSupportedReport (cmd)  
{ 
	if (logEnable) log.debug "Received Notification Supported Report: " + cmd 
		List<hubitat.zwave.Command> cmds=[]
			cmds << secure(zwave.notificationV3.eventSupportedGet(notificationType: 1)) // Smoke
			cmds << secure(zwave.notificationV3.eventSupportedGet(notificationType: 5)) // Water
			cmds << secure(zwave.notificationV3.eventSupportedGet(notificationType: 6)) // Access Control
			cmds << secure(zwave.notificationV3.eventSupportedGet(notificationType: 7)) // Burglar
		if (cmds) sendToDevice(cmds)
}

void zwaveEvent(hubitat.zwave.commands.notificationv3.EventSupportedReport cmd)  { processEventSupportedReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv4.EventSupportedReport cmd)  { processEventSupportedReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv5.EventSupportedReport cmd)  { processEventSupportedReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv6.EventSupportedReport cmd)  { processEventSupportedReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv7.EventSupportedReport cmd)  { processEventSupportedReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv8.EventSupportedReport cmd)  { processEventSupportedReport(cmd) }
void processEventSupportedReport (cmd)  
{ 
	if (logEnable) log.debug "Received Event Notification Supported Report: " + cmd 
}

void sendEventToAll(Map event)
{
	if (logEnable) log.debug "For device ${device.displayName}, processing event: " + event
	if (logEnable) log.debug  "Device has attribute: ${event.name}: " + device.hasAttribute(event.name as String)
	if (device.hasAttribute(event.name as String)) sendEvent(event)

	getChildDevices()?.each{ child ->
			if (logEnable) log.debug "For child device ${child.displayName}, processing event: " + event
			if (logEnable) log.debug  "Child device has attribute: ${event.name}: " + child.hasAttribute(event.name as String)
			if (child.hasAttribute(event.name as String)) sendEvent(event)
		}
}

// v1 and v2 are not implemented in Hubitat. 
void zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd)  { processNotificationReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv4.NotificationReport cmd)  { processNotificationReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv5.NotificationReport cmd)  { processNotificationReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv6.NotificationReport cmd)  { processNotificationReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv7.NotificationReport cmd)  { processNotificationReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd)  { processNotificationReport(cmd) }
void processNotificationReport(cmd)
{
	if (logEnable) log.debug "Processing Notification Report: " + cmd  
	List<Map> events = []
	switch (cmd.notificationType as Integer)
	{
		case 0x01: // Smoke Alarm
			events = processSmokeAlarmNotification(cmd)
			break 
		case 0x05: // Water Alarm
			events = processWaterAlarmNotification(cmd)
			break
		case 0x06: // Locks and entry
			events = processLockNotifications(cmd)
			break
		case 0x07: // Motion Detectors
			events = processHomeSecurityNotification(cmd)
			break
		default :
			log.warn "For device ${device.displayName}, Received a Notification Report with type: ${cmd.notificationType}, which is a type not processed by this driver."
	}
	
	events.each{ sendEventToAll(it) }
}

List<Map> processSmokeAlarmNotification(cmd)
{
	List<Map> events = []
	switch (cmd.event as Integer)
	{
		case 0x00: // Status Idle
			if (logEnable) log.debug "For device ${device.displayName}, Smoke Alarm Notification, Status Idle."
			events << [name:"smoke" , value:"clear", descriptionText:"Smoke detector status Idle."]
			break
		case 0x01: // Smoke detected (location provided)
			if (logEnable) log.debug "For device ${device.displayName}, Smoke Alarm Notification, Smoke detected (location provided)."
			events << [name:"smoke" , value:"detected", descriptionText:"Smoke detected (location provided)."]
			break
		case 0x02: // Smoke detected
			if (logEnable) log.debug "For device ${device.displayName}, Smoke Alarm Notification, Smoke detected."
			events << [name:"smoke" , value:"detected", descriptionText:"Smoke detected."]
			break
		case 0xFE: // Unknown Event / State
			if (logEnable) log.debug "For device ${device.displayName}, Smoke Alarm Notification, Unknown Event / State."
		default :
			log.warn "For device ${device.displayName}, Received a Notification Report with type: ${cmd.notificationType}, which is a type not processed by this driver."
	}
	return events
}

List<Map> processWaterAlarmNotification(cmd)
{
	List<Map> events = []
	switch (cmd.event as Integer)
	{
		case 0x00: // Status Idle
			if (logEnable) log.debug "For device ${device.displayName}, Water Alarm Notification, Status Idle."
			events << [name:"water" , value:"dry", descriptionText:"Water Alarm Notification, Status Dry."]
			break
		case 0x01: // Water leak detected (location provided)
			if (logEnable) log.debug "For device ${device.displayName}, Water Alarm Notification, Water leak detected (location provided)."
			events << [name:"water" , value:"wet", descriptionText:"Water leak detected (location provided)."]
			break
		case 0x02: // Water leak detected
			if (logEnable) log.debug "For device ${device.displayName}, Water Alarm Notification, Water leak detected."
			events << [name:"water" , value:"wet", descriptionText:"Water leak detected."]
			break
		case 0xFE: // Unknown Event / State
			if (logEnable) log.debug "For device ${device.displayName}, Water Alarm Notification, Unknown Event / State."
		default :
			log.warn "For device ${device.displayName}, Received a Notification Report with type: ${cmd.notificationType}, which is a type not processed by this driver."
	}
	return events
}

List<Map> processHomeSecurityNotification(cmd)
{
	List<Map> events = []
	switch (cmd.event as Integer)
	{
		case 0x00: // Status Idle
			if (logEnable) log.debug "For device ${device.displayName}, Home Security Notification, Status Idle."
			events << [name:"tamper" , value:"clear", descriptionText:"Tamper state cleared."]
			events << [name:"motion" , value:"inactive", descriptionText:"Motion Inactive."]
			break
		case 0x03: // Tampering, prouct cover removed
			if (logEnable) log.debug "For device ${device.displayName}, Home Security Notification, Tampering, prouct cover removed."
			events << [name:"tamper" , value:"detected", descriptionText:""]
			break
		case 0x04: // Tampering, invalid code
			if (logEnable) log.debug "For device ${device.displayName}, Home Security Notification, Tampering, invalid code."
			events << [name:"tamper" , value:"detected", descriptionText:"Tampering, invalid code."]
			break
		case 0x07: // Motion Detection (location provided)
			if (logEnable) log.debug "For device ${device.displayName}, Home Security Notification, Motion Detection (location provided)."
			events << [name:"motion" , value:"active", descriptionText:"Motion detected (location provided)."]
			break
		case 0x08: // Motion Detection
			if (logEnable) log.debug "For device ${device.displayName}, Home Security Notification, Motion Detection."
			events << [name:"motion" , value:"active", descriptionText:"Motion detected."]
			break
		case 0x09: // Tampering (Product Moved)
			if (logEnable) log.debug "For device ${device.displayName}, Home Security Notification, Tampering (Product Moved)."
			events << [name:"tamper" , value:"detected", descriptionText:"Tampering (Product Moved)."]
			break
		case 0xFE: // Unknown Event
		default :
			if (logEnable) log.debug "For device ${device.displayName}, Home Security Notification, Unknown Event."
			log.warn "For device ${device.displayName}, Received a Notification Report with type: ${cmd.notificationType}, and event: ${cmd.event}, which is a type not processed by this driver."
	}
	return events
}

//////////////////////////////////////////////////////////////////////
//////        Locks        ///////
//////////////////////////////////////////////////////////////////////
// import hubitat.zwave.commands.doorlockv1.*

void lockInitialize()
{
	sendToDevice (secure( zwave.userCodeV1.usersNumberGet() ))
}

void zwaveEvent(hubitat.zwave.commands.usercodev1.UserCodeReport cmd) { 
log.debug "For device ${device.displayName}, received User Code Report: " + cmd
}
void zwaveEvent(hubitat.zwave.commands.usercodev1.UsersNumberReport cmd) { 
	log.debug "For device ${device.displayName}, received Users Number Report: " + cmd
	sendEvent(name:"maxCodes", value: cmd.supportedUsers)
}

void lockrefresh()
{
	sendToDevice (secure(zwave.doorLockV1.doorLockOperationGet()))
}
void lock()
{
   sendToDevice (secure( zwave.doorLockV1.doorLockOperationSet(doorLockMode: 255) ))
}
void unlock()
{
   sendToDevice (secure( zwave.doorLockV1.doorLockOperationSet(doorLockMode: 0) )  )
}

void deleteCode(codeposition)
{
    log.debug "For device ${device.displayName}, deleting code at position ${codeNumber}."
	sendToDevice (secure( zwave.userCodeV1.userCodeSet(userIdentifier:codeNumber, userIdStatus:0) ))
	sendToDevice (secure( zwave.userCodeV1.userCodeGet(userIdentifier:codeNumber) ))
}

void getCodes()
{
	List<hubitat.zwave.Command> cmds=[]
		cmds << secure(zwave.userCodeV1.usersNumberGet())
		cmds << secure(zwave.userCodeV1.userCodeGet(userIdentifier: 1))
	sendToDevice(cmds)
}

void setCode(codeposition, pincode, name)
{

	String userCode = pincode as String
	log.debug "For device ${device.displayName}, setting code at position ${codeposition} to ${pincode}."
	assert (userCode instanceof String) 

	List<hubitat.zwave.Command> cmds=[]
	cmds << secure(zwave.userCodeV1.userCodeSet(userIdentifier:codeposition, userIdStatus:1, userCode:userCode ))
	cmds << secure(zwave.userCodeV1.userCodeGet(userIdentifier:codeposition))
	sendToDevice(cmds)
}

void setCodeLength(pincodelength)
{
log.warn "Code Length Should be set from Z-Wave Parameter Settings controls."
}

void processLockNotifications(cmd)
{
	if (logEnable) log.debug "Received Door Lock Operation Report: " + cmd  

	Map lockEvent = [name: "lock"]
	switch (cmd.event as Integer)
	{
	case 0x01:
		lockEvent.value = "locked"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Manual Lock Operation."
		break	
	case 0x02:
		lockEvent.value = "unlock"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Manual UnLock Operation."
		break	
	case 0x03:
		lockEvent.value = "locked"	
		lockEvent.descriptionText = "Lock ${device.displayName} - RF Lock Operation."
		break	
	case 0x04:
		lockEvent.value = "unlocked"	
		lockEvent.descriptionText = "Lock ${device.displayName} - RF UnLock Operation."
		break	
	case 0x05:
		lockEvent.value = "locked"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Keypad Lock Operation."
		break	
	case 0x06:
		lockEvent.value = "unlocked"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Keypad UnLock Operation."
		break	
	case 0x07:
		lockEvent.value = "unknown"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Manual Not Fully Locked Operation."
		break	
	case 0x08:
		lockEvent.value = "unknown"	
		lockEvent.descriptionText = "Lock ${device.displayName} - RF Not Fully Locked Operation."
		break	
	case 0x09:
		lockEvent.value = "locked"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Auto Lock Lock Operation."
		break	
	case 0x0A:
		lockEvent.value = "unknown"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Auto Lock Not Fully Locked Operation."
		break	
	case 0x0B:
		lockEvent.value = "unknown"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Lock Jammed."
		break	
	default :
		log.warn "Lock ${device.displayName} - An Undefined Event Occurred."
		lockEvent.descriptionText = "Lock ${device.displayName} - An Undefined Event Occurred."
		break
	} 
	sendEvent(lockEvent)	
}

// This is another form of door lock reporting. I believe its obsolete, but I've included it just in case some lock requires it.  
// Modes 2-4 are not implemented by Hubitat.
void zwaveEvent(hubitat.zwave.commands.doorlockv1.DoorLockOperationReport cmd)  { processDoorLockMode }
void processDoorLockMode (cmd)
{
	if (logEnable) log.debug "Received Door Lock Operation Report: " + cmd  

	Map lockEvent = [name: "lock"]
	switch (cmd.doorLockMode as Integer)
	{
	case 0x00:	
		lockEvent.value = "unlocked"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Door Unsecured."
		break
	case 0x01:
		lockEvent.value = "unlocked with timeout"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Door Unsecured with timeout."
		break
	case 0x10:
		lockEvent.value = "unlocked"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Door Unsecured for inside Door Handles."
		break
	case 0x11:
		lockEvent.value = "unlocked with timeout"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Door Unsecured for inside Door Handles with timeout."
		break
	case 0x20:
		lockEvent.value = "unlocked"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Door Unsecured for outside Door Handles."
		break
	case 0x21:
		lockEvent.value = "unlocked with timeout"	
		lockEvent.descriptionText = "Lock ${device.displayName} - Door Unsecured for outside Door Handles with timeout."
		break		
	case 0xFF:
		lockEvent.value = "locked"
		lockEvent.descriptionText = "Lock ${device.displayName} - Door Secured."
		break
	case 0xFE:
	default :
		lockEvent.value = "unknown"
		lockEvent.descriptionText = "Lock ${device.displayName} had an unknown event."
		break
	}
	sendEvent(lockEvent)
}

