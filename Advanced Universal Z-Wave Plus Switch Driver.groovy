import java.util.concurrent.*;
import groovy.transform.Field

@Field static String driverVersion = "0.0.7"
@Field static Boolean deleteAndResetStateData = false
@Field static defaultParseMap = [
	0x20:2, // Basic Set
	0x25:2, // Switch Binary
	0x26:3, // Switch MultiLevel
	0x31:11,// Sensor MultiLevel
	0x32:5, // Meter
	0x5B:1,	// Central Scene
	0x60:4,	// MultiChannel
	0x62:1,	// Door Lock
	0x63:1,	// User Code
	0x6C:1,	// Supervision
	0x71:8, // Notification
	0x80:1, // Battery
	0x86:3,	// Version
	0x98:1,	// Security
	0x9B:2	// Configuration
	]

// Endpoint 0 is not specified! Only detail for endpoints >= 1
// May eventually just read this from opensmarthouse!
@Field static endPointMap = [
	// Inovelli LZW36
		[manufacturer:0x031E, deviceType:0x000E, deviceId:0x0001,  
			ep:[
				1:[name:"Fan", driver:"Generic Component Fan Control"], 
				2:[name:"Light", driver:"Generic Component Dimmer"]
				]],
				
	// Zooz Zen16
		[manufacturer:0x027A, deviceType:0xA000, deviceId:0xA00A,  
			ep:[
				1:[name:"Relay 1", driver:"Generic Component Switch"], 
				2:[name:"Relay 2", driver:"Generic Component Switch"], 
				3:[name:"Relay 3", driver:"Generic Component Switch"] 
				]
		],	
		
	// Zooz Zen20
		[manufacturer:0x027A, deviceType:0xA000, deviceId:0xA004,  
			ep:[
				1:[name:"Outlet 1", driver:"Generic Component Switch"], 
				2:[name:"Outlet 2", driver:"Generic Component Switch"], 
				3:[name:"Outlet 3", driver:"Generic Component Switch"], 
				4:[name:"Outlet 4", driver:"Generic Component Switch"], 
				5:[name:"Outlet 5", driver:"Generic Component Switch"], 
				6:[name:"Outlet 6", driver:"Generic Component Switch"] 
				]
		],
		
	// Zooz Zen 25
		[manufacturer:0x027A, deviceType:40960, deviceId:40963,  
			ep:[
				1:[name:"Left Switch", driver:"Generic Component Metering Switch"], 
				2:[name:"Right Switch", driver:"Generic Component Metering Switch"], 
				3:[name:"USB", driver:"Generic Component Switch"]
				]
		],	
		
	// Zooz Zen 30
		[manufacturer:0x027A, deviceType:0xA000, deviceId:0xA008,  
			ep:[
				1:[name:"Left Switch", driver:"Generic Component Switch"]
				]
		]				
]

metadata {
	definition (name: "[Beta 0.0.7] Advanced Zwave Plus Metering Switch",namespace: "jvm", author: "jvm") {
			// capability "Configuration" // Does the same as Initialize, so don't show the separate control!
			capability "Initialize"
			capability "Refresh"
		
 		// For switches and dimmers!
			capability "Switch"	
		    // capability "SwitchLevel"
			
		// Does anybody really use the "Change Level" controls? If so, uncomment it!
		//	capability "ChangeLevel"
			
		//	These are generally harmless to keep in, even for non-metering devices, so generally leave uncommented	

            capability "EnergyMeter"
			capability "PowerMeter"
			capability "VoltageMeasurement"
			command "meterRefresh"


		// Central Scene functions. Include the "commands" if you want to generate central scene actions from the web interface. If they are not included, central scene will still be generated from the device.
			capability "PushableButton"
			command "push", ["NUMBER"]	
			capability "HoldableButton"
			command "hold", ["NUMBER"]
			capability "ReleasableButton"
			command "release", ["NUMBER"]
			capability "DoubleTapableButton"
			command "doubleTap", ["NUMBER"]
			
		//	capability "Battery"
		// 	command "batteryGet"
			
		// capability "Lock"
		// capability "Lock Codes"
		// command "lockrefresh"

		command "getAllParameterValues"
		// command "getSupportedNotifications"
		// capability "Sensor"
		// capability "MotionSensor"
		// capability "ContactSensor"
		// capability "RelativeHumidityMeasurement"
		// capability "SmokeDetector"
		// capability "TamperAlert"
		// capability "TemperatureMeasurement"
		// capability "WaterSensor"


		/** The "ResetDriverStateData" command deletes all state data stored by the driver. 
		This is for debugging purposes. In production code, it can be removed.
		*/
		command "ResetDriverStateData"
		command "getFirmwareVersion"
		command "deleteChildDevices"
		command "createChildDevices"

		// setParameter is a generalized function for setting parameters.	
			command "setParameter",[
					[name:"parameterNumber",type:"NUMBER", description:"Parameter Number", constraints:["NUMBER"]],
					[name:"size",type:"NUMBER", description:"Parameter Size", constraints:["NUMBER"]],
					[name:"value",type:"NUMBER", description:"Parameter Value", constraints:["NUMBER"]]
					]			
    }
    preferences 
	{
		input title:"Device Lookup", description: "<p> <a href=\"https://www.opensmarthouse.org/zwavedatabase/\" target=\"_blank\">Click Here to get your Device Info.</a> </p>", type: "paragraph", element: "Device Information"
		
        input name: "advancedEnable", type: "bool", title: "Enable Advanced Configuration", defaultValue: true
        
        if (advancedEnable)
        {
			input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
			input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: true
			
			List<Integer> keyset = state.parameterInputs?.keySet().collect{ it as Integer}
			def sortedKeys = keyset?.sort()
			sortedKeys.each{ input state.parameterInputs["${it}"] }
			
			// state.parameterInputs?.each { input it.value }
        }
    }
}
void ResetDriverStateData()
{
	state.clear()
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
	def endpointInfo = endPointMap.find{ (it.manufacturer == mfr) && (it.deviceType == type) && (it.deviceId 	== id)}
	if (logEnable) log.debug "Endpoint Info is: ${endpointInfo}"	
	
	endpointInfo.ep.each{k, v ->
		def childNetworkID = "${device.deviceNetworkId}-ep00${k}"
		def cd = getChildDevice(childNetworkID)
		if (!cd) {
			log.info "creating child device: ${childNetworkID}"
			addChildDevice("hubitat", v.driver, childNetworkID, [name: "${device.displayName}-${v.name}", isComponent: false])
		} else {
			log.info "Child device: ${childNetworkID} already exist. No need to re-create."
		}
	}	
}

//////////////////////////////////////////////////////////////////////////////////////////////////////
////////  Utilities for storing data in a global Hash Map shared across driver instances  ////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////

// The following ConcurrentHashMap is used to store data with a device key consisting of manufacturer/ device ID / device type / firmware main / firmware sub
@Field static  ConcurrentHashMap<String, Map> deviceSpecificData = new ConcurrentHashMap<String, Map>()

/**
getDeviceMapForProduct returns the main Map data structure containing all the data gathered for the particular Product and firmware version. The data may have been gathered by any of the drivers!
*/
Map getDeviceMapForProduct()
{
	String manufacturer = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("manufacturer").toInteger(), 2)
	String deviceType = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceType").toInteger(), 2)
	String deviceID = 		hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceId").toInteger(), 2) 
	Map deviceFirmware = getFirmwareVersion()
	Integer firmwareMain = 	 	deviceFirmware.main as Integer
	Integer firmwareSub =  	 	deviceFirmware.sub as Integer

	String key = "${manufacturer}:${deviceType}:${deviceID}:${firmwareMain}:${firmwareSub}"

	return deviceSpecificData.get(key, [:])
}

Map getDeviceMapByNetworkID()
{
	String netID = device.getDeviceNetworkId()

	return deviceSpecificData.get(netID, [:])
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

Map getInputControlsForDevice()
{
	Map inputControls = getDeviceMapForProduct().get("inputControls", [:])
	if (inputControls?.size() > 0) 
	{
		if (state.parameterInputs.is(null)) state.parameterInputs = inputControls
		return inputControls
	} else if (state.parameterInputs) {
		if (logEnable) log.debug "Loading Input Controls from saved state data."
		state.parameterInputs.each{ k, v -> inputControls.put( k as Integer, v) }
		return inputControls
		
	} else {
		if (logEnable) log.debug "Retrieving input control date from opensmarthouse.org for device ${device.displayName}."

		try {
			List parameterData = getOpenSmartHouseData()
			inputControls = createInputControls(allParameterData)
			getDeviceMapForProduct().put("inputControls", inputControls)
		} catch (Exception ex) {
			log.warn "Device ${device.displayName}: An Error occurred when attempting to get input controls. Error: ${ex}."
		} finally {
			state.parameterInputs = inputControls
			return inputControls
		}
	}
}

List getOpenSmartHouseData()
{
	if (txtEnable) log.info "Getting data from OpenSmartHouse for device ${device.displayName}."
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
		log.warn "Device ${device.displayName}: No database entry found for manufacturer: ${manufacturer}, deviceType: ${deviceType}, deviceID: ${deviceID}"
		return null
	}
    
    String queryByDatabaseID= "http://www.opensmarthouse.org/dmxConnect/api/zwavedatabase/device/read.php?device_id=${mydevice.id}"    
    
	httpGet([uri:queryByDatabaseID]) { resp-> 
				allParameterData = getDeviceMapForProduct().get("opensmarthouse", resp.data).parameters
			}
	return allParameterData
}

Map createInputControls(data)
{
	Map inputControls = [:]

	if (logEnable) log.debug "Device ${device.displayName}: Creating Input Controls"
	
	data.each
	{
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
			if (deviceOptions)
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

//////////////////////////////////////////////////////////////////////
//////      Initialization, update, and uninstall sequence          ///////
////////////////////////////////////////////////////////////////////// 
void refresh(cd = null ) 
{
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	
	if (txtEnable) "Refreshing device ${targetDevice.displayName} status .."
    sendToDevice(secure(zwave.basicV1.basicGet(), ep))
	
	if (getZwaveClassVersionMap().containsKey(0x32)) { meterRefresh() }
}

void installed() { initialize() }

void configure() { initialize() }

Integer getMajorVersion(String semVer)
{
	def a = semVer?.split("\\.")
	if (a.is( null ) ) {
		return -1 
	} else {
		return a[0] as Integer 
	}
}

void cleanState()
{
	if (deleteAndResetStateData) state.clear()
	if (getMajorVersion(state.driverVersionNum) != getMajorVersion(driverVersion)) state.clear() 

	if (state.driverVersionNum == "0.0.1") state.clean()
	if (state.driverVersionNum == "0.0.2") state.clean()
	if (state.driverVersionNum == "0.0.3") state.clean()
	if (state.driverVersionNum == "0.0.4") state.clean()
	if (state.driverVersionNum == "0.0.5") state.clean()
	if (state.driverVersionNum == "0.0.6") state.clean()	
}

void initialize()
{
	log.info "Initializing device ${device.displayName}."
	
	cleanState()

	state.driverVersionNum = driverVersion
	state.firmwareVersion = getFirmwareVersion()
	if (txtEnable) log.info "Device ${device.displayName} has firmware version: " + state.firmwareVersion

	state.ZwaveClassVersions = getZwaveClassVersionMap()
	state.parameterInputs = getInputControlsForDevice()	

	// Create a link with that can be clicked on to get product instrucions!
	def opensmarthouseData = getDeviceMapForProduct()?.get("opensmarthouse")
    if (opensmarthouseData) 
    {
        Integer databaseRecordID = opensmarthouseData?.get("database_id")
		state.ProductInstructions = "<p> Get Detailed information on your device by clicking this link <a href=\"https://www.opensmarthouse.org/zwavedatabase/${databaseRecordID}/\" target=\"_blank\">Your device info</a> </p>"
	    state.device = "${opensmarthouseData.manufacturer?.label}: ${opensmarthouseData?.label}, ${opensmarthouseData?.description}."
    } else if (state.ProductInstructions.is( null ) ) {
	    state.ProductInstructions = "<p> Get Detailed information on your device by clicking this link <a href=\"https://www.opensmarthouse.org/zwavedatabase/\" target=\"_blank\">Your device info</a> </p>"
	}
    
	getAllParameterValues()
	
	setIsDigitalEvent( false )
	
	if (getZwaveClassVersionMap().containsKey(0x5B)) getCentralSceneInfo()
	
	if (getZwaveClassVersionMap().containsKey(0x32)) state.metersSupported = getSupportedMeters()  
 	if (getZwaveClassVersionMap().containsKey(0x60)) state.endpointData = getEndpointData()
	
	refresh()

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
    log.warn "Device ${device.displayName}: debug logging disabled..."
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
	return allParameterDataStorage.get(key, [:])
}

Map getCurrentParameterValueMap()
{
	String key = "${device.getDeviceNetworkId()}:currentValues"
	/* if (!allParameterDataStorage.containsKey(key)) {
		Map parameterValues = [:]
		if (state.parameterValues) {
			state.parameterValues.each{ k, v -> 
					if ( k && v ) parameterValues.put(k as Integer, v as Integer)
					}
		}
		if (parameterValues.size() > 0) allParameterDataStorage.put(key, parameterValues)
	}
	*/
	return  allParameterDataStorage.get(key, [:])
}

///////////////////////////////////////////////////////////////////////////////////////
///////      Handle Update(), and Set, Get, and Process Parameter Values       ////////
/////////////////////////////////////////////////////////////////////////////////////// 
void updated()
{
	if (txtEnable) log.info "Device ${device.displayName}: Updating changed parameters (if any) . . ."
	if (logEnable) runIn(1800,logsOff)
	
	Map parameterValueMap = getCurrentParameterValueMap()
	Map pendingChangeMap = 	getPendingChangeMap()
		
	if (logEnable) log.debug "Device ${device.displayName}: Updating paramameter values. Last retrieved values are: " + parameterValueMap
	// state.parameterValues = parameterValueMap
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
			settingValueMap.put(PKey as Integer, newValue as Integer)
		}
	if (logEnable) log.debug "Device ${device.displayName}: Updating parameter values. Settings control values are: " + settingValueMap

	// Find what changed
	settingValueMap.each {k, v ->
	if (v.is(null)) return
		if (parameterValueMap?.get(k as Integer).is( null) ) 
		{
			// Parameter doesn't exist in previoulsy retrieved set, so add it to the pending change map.
			if (logEnable) log.debug "Device ${device.displayName}: Failed to find an entry for parameter ${k} in previously retrieved value set."

			pendingChangeMap.put(k as Integer, v as Integer)
		} else {
			Boolean changedValue = (v as Integer) != (parameterValueMap.get(k as Integer) as Integer)
			if (logEnable) log.debug "For parameter ${k}, prior input control value is ${v}, and value has been changed = ${changedValue}, and last retrieved value is ${(parameterValueMap.get(k as Integer) as Integer)}."
			if (changedValue) pendingChangeMap.put(k as Integer, v as Integer)
		}
	}
	
	// Get the parameter value if it is currently null, regardless of change state!
	List<hubitat.zwave.Command> cmds = []
	settingValueMap.each {k, v ->
		if (v.is(null)) {
		log.debug "Setting for parameter ${k} is null, so getting its value!"
			cmds << secure(zwave.configurationV1.configurationGet(parameterNumber: k))
		}
	}
	if (cmds) sendToDevice(cmds)
	
	if (logEnable) log.debug "Device ${device.displayName}: Pending changes are: ${pendingChangeMap}, Pending changes in ConcurrentHashMap are: ${getPendingChangeMap()}"
	state.pendingChanges = pendingChangeMap
	
	processPendingChanges()
}

void processPendingChanges()
{
	// Hubitat state storage seems to convert integer keys to strings. Convert them back!
	Map parameterValueMap = getCurrentParameterValueMap()
	Map pendingChangeMap = getPendingChangeMap()
	Map parameterSizeMap = state.parameterInputs?.collectEntries{k, v -> [(k as Integer):(v.size as Short)]}

	if (logEnable) log.debug "Device ${device.displayName}: Processing pending parameter changes.  Pending Change Data is: " + pendingChangeMap
	if (parameterValueMap.is( null)) 
		{
			log.warn "Device ${device.displayName}: Error: tried to process parameter data, but missing state.parameterValues map!"
			return
		}

	// Set/get each  parameter that has changed
	List<hubitat.zwave.Command> cmds = []
	pendingChangeMap?.each{ k, v ->
		Short PSize = parameterSizeMap?.get(k as Integer)
		if (logEnable) log.debug "Device ${device.displayName}: Parameters for setParameter are: parameterNumber: ${k as Short}, size: ${PSize}, value: ${v}."
		// setParameter((k as Short), (PSize as Short), (v as BigInteger) ) 
		cmds << secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: (v as BigInteger), parameterNumber: (k as Short), size: (PSize as Short)))
	    cmds << secure(zwave.configurationV1.configurationGet(parameterNumber: (k as Short)))
	}
	if (cmds) sendToDevice(cmds)
}


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
		if (txtEnable) log.info "Device ${device.displayName}: Sending commands to get all parameter values."
		sendToDevice(cmds)
	} else {
		if (txtEnable) log.info "Device ${device.displayName}: No parameter values to retrieve."
	}
}


void setParameter(Short parameterNumber = null, Short size = null, BigInteger value = null){
    if (parameterNumber.is( null ) || size.is( null ) || value.is( null ) ) {
		log.warn "Device ${device.displayName}: Can't set parameter ${parameterNumber}, Incomplete parameter list supplied... syntax: setParameter(parameterNumber,size,value), received: setParameter(${parameterNumber}, ${size}, ${value})."
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
	if (logEnable) log.debug "Device ${device.displayName}: Received a ConfigurationReport: ${cmd}"

	Map parameterValueMap = getCurrentParameterValueMap()
	Map pendingChangeMap = getPendingChangeMap()
	Map parameterInputs = getInputControlsForDevice()
	
	// There's a bug in the handling of cmd.scaledConfiguraitonValue. Single-byte return values
	// get turned into negative numbers, so don't use cmd.scaledConfiguraiton if cmd.size == 1!
	Integer newValue = (cmd.size == 1) ? cmd.configurationValue[0] : cmd.scaledConfigurationValue
	if (newValue < 0 ) log.warn "Device ${device.displayName}: Received a negative scaledConfigurationValue value in processConfigurationReport for report ${cmd}."

	log.info "Device ${device.displayName}: Received device report - Configuration parameter ${cmd.parameterNumber} has value ${newValue}."
	parameterValueMap.put(cmd.parameterNumber as Integer, newValue)
	pendingChangeMap.remove(cmd.parameterNumber as Integer)
	state.parameterValues = parameterValueMap
	state.pendingChanges = pendingChangeMap
	
	if (parameterInputs.get(cmd.parameterNumber as Integer)?.multiple as Boolean)
	{
		log.warn "Device ${device.displayName}: Code incomplete - Parameter ${cmd.parameterNumber} is a bitmap type which is not fully processed!"
	} else {
		String configName = "configParam${"${cmd.parameterNumber as Integer}".padLeft(3,"0")}"
		if (logEnable) log.debug "Device ${device.displayName}: updating settings data for ${configName} to new value ${newValue}!"
		device.updateSetting("${configName}", newValue)
	}
}

//////////////////////////////////////////////////////////////////////
//////                  Handle Supervision request            ///////
////////////////////////////////////////////////////////////////////// 
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    if (logEnable) log.debug "Device ${device.displayName}: Supervision get: ${cmd}"
	
	Map parseMap = state.ZwaveClassVersions?.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
	// Map parseMap = getCommandClassVersions()
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
	} else if ((state.firmwareVersion) && ((state.firmwareVersion?.main as Integer) != 255) ) {
		if (logEnable) log.debug "Device ${device.displayName}: Loading firmware version from state.firmwareVersion which has value: ${state.firmwareVersion}."
		return firmwareStore.get("${device.getDeviceNetworkId()}", [main: (state.firmwareVersion.main as Integer), sub: (state.firmwareVersion.sub as Integer)])
	} else {
		// Lock a Semaphore which gets released by the handling function after it receives a response from the device
		Boolean waitingForDeviceResponse = firmwareMutex.tryAcquire(1, 10, TimeUnit.SECONDS )
		
		if (waitingForDeviceResponse == false) {
			log.warn "Device ${device.displayName}, Timed out getting lock to retrieve firmware version for device ${device.displayName}. Try restarting Hubitat."
		}		
		sendToDevice(secure(zwave.versionV1.versionGet()))
		
		// When the firmware report handler is done it will release firmwareMutex lock
		// Thus, once code can acquire the Semaphore again, it knows the device responded and the firmware handler has completed
		Boolean deviceResponded = firmwareMutex.tryAcquire(1, 10, TimeUnit.SECONDS )
		if (deviceResponded == false ) {
			log.warn "Device ${device.displayName}: Possible processing error getting firmware report for device ${device.displayName}. Didn't get a response in time. Try restarting Hubitat."
		}
		firmwareMutex.release()
		
		if (firmwareStore.containsKey("${device.getDeviceNetworkId()}")) { 		
			return firmwareStore.get("${device.getDeviceNetworkId()}")
		}
	}
	
	log.warn "Device ${device.displayName}: Failed to get firmware from device, using a defaul value of main:255, sub:255. The driver will try again next time firmware version is requested."
	return [main:255, sub:255]
}

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	if (logEnable) log.debug "Device ${device.displayName}: Network id: ${"${device.getDeviceNetworkId()}"}, Received firmware version V1 report: ${cmd}"
	if (firmwareStore.containsKey("${device.getDeviceNetworkId()}"))  {
		firmwareStore.remove("${device.getDeviceNetworkId()}")
	}
	firmwareStore.put("${device.getDeviceNetworkId()}", [main:cmd.applicationVersion as Integer, sub:cmd.applicationSubVersion as Integer] )
	if (txtEnable) log.info "Device ${device.displayName}: firmware version is: ${firmwareStore.get("${device.getDeviceNetworkId()}")}."
	
	// The calling function getFirmwareVersion() is waiting for this handler to finish, which is indicated by releasing a Semaphore.
	firmwareMutex.release()
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {processFirmwareReport(cmd) }
void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {processFirmwareReport(cmd) }
void processFirmwareReport(cmd)
{
	if (firmwareStore.containsKey("${device.getDeviceNetworkId()}"))  {
		firmwareStore.remove("${device.getDeviceNetworkId()}")
	}
	firmwareStore.put("${device.getDeviceNetworkId()}", [main:cmd.firmware0Version as Integer, sub:cmd.firmware0SubVersion as Integer] )
	if (txtEnable) log.info "Device ${device.displayName}: firmware version is: ${firmwareStore.get("${device.getDeviceNetworkId()}")}."
	firmwareMutex.release()
}

//////////////////////////////////////////////////////////////////////
//////                  Z-Wave Helper Functions                ///////
//////   Format messages, Send to Device, secure Messages      ///////
////////////////////////////////////////////////////////////////////// 



void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) 
{
	Map parseMap = state.ZwaveClassVersions?.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
        
	// The following lines should only impact firmware gets that occur before the classes are obtained.
	if (parseMap.is( null )) { parseMap = [:] }
	if (!parseMap.containsKey(0x86 as Integer)) { parseMap.put(0x86 as Integer,  1 as Integer) }
	
	hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(parseMap)
	
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) { processMultichannelEncapsulatedCommand( cmd) }
void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCmdEncap cmd) { processMultichannelEncapsulatedCommand( cmd) }
void processMultichannelEncapsulatedCommand( cmd)
{
	Map parseMap = state.ZwaveClassVersions?.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
        
	// The following lines should only impact firmware gets that occur before the classes are obtained.
	if (parseMap.is( null )) { parseMap = [:] }
	if (!parseMap.containsKey(0x86 as Integer)) { parseMap.put(0x86 as Integer,  1 as Integer) }
	
    def encapsulatedCommand = cmd.encapsulatedCommand(parseMap)

    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
    }
}

void parse(String description) {
	Map parseMap = state.ZwaveClassVersions?.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
	// The following 2 lines should only impact firmware gets that occur before the classes are obtained.
	if (parseMap.is( null )) parseMap = [:]
	if (!parseMap.containsKey(0x86 as Integer)) parseMap.put(0x86 as Integer, 1 as Integer)
	if (!parseMap.containsKey(0x32 as Integer)) parseMap.put(0x32 as Integer, 1 as Integer)
	if (logEnable) log.debug "Parsing description string: ${description}"
		hubitat.zwave.Command cmd = zwave.parse(description, parseMap)

    if (cmd) { zwaveEvent(cmd) }
}

void sendToDevice(List<hubitat.zwave.Command> cmds) { sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds), hubitat.device.Protocol.ZWAVE)) }
void sendToDevice(hubitat.zwave.Command cmd) { sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE)) }
void sendToDevice(String cmd) { sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE)) }

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) { return delayBetween(cmds.collect{ it }, delay) }

String secure(String cmd, ep = null){ 
	if (ep) {
		return zwaveSecureEncap(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(cmd))
	} else {
		return zwaveSecureEncap(cmd) 
	}
}

String secure(hubitat.zwave.Command cmd, ep = null){ 
	if (ep) {
		return zwaveSecureEncap(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(cmd))
	} else {
		return zwaveSecureEncap(cmd) 
	}
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "For ${device.displayName}, skipping command: ${cmd}"
}


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
	Integer firmwareMain = 	 	deviceFirmware.get("main") as Integer
	Integer firmwareSub =  	 	deviceFirmware.get("sub") as Integer
	String key = "${manufacturer}:${deviceType}:${deviceID}:${firmwareMain}:${firmwareSub}"
	// if (logEnable) log.debug "Product key in function productKey manufacturer:deviceType:deviceID:firmwareMain:firmwareSub is set to: ${key}."
	return key
}

Map getClasses() { 
	String key = productKey()
	return deviceClasses.get(key, [:])
}

Map   getZwaveClassVersionMap(){
	// All the inclusters supported by the device
	List<Integer> 	deviceInclusters = getDataValue("inClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Integer }
					deviceInclusters += getDataValue("secureInClusters")?.split(",").collect{ hexStrToUnsignedInt(it) as Integer }
					if (!deviceInclusters.contains(32)) deviceInclusters += 32
	
	if ( getClasses().is( null) || (getClasses().size()) == 0)
	{
		if (logEnable) log.debug "Device ${device.displayName}: product: ${productKey()}, initialize class versions using state.ZwaveClassVersions which is ${state.ZwaveClassVersions}"
		state.ZwaveClassVersions?.each{
			getClasses().put(it.key as Integer, it.value as Integer)
		}
	}
	if (logEnable) log.warn "Version 2.2.4 of Hubitat has an error in processing the central scene and meter report. Forcing them to version 1."
	if (deviceInclusters.contains(0x5B)) getClasses().put(0x5B as Integer, 1 as Integer)
	if (deviceInclusters.contains(0x32)) getClasses().put(0x32 as Integer, 1 as Integer)	
	
	if (logEnable) log.debug "Device ${device.displayName}: Current classes for product key ${productKey()} are ${getClasses()}."
	
	List<Integer> neededClasses = []
	
	deviceInclusters.each { if (!getClasses().containsKey(it as Integer)) (neededClasses << it ) }
	
	neededClasses = neededClasses.unique().sort()
		
	if (neededClasses.size() == 0)
	{
		if (logEnable) log.debug "Device ${device.displayName}: Already collected all command classes. Classes are: " + getClasses()
		return getClasses()
	} else {
		if (logEnable) log.debug "Device ${device.displayName}: Retrieving command class versions. Missing Class count: ${neededClasses}."

		try {
			neededClasses.each {
				classVersionMutex.tryAcquire(1, 5, TimeUnit.SECONDS )
				sendToDevice(secure(zwave.versionV3.versionCommandClassGet(requestedCommandClass:it.toInteger())))
			}
			classVersionMutex.tryAcquire(2, 5, TimeUnit.SECONDS )
			if (logEnable) log.debug "Device ${device.displayName}: Tried to get information for Zwave command class: ${it}. Stored command classes are now: " + getClasses()
			// classVersionMutex.release(2)
		} catch (Exception ex) {
			log.warn "Device ${device.displayName}: An Error occurred when attempting to get input controls. Error: ${ex}."
		} finally {
			classVersionMutex.release(2)
			return getClasses()
		}
	}
}


// There are 3 versions of command class reports - could just include only the highest and let Groovy resolve!
void zwaveEvent(hubitat.zwave.commands.versionv1.VersionCommandClassReport cmd, ep = null ) { processVersionCommandClassReport (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.versionv2.VersionCommandClassReport cmd, ep = null ) { processVersionCommandClassReport (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.versionv3.VersionCommandClassReport cmd, ep = null ) { processVersionCommandClassReport (cmd, ep) }

void processVersionCommandClassReport (cmd, ep) {
	if (ep) log.warn "Received an endpoint identifier in VersionCommandClassReport. Alert developer. This isn't fully handled yet!"
	
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

String putCCButtonState(Integer button, String state) {
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
	sendEvent(name: "numberOfButtons", value: cmd.supportedScenes)
}

void zwaveEvent(hubitat.zwave.commands.centralscenev2.CentralSceneSupportedReport  cmd) {
    if(logEnable) log.debug "Central Scene V2 Supported Report Info ${cmd}"	
	sendEvent(name: "numberOfButtons", value: cmd.supportedScenes)
}

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneSupportedReport  cmd) {
    if(logEnable) log.debug "Central Scene V3 Supported Report Info ${cmd}"	
	sendEvent(name: "numberOfButtons", value: cmd.supportedScenes)
}

// This next 2 functions operates as a backup in case a release report was lost on the network
// It will force a release to be sent if there has been a hold event and then
// a release has not occurred within the central scene hold button refresh period.
// The central scene hold button refresh period is 200 mSec for old devices (state.slowRefresh == false), else it is 55 seconds.

void forceReleaseMessage(button)
{
	// only need to force a release hold if the button state is "held" when the timer expires
    log.warn "Device ${device.displayName}: Central Scene Release message for button ${button} not received before timeout - Faking a release message!"
    sendEvent(name:"released", value:button , type:"digital", descriptionText:"${device.displayName} button ${button} forced release")
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
			default : log.warn "Device ${device.displayName}: Attempted to process lost release message code for button ${button}, but this is an error as code handles a maximum of 8 buttons."
	    }
    }
    catch (Exception ex) { log.debug "Device ${device.displayName}: Exception in function cancelLostReleaseTimer: ${ex}"}
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
		default : log.warn "Device ${device.displayName}: Attempted to process lost release message code for button ${button}, but this is an error as code handles a maximum of 8 buttons."
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

void ProcessCCReport(cmd) 
{
    Map event = [type:"physical", isStateChange:true]
	
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
				if (logEnable) log.debug "Still Holding button ${cmd.sceneNumber}"
			} else {
				event.descriptionText="${device.displayName} button ${event.value} held"
				if (logEnable) log.debug event.descriptionText
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
			log.warn "Device ${device.displayName}: Received and Ignored key tapped ${taps} times on button number ${cmd.sceneNumber}. Maximum button taps supported is 2"
			break
	}
}

//////////////////////////////////////////////////////////////////////
//////        Handle Meter Reports and Related Functions        ///////
////////////////////////////////////////////////////////////////////// 
@Field static Semaphore meterReportMutex = new Semaphore(1)
@Field static  ConcurrentHashMap<String, Map> meterTypesSupported = new ConcurrentHashMap<String, Map>()


Map getMeters() { 
	String key = productKey()
	return meterTypesSupported.get(key, [:])
}


Map getSupportedMeters()
{
	Boolean locked = false
	Boolean processedReport = false

	if (getZwaveClassVersionMap().get(0x32 as Integer) != 1)
	{
		if (state.metersSupported.is( null ) || (state.metersSupported.size() == 0))
		{
			locked = meterReportMutex.tryAcquire(1, 10, TimeUnit.SECONDS)
				sendToDevice(secure(zwave.meterV2.meterSupportedGet()))
			processedReport = meterReportMutex.tryAcquire(1, 10, TimeUnit.SECONDS)
			if (! processedReport) log.warn "Device ${device.displayName}: Timeout Error - Failed to process Meter Get Report within 10 seconds of request to device."
			meterReportMutex.release(1)
		} else if (txtEnable) log.info "Device ${device.displayName}: Supported meter types are ${state.metersSupported}."
		return getMeters()
	} else {
	if (logEnable) log.debug "Device ${device.displayName} supports obsolete Z-Wave Meter Command Class Version 1 which has  not been implemented!. For support, enter a report on driver github site."
	return [:]
	}
}
void meterReset() {
    if (txtEnable) log.info "Device ${device.displayName}: Resetting energy statistics"
	sendToDevice(secure(zwave.meterV2.meterReset()))
}

void meterRefresh()
{
	if (getZwaveClassVersionMap()?.get(0x60 as Integer))
	{	
		log.warn "meterRefresh() code is incomplete. Should check each endpont and individually refresh!"
	}
	meterRefreshByTarget()
}

void meterRefreshByTarget( ep = null ) 
{
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Integer) == ep}
	} else { targetDevice = device }

	if (getZwaveClassVersionMap()?.get(0x32 as Integer).is( null ))
	{
		log.warn "Called meterRefresh() for a Device ${targetDevice.displayName} that does not support metering. No Meter Refresh performed."
		return
	}

    if (txtEnable) log.info "Refreshing Energy Meter values for device: ${targetDevice.displayName}."
	
	if (getZwaveClassVersionMap()?.get(0x32 as Integer) == 1)
	{
		if (logEnable) log.debug "Performing a Version 1 Meter Get for device ${targetDevice.displayName}."
		sendToDevice(secure(zwave.meterV1.meterGet(), ep))
	} else {
		if (logEnable) log.debug "Performing a Version 2+ Meter Get for device ${targetDevice.displayName}. state.metersSupported: " + state.metersSupported
		List<hubitat.zwave.Command> cmds = []
			if (state.metersSupported?.kWh as Boolean) cmds << secure(zwave.meterV3.meterGet(scale: 0), ep)
			if (state.metersSupported?.kVAh as Boolean ) cmds << secure(zwave.meterV3.meterGet(scale: 1), ep)
			if (state.metersSupported?.Watts as Boolean ) cmds << secure(zwave.meterV3.meterGet(scale: 2), ep)
			if (state.metersSupported?.PulseCount as Boolean ) cmds << secure(zwave.meterV3.meterGet(scale: 3), ep)
			if (state.metersSupported?.Volts as Boolean ) cmds << secure(zwave.meterV3.meterGet(scale: 4), ep)
			if (state.metersSupported?.Amps as Boolean) cmds << secure(zwave.meterV3.meterGet(scale: 5), ep)
			if (state.metersSupported?.PowerFactor as Boolean ) cmds << secure(zwave.meterV3.meterGet(scale: 6), ep)
		if (cmds) sendToDevice(cmds)	
	}
}

void zwaveEvent(hubitat.zwave.commands.meterv2.MeterSupportedReport cmd, ep = null ) { ProcessMeterSupportedReport (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv3.MeterSupportedReport cmd, ep = null ) { ProcessMeterSupportedReport (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv4.MeterSupportedReport cmd, ep = null ) { ProcessMeterSupportedReport (cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv5.MeterSupportedReport cmd, ep = null ) { ProcessMeterSupportedReport (cmd, ep) }
void ProcessMeterSupportedReport (cmd, ep) {
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Integer) == ep}
	} else { targetDevice = device }	

	Map meterMap = getMeters()
    if (cmd.meterType.toInteger() == 1 )
    {
		meterMap.put("kWh"   		, ( cmd.scaleSupported & 0b00000001 ) as Boolean )
		meterMap.put("kVAh"   		, ( cmd.scaleSupported & 0b00000010 ) as Boolean )
		meterMap.put("Watts"   		, ( cmd.scaleSupported & 0b00000100 ) as Boolean )
		meterMap.put("PulseCount" 	, ( cmd.scaleSupported & 0b00001000 ) as Boolean )
		meterMap.put("Volts"     	, ( cmd.scaleSupported & 0b00010000 ) as Boolean )
		meterMap.put("Amps"     	, ( cmd.scaleSupported & 0b00100000 ) as Boolean )
		meterMap.put("PowerFactor" 	, ( cmd.scaleSupported & 0b01000000 ) as Boolean )
		
        if ( cmd.hasProperty("moreScaleType") )
		{
			meterMap.put("kVar"		, ( cmd.scaleSupportedBytes[1] & 0b00000001 ) as Boolean)
			meterMap.put("kVarh"	, ( cmd.scaleSupportedBytes[1] & 0b00000010 ) as Boolean)
		}
    } else  {
		log.warn "Device ${targetDevice.displayName}: Received a meter support type of ${cmd.meterType} which is not processed by this code."
	}
	meterReportMutex.release(1)
}

void zwaveEvent(hubitat.zwave.commands.meterv1.MeterReport cmd, ep = null ) { processMeterReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv2.MeterReport cmd, ep = null ) { processMeterReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd, ep = null ) { processMeterReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv4.MeterReport cmd, ep = null ) { processMeterReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.meterv5.MeterReport cmd, ep = null ) { processMeterReport(cmd, ep) }

void processMeterReport( cmd, ep ) 
{
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Integer) == ep}
	} else { targetDevice = device }	
	
	if (logEnable && cmd.hasProperty("rateType") && (cmd.rateType != 1)) log.warn "Device ${targetDevice.displayName}: Unexpected Meter rateType received. Value is: ${cmd.rateType}."
	
	if (cmd.meterType == 1)
	{
		try {
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
				log.warn "Device ${targetDevice.displayName}: Received a meter report with unsupported type: More Scale Types. Report was: ${cmd}."
			   break
			}
		} catch (ex)
		{
			log.debug "Error ${ex} processing report ${cmd}. Report is of class ${cmd.class}."
		}
	} else {
		log.warn "Device ${targetDevice.displayName}: Received unexpected meter type for ${targetDevice.displayName}. Only type '1' (Electric Meter) is supported. Received type: ${cmd.meterType}"
	}
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
//////        Child Device Methods        ///////
////////////////////////////////////////////////////////////////////// 

Integer getEndpoint(com.hubitat.app.DeviceWrapper device)
{
	return device.deviceNetworkId.split("-ep")[-1] as Integer
}

void componentRefresh(cd){
    if (logEnable) log.info "received refresh request from component ${cd.displayName}"
	refresh(cd)
}

void componentOn(cd){
    if (logEnable) log.info "received on request from ${cd.displayName}"
    // getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])
	on(cd)
}

void componentOff(cd){
    if (logEnable) log.info "received off request from ${cd.displayName}"
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
//////        Handle Basic Reports and Device Functions        ///////
////////////////////////////////////////////////////////////////////// 

// @Field static  ConcurrentHashMap<Long, Boolean> EventTypeIsDigital = new ConcurrentHashMap<Long, Boolean>()

Boolean isDigitalEvent() { return getDeviceMapByNetworkID().get("EventTypeIsDigital") as Boolean }
void setIsDigitalEvent(Boolean value) { getDeviceMapByNetworkID().put("EventTypeIsDigital", value as Boolean)}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, ep = null) 			{ processDeviceReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.switchbinaryv2.SwitchBinaryReport cmd, ep = null)  			{ processDeviceReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, ep = null) 							{ processDeviceReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.basicv2.BasicReport cmd, ep = null) 							{ processDeviceReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd, ep = null)	{ processDeviceReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelReport cmd, ep = null)	{ processDeviceReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd, ep = null)	{ processDeviceReport(cmd, ep) }
void processDeviceReport(cmd,  ep)
{
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Integer) == ep}
	} else { targetDevice = device }	

	Boolean isSwitch = targetDevice.hasAttribute("switch") || targetDevice.hasCapability("Switch") || targetDevice.hasCapability("Bulb")  \
					|| targetDevice.hasCapability("Light") || targetDevice.hasCapability("Outlet")  || targetDevice.hasCapability("RelaySwitch")
	Boolean isDimmer = targetDevice.hasAttribute("level")  || targetDevice.hasCapability("SwitchLevel")
	Boolean turnedOn = false
	Integer newLevel = 0

	if (cmd.hasProperty("duration")) //  Consider duration and target, but only when process a BasicReport Version 2
	{
		turnedOn = ((cmd.duration as Integer == 0 ) && ( cmd.value as Integer != 0 )) || ((cmd.duration as Integer != 0 ) && (cmd.targetValue as Integer != 0 ))
		newLevel = ((cmd.duration as Integer == 0 ) ? cmd.value : cmd.targetValue ) as Integer
	} else {
		turnedOn = (cmd.value as Integer) > (0 as Integer)
		newLevel = cmd.value as Integer
	}
	
	String priorSwitchState = targetDevice.currentValue("switch")
	String newSwitchState = (turnedOn ? "on" : "off")
	Integer priorLevel = targetDevice.currentValue("level")
	Integer targetLevel = ((newLevel == 99) ? 100 : newLevel)
	
    if (isSwitch && (priorSwitchState != newSwitchState))
	{
		targetDevice.sendEvent(	name: "switch", value: newSwitchState, 
						descriptionText: "Device ${targetDevice.displayName} set to ${newSwitchState}.", 
						type: isDigitalEvent() ? "digital" : "physical" )
		if (txtEnable) log.info "Device ${targetDevice.displayName} set to ${newSwitchState}."
	}
	if (isDimmer && turnedOn) // If it was turned off, that would be handle in the "isSwitch" block above.
	{
		// Don't send the event if the level doesn't change except if transitioning from off to on, always send!
		if ((priorLevel != targetLevel) || (priorSwitchState != newSwitchSTate))
		{
			targetDevice.sendEvent( 	name: "level", value: (newLevel == 99) ? 100 : newLevel, 
					descriptionText: "Device ${targetDevice.displayName} level set to ${targetLevel}%", 
					type: isDigitalEvent() ? "digital" : "physical" )
			if (txtEnable) log.info "Device ${targetDevice.displayName} level set to ${targetLevel}%"		
		}
	}

	if (!isSwitch && !isDimmer) log.warn "For device ${targetDevice.displayName} receive a BasicReport which wasn't processed. Need to check BasicReport handling code." + cmd
	setIsDigitalEvent( false )
}

void on(cd = null ) {
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null

	if (targetDevice.hasCapability("SwitchLevel")) {
		Integer level = (targetDevice.currentValue("level") as Integer) ?: 100
		if (txtEnable) log.info "Device ${targetDevice.displayName}: Turned On at Level: ${level}."

		sendToDevice(secure(zwave.basicV1.basicSet(value: ((level > 99) ? 99 : level)), ep)	)	
		targetDevice.sendEvent(name: "switch", value: "on", descriptionText: "Device ${targetDevice.displayName} turned on", type: "digital")
		targetDevice.sendEvent(name: "level", value: level, descriptionText: "Device ${targetDevice.displayName} set to level ${level}%", type: "digital")

	} else {
		if (txtEnable) log.info "Device ${targetDevice.displayName}: Turning to: On."
		sendToDevice(secure(zwave.basicV1.basicSet(value: 255 ), ep))
		targetDevice.sendEvent(name: "switch", value: "on", descriptionText: "Device ${targetDevice.displayName} turned on", type: "digital")
	}
}

void off(cd = null ) {
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null

	if (txtEnable) log.info "Device ${targetDevice.displayName}: Turning device to: Off."
	
	sendToDevice (secure(zwave.basicV1.basicSet(value: 0 ), ep))

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
			sendToDevice(secure(zwave.switchMultilevelV1.switchMultilevelSet(value: 0), ep))
			log.warn "${targetDevice.displayName} does not support dimming duration setting command. Defaulting to dimming duration set by device parameters."
		} else {
			sendToDevice(secure(zwave.switchMultilevelV2.switchMultilevelSet(value: 0, dimmingDuration: duration), ep))
		}
		targetDevice.sendEvent(name: "switch", value: "off", descriptionText: "Device ${targetDevice.displayName} remains at off", type: "digital")
		// Return after sending the switch off
		return
	}
	// If turning the device on, then ...
	if (targetDevice.hasCapability("SwitchLevel")) {		// Device is a dimmer!
		if (getZwaveClassVersionMap().get(38 as Integer) < 2)
		{
			sendToDevice(secure(zwave.switchMultilevelV1.switchMultilevelSet(value: ((level > 99) ? 99 : level)   ), ep))
			if (logEnable) log.warn "${targetDevice.displayName} does not support dimming duration setting command. Defaulting to dimming duration set by device parameters."
		} else {
			sendToDevice(secure(zwave.switchMultilevelV2.switchMultilevelSet(value: ((level > 99) ? 99 : level), dimmingDuration: duration), ep))
		}
	} else if (targetDevice.hasCapability("Switch")) {   // Device is a non-dimming switch, but can still send the Z-wave level value
		// To turn on a non-dimming switch in response to a setlevel command!"
		sendToDevice(secure(zwave.basicV1.basicSet(value: ((level > 99) ? 99 : level) )), ep)
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
    sendToDevice(secure(zwave.switchMultilevelV1.switchMultilevelStartLevelChange(upDown: upDown, ignoreStartLevel: 1, startLevel: 0), ep))
}

void stopLevelChange(cd = null ){
	def targetDevice = (cd ? cd : device)
	def ep = cd ? (cd.deviceNetworkId.split("-ep")[-1] as Integer) : null
	List<hubitat.zwave.Command> cmds = []
		cmds.add(secure(zwave.switchMultilevelV1.switchMultilevelStopLevelChange(), ep))
		cmds.add(secure(zwave.basicV1.basicGet(), ep))
	sendToDevice(cmds)
}

////////////////  Send Button Events Resulting from Capabilities Processing /////////////
// According to Z-Wave standards, central scene is reported for the parent, not the individual child devices.
// Thus, don't worry about endpoints!

void sendButtonEvent(action, button, type){
    String descriptionText = "${device.displayName} button ${button} was ${action} [${type}]"
    if (txtEnable) log.info descriptionText
    sendEvent(name:action, value:button, descriptionText:descriptionText, type:type)
}

void push(button)		{ sendButtonEvent("pushed", 		button, "digital") }
void hold(button)		{ sendButtonEvent("held", 			button, "digital") }
void release(button)	{ sendButtonEvent("released", 		button, "digital") }
void doubleTap(button)	{ sendButtonEvent("doubleTapped", 	button, "digital") }

//////////////////////////////////////////////////////////////////////
//////        Handle  Multilevel Sensor       ///////
//////////////////////////////////////////////////////////////////////

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv1.SensorMultilevelReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv2.SensorMultilevelReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv3.SensorMultilevelReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv4.SensorMultilevelReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv6.SensorMultilevelReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv7.SensorMultilevelReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv8.SensorMultilevelReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv9.SensorMultilevelReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv10.SensorMultilevelReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep) }
void processSensorMultilevelReport(cmd, ep)
{
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Integer) == ep}
	} else { targetDevice = device }	
	
	if (ep) log.warn "Device ${device.displayName}: Endpoint handling in report type SensorMultilevelReport is incomplete! Alert developer."

	log.warn "Device ${device.displayName}: WARNING. MultiLevel Report code is currently incomplete. Sensor Multilevel Report is: " + cmd
	switch (cmd.sensorType)
	{
	case 0x01: // temperature
		if (scale == 0x00) // Celcius
		{
			if (logEnable) log.debug "Device ${device.displayName}: received outside temperature report in celsius: ${cmd}."

		} else if (scale == 0x01) // Fahrenheit
		{
			if (logEnable) log.debug "Device ${device.displayName}: received temperature report in fahrenheit: ${cmd}."
		}
		break
	case 0x03: // Illuminance
		if (scale == 0x00) // Percentage value
		{
			if (logEnable) log.debug "Device ${device.displayName}: received illuminance report in %: ${cmd}."
		} else if (scale == 0x01) // Lux
		{
			if (logEnable) log.debug "Device ${device.displayName}: received illuminance report in Lux: ${cmd}."
		}
		break	
	case 0x04: // Power
		if (scale == 0x00) // Watt(W)
		{
			if (logEnable) log.debug "Device ${device.displayName}: received power report in watts: ${cmd}."
		} else if (scale == 0x01) // BTU/h
		{
			if (logEnable) log.debug "Device ${device.displayName}: received power report in BTU/h: ${cmd}."
		}
		break		
	case 0x05: // Humidity
		if (scale == 0x00) // Percentage
		{
			if (logEnable) log.debug "Device ${device.displayName}: received Humidity report in percentage: ${cmd}."
		} else if (scale == 0x01) // Absolute (g/m3)
		{
			if (logEnable) log.debug "Device ${device.displayName}: received Humidity report in g/m3: ${cmd}."
		}
		break		
	case 0x0F: // voltage
		if (scale == 0x00) // Volt
		{
			if (logEnable) log.debug "Device ${device.displayName}: received voltage report in Volts: ${cmd}."
		} else if (scale == 0x01) // milliVolt
		{
			if (logEnable) log.debug "Device ${device.displayName}: received voltage report in milliVolts: ${cmd}."
		}
		break		
	case 0x40: // outside temperature
		if (scale == 0x00) // Celcius
		{
			if (logEnable) log.debug "Device ${device.displayName}: received outside temperature report in celsius: ${cmd}."
		} else if (scale == 0x01) // Fahrenheit
		{
			if (logEnable) log.debug "FDevice ${device.displayName}: received outside temperature report in fahrenheit: ${cmd}."
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
void zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationSupportedReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep ) }
void zwaveEvent(hubitat.zwave.commands.notificationv4.NotificationSupportedReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep ) }
void zwaveEvent(hubitat.zwave.commands.notificationv5.NotificationSupportedReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep ) }
void zwaveEvent(hubitat.zwave.commands.notificationv6.NotificationSupportedReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep ) }
void zwaveEvent(hubitat.zwave.commands.notificationv7.NotificationSupportedReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep ) }
void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationSupportedReport cmd, ep = null )  { processNotificationSupportedReport(cmd, ep ) }
void processNotificationSupportedReport (cmd, ep )  
{ 
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Integer) == ep}
	} else { targetDevice = device }		
	
	if (ep) log.warn "Device ${device.displayName}: Endpoint handling in report type NotificationSupportedReport is incomplete! Alert developer."

	if (logEnable) log.debug "Device ${device.displayName}: Received Notification Supported Report: " + cmd 
		List<hubitat.zwave.Command> cmds=[]
	
	if (cmd.accessControl) 	cmds << secure(zwave.notificationV3.eventSupportedGet(notificationType: 6)) // Access Control
	if (cmd.burglar)		cmds << secure(zwave.notificationV3.eventSupportedGet(notificationType: 7)) // Burglar
	// if (cmd.clock)
	// if (cmd.co)
	// if (cmd.co2)
	// if (cmd.energency)
	// if (cmd.first)
	// if (cmd.heat)
	// if (cmd.powerManagement)
	if (cmd.smoke)		cmds << secure(zwave.notificationV3.eventSupportedGet(notificationType: 1)) // Smoke
	// if (cmd.system
	if (cmd.water)		cmds << secure(zwave.notificationV3.eventSupportedGet(notificationType: 5)) // Water

	if (cmds) sendToDevice(cmds)
}

void zwaveEvent(hubitat.zwave.commands.notificationv3.EventSupportedReport cmd, ep = null )  { processEventSupportedReport(cmd, ep ) }
void zwaveEvent(hubitat.zwave.commands.notificationv4.EventSupportedReport cmd, ep = null )  { processEventSupportedReport(cmd, ep ) }
void zwaveEvent(hubitat.zwave.commands.notificationv5.EventSupportedReport cmd, ep = null )  { processEventSupportedReport(cmd, ep ) }
void zwaveEvent(hubitat.zwave.commands.notificationv6.EventSupportedReport cmd, ep = null )  { processEventSupportedReport(cmd, ep ) }
void zwaveEvent(hubitat.zwave.commands.notificationv7.EventSupportedReport cmd, ep = null )  { processEventSupportedReport(cmd, ep ) }
void zwaveEvent(hubitat.zwave.commands.notificationv8.EventSupportedReport cmd, ep = null )  { processEventSupportedReport(cmd, ep ) }
void processEventSupportedReport (cmd, ep )  
{ 
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Integer) == ep}
	} else { targetDevice = device }	
	
	if (ep) log.warn "Device ${device.displayName}: Endpoint handling in report type EventSupportedReport is incomplete! Alert developer."

	Boolean isMotionSensor = false
	Boolean isLeakSensor = false
	Boolean isTamperSensor = false

	switch (cmd.notificationType)
	{
	case 1: // smoke
		if (logEnable) log.debug "Device ${device.displayName}: EventSupportedReport Smoke Type is: ${cmd}."
		break
	case 5: // Water
		if (logEnable) log.debug "Device ${device.displayName}: EventSupportedReport Water Type is: ${cmd}."
		if (cmd.supportedEvents[1] || cmd.supportedEvents[2]) isLeakSensor = true // Motion Detected
		break
	case 6: // Access Control
		if (logEnable) log.debug "Device ${device.displayName}: EventSupportedReport Access Control Type is: ${cmd}."
		break
	case 7: // burglar
		if (logEnable) log.debug "Device ${device.displayName}: EventSupportedReport burglar Type is: ${cmd}."
		if (cmd.supportedEvents[7] || cmd.supportedEvents[8]) isMotionSensor = true // Motion Detected
		if (cmd.supportedEvents[9]) isTamperSensor = true // Tamper, product moved
		break
	}

	if (isMotionSensor)
	{
		String childNetworkID = "${device.displayName}-Motion"
		def cd = getChildDevice(childNetworkID)
		if (!cd) cd = addChildDevice("hubitat", "Generic Component Motion Sensor", childNetworkID, [name: childNetworkID, isComponent: true])
	}
	if (isLeakSensor)
	{
		String childNetworkID = "${device.displayName}-Leak"
		def cd = getChildDevice(childNetworkID)
		if (!cd) cd = addChildDevice("hubitat", "Generic Component Water Sensor", childNetworkID, [name: childNetworkID, isComponent: true])
	}

	if (logEnable) log.debug "Device ${device.displayName}: Received Event Notification Supported Report: " + cmd 
}

// v1 and v2 are not implemented in Hubitat. 
void zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd, ep = null)  { processNotificationReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.notificationv4.NotificationReport cmd, ep = null)  { processNotificationReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.notificationv5.NotificationReport cmd, ep = null)  { processNotificationReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.notificationv6.NotificationReport cmd, ep = null)  { processNotificationReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.notificationv7.NotificationReport cmd, ep = null)  { processNotificationReport(cmd, ep) }
void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd, ep = null)  { processNotificationReport(cmd, ep) }
void processNotificationReport(cmd, ep)
{
	def targetDevice
	if (ep) {
		targetDevice = getChildDevices().find{ (it.deviceNetworkId.split("-ep")[-1] as Integer) == ep}
	} else { targetDevice = device }		
	
	if (ep) log.warn "Device ${device.displayName}: Endpoint handling in report type NotificationReport is incomplete! Alert developer."

	if (logEnable) log.debug "Device ${device.displayName}: Processing Notification Report: " + cmd  
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
			log.warn "Device ${device.displayName}: Received a Notification Report with type: ${cmd.notificationType}, which is a type not processed by this driver."
	}
	
	events.each{ sendEventToAll(it) }
}

List<Map> processSmokeAlarmNotification(cmd)
{
	List<Map> events = []
	switch (cmd.event as Integer)
	{
		case 0x00: // Status Idle
			if (logEnable) log.debug "Device ${device.displayName}:  Smoke Alarm Notification, Status Idle."
			events << [name:"smoke" , value:"clear", descriptionText:"Smoke detector status Idle."]
			break
		case 0x01: // Smoke detected (location provided)
			if (logEnable) log.debug "Device ${device.displayName}:  Smoke Alarm Notification, Smoke detected (location provided)."
			events << [name:"smoke" , value:"detected", descriptionText:"Smoke detected (location provided)."]
			break
		case 0x02: // Smoke detected
			if (logEnable) log.debug "Device ${device.displayName}:  Smoke Alarm Notification, Smoke detected."
			events << [name:"smoke" , value:"detected", descriptionText:"Smoke detected."]
			break
		case 0xFE: // Unknown Event / State
			if (logEnable) log.debug "Device ${device.displayName}:  Smoke Alarm Notification, Unknown Event / State."
		default :
			log.warn "Device ${device.displayName}:  Received a Notification Report with type: ${cmd.notificationType}, which is a type not processed by this driver."
	}
	return events
}

List<Map> processWaterAlarmNotification(cmd)
{
	List<Map> events = []
	switch (cmd.event as Integer)
	{
		case 0x00: // Status Idle
			if (logEnable) log.debug "Device ${device.displayName}:  Water Alarm Notification, Status Idle."
			events << [name:"water" , value:"dry", descriptionText:"Water Alarm Notification, Status Dry."]
			break
		case 0x01: // Water leak detected (location provided)
			if (logEnable) log.debug "Device ${device.displayName}:  Water Alarm Notification, Water leak detected (location provided)."
			events << [name:"water" , value:"wet", descriptionText:"Water leak detected (location provided)."]
			break
		case 0x02: // Water leak detected
			if (logEnable) log.debug "Device ${device.displayName}:  Water Alarm Notification, Water leak detected."
			events << [name:"water" , value:"wet", descriptionText:"Water leak detected."]
			break
		case 0xFE: // Unknown Event / State
			if (logEnable) log.debug "Device ${device.displayName}:  Water Alarm Notification, Unknown Event / State."
		default :
			log.warn "Device ${device.displayName}:  Received a Notification Report with type: ${cmd.notificationType}, which is a type not processed by this driver."
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
   sendToDevice (secure( zwave.doorLockV1.doorLockOperationSet(doorLockMode: 255) ))
}
void unlock()
{
   sendToDevice (secure( zwave.doorLockV1.doorLockOperationSet(doorLockMode: 0) )  )
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

//////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////
////////////        Learn About Endpoints        ////////////////
/////////////////////////////////////////////////////////////////////////////////////////////// 


@Field static  ConcurrentHashMap<String, Map> endPointInformation = new ConcurrentHashMap<String, Map>()

@Field static Semaphore getEndpointMutex = new Semaphore(1)


Map getEndpointMap() { 
	String key = productKey()
	return endPointInformation.get(key, [:])
}

Map   getEndpointData(){

	getEndpointMutex.tryAcquire(1, 5, TimeUnit.SECONDS )

	if (logEnable) log.debug "Device ${device.displayName}: Getting endpoint information."
	
	sendToDevice(secure(zwave.multiChannelV3.multiChannelEndPointGet()))

	getEndpointMutex.tryAcquire(1, 10, TimeUnit.SECONDS )

	getEndpointMutex.release(1)
	
	Integer index = 1
	Integer endPointCount = getEndpointMap().get("report").get("endPoints") as Integer
	if (logEnable) log.debug "EndPoint Count is: " + endPointCount
	for (index = 1; index <= endPointCount; index++)
	{
		if (logEnable) log.debug "Getting capabilities of endpoint #: ${index}."
		getEndpointCapabilityData(index)
	}
	return getEndpointMap()
}

// There are 3 versions of command class reports - could just include only the highest and let Groovy resolve!
void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelEndPointReport  cmd) { processMultiChannelEndPointReport (cmd) }
void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelEndPointReport  cmd) { processMultiChannelEndPointReport (cmd) }
void processMultiChannelEndPointReport  (cmd) 
{

	if (logEnable) log.debug "MultiChannelEndpointReport is: $cmd"
	
	getEndpointMap().get("report", [:]).put("dynamic", (cmd.dynamic) as Boolean)
	getEndpointMap().get("report").put("endPoints", (cmd.endPoints) as Integer)
	getEndpointMap().get("report").put("identical", (cmd.identical) as Boolean)
	
	if (cmd.hasProperty("aggregatedEndPoints")) 	getEndpointMap().get("report").put("aggregatedEndPoints", (cmd.aggregatedEndPoints) as Integer)

	if (logEnable) log.debug "Stored data is: " + getEndpointMap()
	getEndpointMutex.release(1)
}

Map   getEndpointCapabilityData(ep)
{

	getEndpointMutex.tryAcquire(1, 5, TimeUnit.SECONDS )

	if (logEnable) log.debug "Device ${device.displayName}: Getting endpoint information."
	
	sendToDevice(secure(zwave.multiChannelV3.multiChannelCapabilityGet(endPoint:ep)))

	getEndpointMutex.tryAcquire(1, 10, TimeUnit.SECONDS )

	getEndpointMutex.release(1)
	return getEndpointMap()
}


void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCapabilityReport  cmd) { processMultiChannelCapabilityReport (cmd) }
void zwaveEvent(hubitat.zwave.commands.multichannelv4.MultiChannelCapabilityReport  cmd) { processMultiChannelCapabilityReport (cmd) }
void processMultiChannelCapabilityReport(cmd) 
{

	if (logEnable) log.debug "MultiChannelCapabilityReport is: ${cmd}"
	
	getEndpointMap().get("capabilityReport", [:]).get(cmd.endPoint, [:]).put("dynamic", (cmd.dynamic) as Boolean)
	getEndpointMap().get("capabilityReport", [:]).get(cmd.endPoint, [:]).put("genericDeviceClass", (cmd.genericDeviceClass) as Integer)
	getEndpointMap().get("capabilityReport", [:]).get(cmd.endPoint, [:]).put("specificDeviceClass", (cmd.specificDeviceClass) as Integer)
	getEndpointMap().get("capabilityReport", [:]).get(cmd.endPoint, [:]).put("commandClass", cmd.commandClass)
	if (logEnable) log.debug "capabilityReport data is: " + getEndpointMap()
	getEndpointMutex.release(1)
}
