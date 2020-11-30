/*
Operation Sequence ...

1. Get the device's firmware using: getFirmwareVersionFromDevice(), store it in state.universalDriverData.firmware.[main: ##, sub: ##]
2. Get the device's database record using: getDeviceDataFromDatabase


*/

if ( ! state.universalDriverData) { state.universalDriverData = [:] }

metadata {
        definition (name: "Super Universal Zwave Plus Dimmer",namespace: "jvm", author: "jvm") {
 		// Pick one of the following 5 Capabilities. Comment out the remainder.
			// capability "Bulb"
			// capability "Light"
			// capability "Outlet"		
			// capability "RelaySwitch"
			capability "Switch"		
		
		// Include the following for dimmable devices.
			capability "SwitchLevel"
			capability "ChangeLevel"
		
		capability "Initialize"
		capability "Configuration"
		capability "Refresh"
		
		// Central Scene functions. Include the "commands" if you want to generate central scene actions from the web interface. If they are not included, central scene will still be generated from the device.
			capability "PushableButton"
				command "push", ["NUMBER"]	
				
			capability "HoldableButton"
				command "hold", ["NUMBER"]
				
			capability "ReleasableButton"
				command "release", ["NUMBER"]
				
			capability "DoubleTapableButton"
					command "doubleTap", ["NUMBER"]
			
        // The following is for debugging. In final code, it can be removed!
    	command "getDeviceDataFromDatabase"
		
		// All data used by this driver is stored as a Map (more particulaly, as a Map of Maps) 
		// in the state variable "state.universalDriverData". The "uninstall" command deletes that key
		// to clean up the data before the user changest back to the retular driver
        command "uninstall"
		command "EraseState"
		// A generalized function for setting parameters.	
			command "setParameter",[[name:"parameterNumber",type:"NUMBER", description:"Parameter Number", constraints:["NUMBER"]],
					[name:"size",type:"NUMBER", description:"Parameter Size", constraints:["NUMBER"]],
					[name:"value",type:"NUMBER", description:"Parameter Value", constraints:["NUMBER"]]
					]		
		
        fingerprint inClusters:"0x5E,0x25,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x5B,0x6C,0x9F,0x7A", deviceJoinName: "ZWave Plus CentralScene Dimmer" //US
    }
    preferences 
	{
        input name: "advancedEnable", type: "bool", title: "Enable Advanced Configuration", defaultValue: true
        
        if (advancedEnable)
        {
			input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
			input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: true
			input name: "confirmSend", type: "bool", title: "Always confirm new value after sending to device (reduces performance)", defaultValue: false
			state.universalDriverData?.zwaveParameters?.each { input it.value.input }
        }
    }
}

/*
//////////////////////////////////////////////////////////////////////
//////      Get Device's Database Information Version          ///////
////////////////////////////////////////////////////////////////////// 
The function getDeviceDataFromDatabase() accesses the Z-Wave device database at www.opensmarthouse.org to
retrieve a database record that contains a detailed descrption of the device.
Since the database records are firmware-dependent, This function 
should be called AFTER retrieving the device's firmware version using getFirmwareVersionFromDevice().
*/
void getDeviceDataFromDatabase()
{
	if (logEnable) log.debug "Getting Device Information from Database"
  
	String manufacturer = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("manufacturer").toInteger(), 2)
	String deviceType = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceType").toInteger(), 2)
	String deviceID = 		hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceId").toInteger(), 2)
	
    if (logEnable) log.debug " manufacturer: ${manufacturer}, deviceType: ${deviceType}, deviceID: ${deviceID}, Version: ${state.universalDriverData.firmware.main}, SubVersion: ${state.universalDriverData.firmware.sub}"

    String DeviceInfoURI = "http://www.opensmarthouse.org/dmxConnect/api/zwavedatabase/device/list.php?filter=manufacturer:0x${manufacturer}%20${deviceType}:${deviceID}"

    def mydevice
    
    httpGet([uri:DeviceInfoURI])
    { resp->
        if(logEnable) log.debug "Response Data: ${resp.data}"
        if(logEnable) log.debug "Response Data class: ${resp.data instanceof Map}"
        
		mydevice = resp.data.devices.find { element ->
     
            Minimum_Version = element.version_min.split("\\.")
            Maximum_Version = element.version_max.split("\\.")
            Integer minMainVersion = Minimum_Version[0].toInteger()
            Integer minSubVersion = Minimum_Version[1].toInteger()
            Integer maxMainVersion = Maximum_Version[0].toInteger()
            Integer maxSubVersion =   Maximum_Version[1].toInteger()        
    
            Boolean aboveMinimumVersion = (state.universalDriverData.firmware.main > minMainVersion) || ((state.universalDriverData.firmware.main == minMainVersion) && (state.universalDriverData.firmware.sub >= minSubVersion))
			
            Boolean belowMaximumVersion = (state.universalDriverData.firmware.main < maxMainVersion) || ((state.universalDriverData.firmware.main == maxMainVersion) && (state.universalDriverData.firmware.sub <= maxSubVersion))
            
            aboveMinimumVersion && belowMaximumVersion
        }
	}

    if(logEnable) log.debug "Database Identifier: ${mydevice.id}"  
    
    String queryByDatabaseID= "http://www.opensmarthouse.org/dmxConnect/api/zwavedatabase/device/read.php?device_id=${mydevice.id}" 
    
    def allParameterData
    
     httpGet([uri:queryByDatabaseID])
        { resp->
			log.info "Processing data for device model: ${resp.data?.label}, Manufacturer: ${resp.data?.manufacturer?.label}"

            if (logEnable) log.debug "Parameter Data in Response: ${resp.data.parameters}"
            allParameterData = resp.data.parameters
        }

    allParameterData.each{
                if(logEnable) log.debug "Parameter ${it.param_id}: label: ${it.label}, Is Bitmask: ${it.bitmask}:${it.bitmask.toInteger() == 0 ? false : true }"
                }
    def allInputs = []
    
    def newData = allParameterData.each{
	
        if (it.bitmask.toInteger())
		{
		log.warn "Parameter ${it.param_id} is specified in bitmap form. This is currently unsupported"
		return
		}
		
        Map newInput = [name: "configParam${"${it.param_id}".padLeft(3, "0")}", title: "(${it.param_id}) ${it.label}", description: it.description, defaultValue: it.default, parameterSize:it.size]
        def deviceOptions = [:]
        it.options.each
        {
            deviceOptions.put(it.value, it.label)
        }
        
        // Set input type. Should be one of: bool, date, decimal, email, enum, number, password, time, text. See: https://docs.hubitat.com/index.php?title=Device_Preferences
        if (deviceOptions)
        {
            newInput.type = "enum"
            newInput.options = deviceOptions
        }
        else
        {
            newInput.type = "integer"
        }
        if(logEnable) log.debug "deviceOptions is $deviceOptions"     
             
        if(logEnable) log.debug "newInput = ${newInput}"
        state.universalDriverData.zwaveParameters.put(it.param_id, [input: newInput])
    }
    if (logEnable) log.debug newData
}

//////////////////////////////////////////////////////////////////////
//////      Initialization, update, and uninstall sequence          ///////
////////////////////////////////////////////////////////////////////// 
void refresh() {
	if(txtEnable) "Refreshing device ${device.displayName} status .."
    List<hubitat.zwave.Command> cmds=[]
	cmds.add(zwave.basicV1.basicGet())
    sendToDevice(cmds)
}

void installed()
{
    if (!state.universalDriverData)  state.universalDriverData = [:] 
    if (!state.universalDriverData.zwaveParameters) state.universalDriverData.zwaveParameters =[:]
	if (!state.universalDriverData.ZwaveClassVersions) state.universalDriverData.ZwaveClassVersions = [:]

    getFirmwareVersionFromDevice() // sets the firmware version in state.universalDriverData.firmware[main: ??,sub: ??]

    getDeviceDataFromDatabase()
	
	getZwaveClassVersions()
		
	pollDevicesForCurrentValues()
}

void configure()
{
}

void EraseState()
{
state.clear()
}

void initialize()
{
    if (!state.universalDriverData)  state.universalDriverData = [:] 
    if (!state.universalDriverData.zwaveParameters) state.universalDriverData.zwaveParameters =[:]
	if (!state.universalDriverData.ZwaveClassVersions) state.universalDriverData.ZwaveClassVersions = [:]

    getFirmwareVersionFromDevice() // sets the firmware version in state.universalDriverData.firmware[main: ??,sub: ??]
    
	getDeviceDataFromDatabase()
	
	getZwaveClassVersions()

	pollDevicesForCurrentValues()
}

void uninstall()
{
    state.remove("parameterTool")
	state.remove("universalDriverData")
	state.remove("ZwaveClassVersions")
}

void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void updated()
{
	if (logEnable) log.debug "Updated function called"
	if (logEnable) runIn(1800,logsOff)

	/*
	state.universalDriverData.zwaveParameters is arranged in key : value pairs.
	key is the parameter #
	value is a map of "input" controls, which is arranged under the sub-key "input"
	so values are accessed as v.[input:[defaultValue:0, name:configParam004, parameterSize:1, options:[0:Normal, 1:Inverted], description:Controls the on/off orientation of the rocker switch, title:(4) Orientation, type:enum]]
	*/
	state.universalDriverData?.zwaveParameters.each { k , v -> 

        if ((v.lastRetrievedValue as Integer) != (settings.get(v.input.name) as Integer) )
        { 
			if (logEnable) log.debug "Parameter ${k} Last retrieved value ${v.lastRetrievedValue}, requested settings value ${settings.get(v.input.name)}"
			setParameter(k, v.input.parameterSize, settings.get(v.input.name) ) 
        }
		else
		{
			if (logEnable) log.debug "Parameter ${k} is unchanged with value ${settings.get(v.input.name)}"
		}
     }
}

//////////////////////////////////////////////////////////////////////
///////        Set, Get, and Process Parameter Values         ////////
////////////////////////////////////////////////////////////////////// 

void getParameterValue(parameterNumber)
{
 	if (logEnable) log.debug "Getting value of parameter ${parameterNumber}"

    List<hubitat.zwave.Command> cmds=[]	
		cmds.add(secure(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber as Integer)))
	if (cmds) sendToDevice(cmds)
}

void setParameter(parameterNumber, parameterSize, value){
	if (txtEnable) log.info "Setting parameter ${parameterNumber}, of size ${parameterSize} to value ${value}."
    if (parameterNumber.is( null ) || parameterSize.is( null ) || value.is( null )) {
		log.warn "incomplete parameter list supplied..."
		log.warn "syntax: setParameter(parameterNumber,parameterSize,value)"
		return
    } 

	List<hubitat.zwave.Command> cmds=[]

	// All versions of configurationSet work the same!
	cmds.add(secure(zwave.configurationV2.configurationSet(scaledConfigurationValue: value as Integer, parameterNumber: parameterNumber as Integer, size: parameterSize as Integer)))
	cmds.add "delay 500"
	cmds.add(secure(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber as Integer)))
	
	if (cmds) sendToDevice(cmds)
}

void pollDevicesForCurrentValues()
{
	// On startup, this should poll all the devices for their initial values!
	state.universalDriverData.zwaveParameters.each { k , v -> getParameterValue(k)  }
}

void zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	
	if (logEnable) log.debug "Received configuration report for parameter ${cmd.parameterNumber} indicating parameter set to value: ${cmd.scaledConfigurationValue}. Full report contents are: ${cmd}."

		String parameterName = "configParam${"${cmd.parameterNumber}".padLeft(3, "0")}"
		def currentValue = settings[parameterName]
		def reportedValue  = cmd.scaledConfigurationValue
		
		// Sometimes the 	www.opemsmarthouse.org database has the wrong parameter sizes. This tries to correct that!
		Integer storedSize = state.universalDriverData.zwaveParameters["${cmd.parameterNumber}"].input.parameterSize
		if (cmd.size != storedSize)
		{
			log.warn "Configuration report V2 returned from device for parameter ${cmd.parameterNumber} indicates a size of ${cmd.size}, while the database from www.opensmarthouse.org gave a size of ${storedSize}. Please report to developer! Making correction to local database. Please try your parameter setting again."
			state.universalDriverData.zwaveParameters["${cmd.parameterNumber}"].input.put("parameterSize", cmd.size)
		}
		
		if (currentValue != reportedValue)
		{
			settings[parameterName] = reportedValue
		}
		state.universalDriverData.zwaveParameters["$cmd.parameterNumber"].lastRetrievedValue = cmd.scaledConfigurationValue
}

// This is the exact same code as the preceding, with a v1 as the signature!
void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	
	if (logEnable) log.debug "Received configuration report for parameter ${cmd.parameterNumber} indicating parameter set to value: ${cmd.scaledConfigurationValue}. Full report contents are: ${cmd}."

		String parameterName = "configParam${"${cmd.parameterNumber}".padLeft(3, "0")}"
		def currentValue = settings[parameterName]
		def reportedValue  = cmd.scaledConfigurationValue
		
		// Sometimes the 	www.opemsmarthouse.org database has the wrong parameter sizes. This tries to correct that!
		if (cmd.size != state.universalDriverData.zwaveParameters["$cmd.parameterNumber"].input.parameterSize)
		{
			log.warn "Configuration report V1 returned from device for parameter ${cmd.parameterNumber} indicates a size of ${cmd.size}, while the database from www.opensmarthouse.org gave a size of ${state.universalDriverData.zwaveParameters["$cmd.parameterNumber"].input.parameterSize}. Please report to developer! Making correction to local database. Please try your parameter setting again."
			state.universalDriverData.zwaveParameters["$cmd.parameterNumber"].input.parameterSize = cmd.size
		}
		
		if (currentValue != reportedValue)
		{
			settings[parameterName] = reportedValue
		}
		state.universalDriverData.zwaveParameters["$cmd.parameterNumber"].lastRetrievedValue = cmd.scaledConfigurationValue
}
//////////////////////////////////////////////////////////////////////
//////                  Handle Supervision request            ///////
////////////////////////////////////////////////////////////////////// 
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    if (logEnable) log.debug "For ${device.displayName}, Supervision get: ${cmd}"
	
	Map parseMap = state.universalDriverData.ZwaveClassVersions.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(parseMap)
	
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
    sendToDevice(new hubitat.zwave.commands.supervisionv1.SupervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0))
}
//////////////////////////////////////////////////////////////////////
//////                  Get Device Firmware Version            ///////
////////////////////////////////////////////////////////////////////// 
void queryForFirmwareReport()
{
	if (logEnable) log.debug "Querying for firmware report"

    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.versionV3.versionGet())
    sendToDevice(cmds)
}

void getFirmwareVersionFromDevice()
{
    if(!state.universalDriverData.firmware)
	{
        state.universalDriverData.firmware = [:]
		def deviceVersion = "${device.getDataValue("firmwareVersion")}".split("\\.")
		if(deviceVersion)
		{
			state.universalDriverData.firmware.main =deviceVersion[0].toInteger()
			state.universalDriverData.firmware.sub = deviceVersion[1].toInteger()
			if (logEnable) log.debug "Firmware Version is: ${state.universalDriverData.firmware}"
		}
		else
		{
		log.warn "Missing Firmware Version - have to get the firmware - run a ZWave Query!"
		queryForFirmwareReport()
		pauseExecution(5000)
		}
	}
    else
    {
       if (logEnable) log.debug "Firmware Version already exist: ${state.universalDriverData.firmware}"
    }
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
    if (logEnable) log.debug "For ${device.displayName}, Received V3 version report: ${cmd}"
	if (state.universalDriverData == null) state.universalDriverData = [:]
    state.universalDriverData.put("firmware", [main: cmd.firmware0Version, sub:cmd.firmware0SubVersion])
	
	if (logEnable) log.debug "state is: ${state }, state.universalDriverData is ${state.universalDriverData}"

}
//////////////////////////////////////////////////////////////////////
//////                  Z-Wave Helper Functions                ///////
//////   Format messages, Send to Device, secure Messages      ///////
////////////////////////////////////////////////////////////////////// 

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    // hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)

	Map parseMap = state.universalDriverData.ZwaveClassVersions.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(parseMap)
	
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void parse(String description) {
    // if (logEnable) log.debug "For ${device.displayName}, parse:${description}"

    hubitat.zwave.Command cmd = zwave.parse(description)

    if (cmd) {
        zwaveEvent(cmd)
    }
}

void sendToDevice(List<hubitat.zwave.Command> cmds) {
    sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(hubitat.zwave.Command cmd) {
    sendHubCommand(new hubitat.device.HubAction(secureCommand(cmd), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(String cmd) {
    sendHubCommand(new hubitat.device.HubAction(secureCommand(cmd), hubitat.device.Protocol.ZWAVE))
}

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=50) {
    return delayBetween(cmds.collect{ secureCommand(it) }, delay)
}

String secureCommand(hubitat.zwave.Command cmd) {
    secureCommand(cmd.format())
}

String secureCommand(String cmd) {
    String encap=""
    if (getDataValue("zwaveSecurePairingComplete") != "true") {
        return cmd
    } else {
        encap = "988100"
    }
    return "${encap}${cmd}"
}

String secure(String cmd){
    return zwaveSecureEncap(cmd)
}

String secure(hubitat.zwave.Command cmd){
    return zwaveSecureEncap(cmd)
}

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

Integer   getZwaveClassVersions(){
    List<hubitat.zwave.Command> cmds = []
	Integer getItems = 0
	
	if(logEnable) log.debug "Current Command Class version state is: ${state.universalDriverData.ZwaveClassVersions}"
	
	// All the inclusters suppored by the device
	List<Integer> deviceInclusters = getDataValue("inClusters").split(",").collect{ hexStrToUnsignedInt(it) as Integer }
		deviceInclusters << 32
	
	// The next list is the classes actually used by this driver. We only need info. on those classes!
	List<Integer> driverInclusters = [0x20, 0x25, 0x26, 0x5B, 0x6C, 0x70, 0x86]

    driverInclusters.each {
	
	if (deviceInclusters.contains(it)) 
		{
			Integer thisClass = it as Integer
			
			if ( !state.universalDriverData.ZwaveClassVersions?.get(thisClass as Integer) && !state.universalDriverData.ZwaveClassVersions?.get(thisClass as String) )
			{
	
			getItems += 1
			if(logEnable) log.debug "Requesting Command class version for class 0x${intToHexStr(it)}"
			// gets are the same in all command class versions
			cmds.add(zwave.versionV3.versionCommandClassGet(requestedCommandClass:it.toInteger()))
			}
		}
    }
	if(logEnable) log.debug "Getting ${getItems} command versions which were previously not retrieved."
	
    if(cmds) sendToDevice(cmds)
	return getItems
}

// There are 3 versions of command class reports - could just include only the highest and let Groovy resolve!
void zwaveEvent(hubitat.zwave.commands.versionv1.VersionCommandClassReport cmd) {
	if(logEnable) log.debug "Processing command class report V1 to update state.universalDriverData.ZwaveClassVersions"
	state.universalDriverData.ZwaveClassVersions?.put((cmd.requestedCommandClass as String), (cmd.commandClassVersion as Integer))
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionCommandClassReport cmd) {
	if(logEnable) log.debug "Processing command class report V2 to update state.universalDriverData.ZwaveClassVersions"
	state.universalDriverData.ZwaveClassVersions?.put((cmd.requestedCommandClass as String), (cmd.commandClassVersion as Integer))
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionCommandClassReport cmd) {
	if(logEnable) log.debug "Processing command class report V3 to update state.universalDriverData.ZwaveClassVersions received command ${cmd}. Current stored value array is ${state.universalDriverData.ZwaveClassVersions}."
	if (! state.universalDriverData) state.universalDriverData = [:]
	state.universalDriverData.ZwaveClassVersions.put(cmd.requestedCommandClass, cmd.commandClassVersion)

	if(logEnable) log.debug "Array after adding value is ${state.universalDriverData.ZwaveClassVersions}."
}

///////////////////////////////////////////////////////////////////////////////////////////////
///////////////                  Central Scene Processing          ////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////

// The 'get" is the same in all versions of command class so just use the highest version supported!
void getCentralSceneInfo() {
    List<hubitat.zwave.Command> cmds = []
	cmds.add( zwave.centralSceneV3.centralSceneSupportedGet() )
	sendToDevice(cmds)
}

// ====================
void zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneSupportedReport  cmd) {
    if(logEnable) log.debug "Central Scene V1 Supported Report Info ${cmd}"	
	state.centralScene = cmd
	sendEvent(name: "numberOfButtons", value: cmd.supportedScenes, isStateChange:true)
}

void zwaveEvent(hubitat.zwave.commands.centralscenev2.CentralSceneSupportedReport  cmd) {
    if(logEnable) log.debug "Central Scene V2 Supported Report Info ${cmd}"	
	state.centralScene = cmd
	sendEvent(name: "numberOfButtons", value: cmd.supportedScenes, isStateChange:true)
}

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneSupportedReport  cmd) {
    if(logEnable) log.debug "Central Scene V3 Supported Report Info ${cmd}"	
	state.centralScene = cmd
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
	state.universalDriverState.buttons.put(button, "released")

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
    try{
	switch(button)
	    {
	    case 1: unschedule(forceReleaseHold01); break
	    case 2: unschedule(forceReleaseHold02); break
	    case 3: unschedule(forceReleaseHold03); break
	    case 4: unschedule(forceReleaseHold04); break
	    case 5: unschedule(forceReleaseHold05); break
	    case 6: unschedule(forceReleaseHold06); break
	    case 7: unschedule(forceReleaseHold07); break
	    case 8: unschedule(forceReleaseHold08); break
	    default: log.warn "Attempted to process lost release message code for button ${button}, but this is an error as code handles a maximum of 8 buttons."
	    }
    }
    catch(Exception ex) { log.debug "Exception in function cancelLostReleaseTimer: ${ex}"}

}

void setReleaseGuardTimer(button)
{
	// The code starts a release hold timer which will force a "release" to be issued
	// if a refresh isn't received within the slow refresh period!
	// If you get a refresh, executing again restarts the timer!
	// Timer is canceled by the cancelLostReleaseTimer if a "real" release is received.
	switch(button)
	{
	case 1: runIn(60, forceReleaseHold01); break
	case 2: runIn(60, forceReleaseHold02); break
	case 3: runIn(60, forceReleaseHold03); break
	case 4: runIn(60, forceReleaseHold04); break
	case 5: runIn(60, forceReleaseHold05); break
	case 6: runIn(60, forceReleaseHold06); break
	case 7: runIn(60, forceReleaseHold07); break
	case 8: runIn(60, forceReleaseHold08); break
	default: log.warn "Attempted to process lost release message code for button ${button}, but this is an error as code handles a maximum of 8 buttons."
	}
}

// ==================  End of code to help handle a missing "Released" messages =====================

int tapCount(attribute)
{
	// Converts a Central Scene command.keyAttributes value into a tap count
	// Returns negative numbers for special values of Released (-1) and Held (-2).
	switch(attribute)
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
		default:  // For 3 or grater, subtract 1 from the attribute to get # of taps.
			return (attribute - 1)
			break
	}
}
void zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
	ProcessCCReport(cmd)
}
void zwaveEvent(hubitat.zwave.commands.centralscenev2.CentralSceneNotification cmd) {
	ProcessCCReport(cmd)
}

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd){
	ProcessCCReport(cmd)
}

void ProcessCCReport(cmd) {
if (! state.universalDriverState) { state.universalDriverState = [:] }
if (! state.universalDriverState.buttons) { state.universalDriverState.buttons = [:] }

    Map event = [type:"physical", isStateChange:true]
	if(logEnable) log.debug "Received Central Scene Notification ${cmd}"
	
	def taps = tapCount(cmd.keyAttributes)
	
	if(logEnable) log.debug "Mapping of key attributes to Taps: ${taps}"
	
		if (state.universalDriverState.buttons.get(cmd.sceneNumber) == "held")
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

		switch(taps)
		{
			case -1:
                cancelLostReleaseTimer(cmd.sceneNumber)
				event.name = "released" 
				event.value = cmd.sceneNumber
				event.descriptionText="${device.displayName} button ${event.value} released"
				if (txtEnable) log.info event.descriptionText
				state.universalDriverState.buttons.put(cmd.sceneNumber, event.name)

				sendEvent(event)
				break

			case -2:	
				event.name = "held" 
				event.value = cmd.sceneNumber

				if (state.universalDriverState.buttons.get(cmd.sceneNumber) == "held")
				{
					// If currently holding and receive a refresh, don't send another hold message
					// Just report that still holding
					// Refresh received every 55 seconds if slowRefresh is enabled by the device
					// Else its received every 200 mSeconds.
					if (txtEnable) log.info "Still Holding button ${cmd.sceneNumber}"
				} 
				else
				{
					event.descriptionText="${device.displayName} button ${event.value} held"
					if (txtEnable) log.info event.descriptionText
					state.universalDriverState.buttons.put(cmd.sceneNumber, event.name)
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
				state.universalDriverState.buttons.put(cmd.sceneNumber, event.name)
				sendEvent(event)
				break				
	 
			case 2:
				event.name = "doubleTapped" 
				event.value=cmd.sceneNumber
				event.descriptionText="${device.displayName} button ${cmd.sceneNumber} doubleTapped"
				if (txtEnable) log.info event.descriptionText
				state.universalDriverState.buttons.put(cmd.sceneNumber, event.name)
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
//////        Handle Basic Reports and Device Functions        ///////
////////////////////////////////////////////////////////////////////// 

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
						descriptionText: "Device ${device.displayName} set to ${value}.", 
						type: (state.universalDriverData.get("isDigital") as Boolean ) ? "digital" : "physical" )
	}

    if ((device.hasAttribute("level")  || device.hasCapability("SwitchLevel") ) && (cmd.value != 0))
	{
		eventProcess( 	name: "level", value: cmd.value, 
						descriptionText: "Device ${device.displayName} level set to ${value}%", 
						type: (state.universalDriverData.get("isDigital") as Boolean ) ? "digital" : "physical" )
	}
	state.universalDriverData.put("isDigital", false)
}

void zwaveEvent(hubitat.zwave.commands.basicv2.BasicReport cmd) {

    if (logEnable) log.debug "Received BasicReport v2 containing: $cmd}"
	if ((cmd.value != cmd.targetValue) && (cmd.duration == 0)) log.warn "Received a V2 Basic Report with mismatched value and targetValue and non-zero duration: ${cmd}."
    if (device.hasAttribute("switch") || device.hasCapability("Switch")) 
	{
		eventProcess(	name: "switch", value: (cmd.targetValue ? "on" : "off"), 
						descriptionText: "Device ${device.displayName} set to ${value}.", 
						type: (state.universalDriverData.get("isDigital") as Boolean ) ? "digital" : "physical" )
	}

    if ((device.hasAttribute("level")  || device.hasCapability("SwitchLevel") ) && (cmd.targetValue != 0))
	{
		eventProcess( 	name: "level", value: cmd.targetValue, 
						descriptionText: "Device ${device.displayName} level set to ${value}%", 
						type: (state.universalDriverData.get("isDigital") as Boolean ) ? "digital" : "physical" )
	}
	state.universalDriverData.put("isDigital", false)
}

//returns on physical v1
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd){

	if (logEnable) log.debug "Received MultiLevel v1 Report containing: $cmd}"
    if (device.hasAttribute("switch") || device.hasCapability("Switch")) 
	{
		eventProcess(	name: "switch", value: (cmd.value ? "on" : "off"), 
						descriptionText: "Device ${device.displayName} set to ${(cmd.value ? "on" : "off")}", type: "physical" )
	}

    if ((device.hasAttribute("level")  || device.hasCapability("SwitchLevel") ) && (cmd.value != 0))
	{
		eventProcess( 	name: "level", value: cmd.value, 
						descriptionText: "Device ${device.displayName} level set to ${cmd.value}%", type: "physical" )
	}
	state.universalDriverData.put("isDigital", false)
}

//returns on physical v2
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelReport cmd){
    if (logEnable) log.debug "SwitchMultilevelV2Report value: ${cmd}"
    if (device.hasAttribute("switch") || device.hasCapability("Switch")) 
	{
		eventProcess(	name: "switch", value: (cmd.value ? "on" : "off"), 
						descriptionText: "Device ${device.displayName} set to ${(cmd.value ? "on" : "off")}", type: "physical" )
	}

    if ((device.hasAttribute("level")  || device.hasCapability("SwitchLevel") ) && (cmd.value != 0))
	{
		eventProcess( 	name: "level", value: cmd.value, 
						descriptionText: "Device ${device.displayName} level set to ${cmd.value}%", type: "physical" )
	}
	state.universalDriverData.put("isDigital", false)
}

//returns on physical v3
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd){
    if (logEnable) log.debug "SwitchMultilevelV3Report value: ${cmd}"
    if (device.hasAttribute("switch") || device.hasCapability("Switch")) 
	{
		eventProcess(	name: "switch", value: (cmd.value ? "on" : "off"), 
						descriptionText: "Device ${device.displayName} set to ${(cmd.value ? "on" : "off")}", type: "physical" )
	}

    if ((device.hasAttribute("level")  || device.hasCapability("SwitchLevel") ) && (cmd.value != 0))
	{
		eventProcess( 	name: "level", value: cmd.value, 
						descriptionText: "Device ${device.displayName} level set to ${cmd.value}%", type: "physical" )
	}
	state.universalDriverData.put("isDigital", false)
}


void eventProcess(Map event) {
    if (device.currentValue(event.name).toString() != event.value.toString() ) {
	log.debug "${device.displayName} has current value ${device.currentValue(event.name).toString()} and new event value ${event.value.toString()}."
        event.isStateChange=true
        sendEvent(event)
    }
}

void on() {
	if (device.hasCapability("SwitchLevel")) {
		Integer levelValue = (device.currentValue("level") as Integer) ?: 99
		if (txtEnable) log.info "Turning device ${device.displayName} On to Level: ${levelValue}."

		sendToDevice(secure(zwave.basicV1.basicSet(value: levelValue )))		
	}
	else {
		if (txtEnable) log.info "Turning device ${device.displayName} to: On."
		sendToDevice(secure(zwave.basicV1.basicSet(value: 255 )))
	}
	
	if (confirmSend ) 
	{
	state.universalDriverData.put("isDigital", true)
		sendToDevice (secure(zwave.basicV1.basicGet()))
	} else {
		sendEvent(name: "switch", value: "on", descriptionText: "Device ${device.displayName} turned on", 
			type: "digital", isStateChange: (device.currentValue("switch") == "on") ? false : true )
	}
}

void off() {
	if (txtEnable) log.info "Turning device ${device.displayName} to: Off."
	
	sendToDevice (secure(zwave.basicV1.basicSet(value: 0 )))
	if (confirmSend ) 
	{
	state.universalDriverData.put("isDigital", true)
		sendToDevice (secure(zwave.basicV1.basicGet()))
	} else {
		sendEvent(name: "switch", value: "off", descriptionText: "Device ${device.displayName} turned off", 
				type: "digital",  isStateChange: (device.currentValue("switch") == "off") ? false : true )
	}
}


void setLevel(level, duration = 0)
{
	state.universalDriverData.put("isDigital", true)
	if (logEnable) log.debug "Executing function setlevel(level, duration)."
	if ( level < 0  ) level = 0
	if ( level > 99 ) level = 99
	if ( duration < 0 ) duration = 0
	if ( duration > 127 ) duration = 127

	if (level == 0)
	{
		Boolean stateChange = ((device.currentValue("level") != 0) ? true : false)
		sendEvent(name: "switch", value: "off", descriptionText: "Device ${device.displayName} remains at off", type: "digital", isStateChange: stateChange )
		
			List<hubitat.zwave.Command> cmds = []
			if (state.universalDriverData.ZwaveClassVersions?.get("38") == 1)
			{
				cmds.add(secure(zwave.switchMultilevelV1.switchMultilevelSet(value: 0)))
				log.warn "${device.displayName} does not support dimming duration settting command. Defaulting to dimming duration set by device parameters."
			} else {
				cmds.add(secure(zwave.switchMultilevelV2.switchMultilevelSet(value: 0, dimmingDuration: duration)))
			}
        	if(cmds) sendToDevice(cmds)
			
		return
	}
	
	if (device.hasCapability("SwitchLevel")) {		
		List<hubitat.zwave.Command> cmds = []
			if (state.universalDriverData.ZwaveClassVersions?.get("38") < 1)
			{
				cmds.add(secure(zwave.switchMultilevelV1.switchMultilevelSet(value: level)))
				log.warn "${device.displayName} does not support dimming duration settting command. Defaulting to dimming duration set by device parameters."
			} else {
				cmds.add(secure(zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: duration)))
			}
        	if(cmds) sendToDevice(cmds)

		}
	if (logEnable) log.debug "Current switch value is ${device.currentValue("switch")}"
	if (device.currentValue("switch") == "off")
		{	
			if (logEnable) log.debug "Turning switch on in setlevel function"
			sendEvent(name: "switch", value: "on", descriptionText: "Device ${device.displayName} turned on", type: "digital", isStateChange: true)
		}
		
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
	if (cmds) sendToDevice(cmds)

}
////////////////  Send Button Events Resulting from Capabilities Processing /////////////

void sendButtonEvent(action, button, type){
    String descriptionText = "${device.displayName} button ${button} was ${action} [${type}]"
    if (txtEnable) log.info descriptionText
    sendEvent(name:action, value:button, descriptionText:descriptionText, isStateChange:true, type:type)
}

void push(button){
    sendButtonEvent("pushed", button, "digital")
}

void hold(button){
    sendButtonEvent("held", button, "digital")
}

void release(button){
    sendButtonEvent("released", button, "digital")
}

void doubleTap(button){
    sendButtonEvent("doubleTapped", button, "digital")
}
