/*
Operation Sequence ...

1. Get the device's firmware using: getFirmwareVersionFromDevice(), store it in state.parameterTool.firmware.[main: ##, sub: ##]
2. Get the device's database record using: getDeviceDataFromDatabase


*/

metadata {
        definition (name: "Super Parameter Setting Tool",namespace: "jvm", author: "jvm") {
        
		capability "Initialize"
        
    	command "getDeviceDataFromDatabase"
		
		// All data used by this driver is stored as a Map (more particulaly, as a Map of Maps) 
		// in the state variable "state.parameterTool". The "uninstall" command deletes that key
		// to clean up the data before the user changest back to the retular driver
        command "uninstall"
    }
    preferences 
	{
        input name: "advancedEnable", type: "bool", title: "Enable Advanced Configuration", defaultValue: true
        
        if (advancedEnable)
        {
			input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
			input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: true
			state.parameterTool?.zwaveParameters?.each { input it.value.input }
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
	
    if (logEnable) log.debug " manufacturer: ${manufacturer}, deviceType: ${deviceType}, deviceID: ${deviceID}, Version: ${state.parameterTool.firmware.main}, SubVersion: ${state.parameterTool.firmware.sub}"

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
    
            Boolean aboveMinimumVersion = (state.parameterTool.firmware.main > minMainVersion) || ((state.parameterTool.firmware.main == minMainVersion) && (state.parameterTool.firmware.sub >= minSubVersion))
			
            Boolean belowMaximumVersion = (state.parameterTool.firmware.main < maxMainVersion) || ((state.parameterTool.firmware.main == maxMainVersion) && (state.parameterTool.firmware.sub <= maxSubVersion))
            
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
        state.parameterTool.zwaveParameters.put(it.param_id, [input: newInput])
    }
    if (logEnable) log.debug newData
}

//////////////////////////////////////////////////////////////////////
//////      Initialiation, update, and uninstall sequence          ///////
////////////////////////////////////////////////////////////////////// 
void initialize()
{
    if(!state.parameterTool)  state.parameterTool = [:] 
    if(!state.parameterTool.zwaveParameters) state.parameterTool.zwaveParameters =[:]
    getFirmwareVersionFromDevice() // sets the firmware version in state.parameterTool.firmware[main: ??,sub: ??]
    getDeviceDataFromDatabase()
	pollDevicesForCurrentValues()
}

void uninstall()
{
    state.remove("parameterTool")
}


void updated()
{
	if (logEnable) log.debug "Updated function called"

	/*
	state.parameterTool.zwaveParameters is arranged in key : value pairs.
	key is the parameter #
	value is a map of "input" controls, which is arranged under the sub-key "input"
	so values are accessed as v.[input:[defaultValue:0, name:configParam004, parameterSize:1, options:[0:Normal, 1:Inverted], description:Controls the on/off orientation of the rocker switch, title:(4) Orientation, type:enum]]
	*/
	state.parameterTool.zwaveParameters.each { k , v -> 

        if ((v.lastRetrievedValue as Integer) != (settings.get(v.input.name) as Integer) )
        { 
		
			log.debug "Parameter ${k} Last retrieved value ${v.lastRetrievedValue}, requested settings value ${settings.get(v.input.name)}"
			setParameter(k, v.input.parameterSize, settings.get(v.input.name) ) 
        }
		else
		{
			log.debug "Parameter ${k} is unchanged with value ${settings.get(v.input.name)}"
		}
     }
}

//////////////////////////////////////////////////////////////////////
///////        Set, Get, and Process Parameter Values         ////////
////////////////////////////////////////////////////////////////////// 

void getParameterValue(parameterNumber)
{
 	log.debug "Getting value of parameter ${parameterNumber}"

    List<hubitat.zwave.Command> cmds=[]	
		cmds.add(secure(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber as Integer)))
	if (cmds) sendToDevice(cmds)
}

void setParameter(parameterNumber, parameterSize, value){
	log.debug "Setting parameter ${parameterNumber}, of size ${parameterSize} to value ${value}."
    if (parameterNumber.is( null ) || parameterSize.is( null ) || value.is( null )) {
		log.warn "incomplete parameter list supplied..."
		log.warn "syntax: setParameter(parameterNumber,parameterSize,value)"
		return
    } 

	List<hubitat.zwave.Command> cmds=[]

	cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: value as Integer, parameterNumber: parameterNumber as Integer, size: parameterSize as Integer)))
	cmds.add "delay 500"
	cmds.add(secure(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber as Integer)))
	
	if (cmds) sendToDevice(cmds)


}

void pollDevicesForCurrentValues()
{
	// On startup, this should poll all the devices for their initial values!
	state.parameterTool.zwaveParameters.each { k , v -> 
		log.info "getting for key ${k}"
		getParameterValue(k) 
		}
}

void zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	
	log.debug "Received configuration report ${cmd} with scaled value ${cmd.scaledConfigurationValue}"

		String parameterName = "configParam${"${cmd.parameterNumber}".padLeft(3, "0")}"
		def currentValue = settings[parameterName]
		def reportedValue  = cmd.scaledConfigurationValue
    
		if (currentValue != reportedValue)
		{
			settings[parameterName] = reportedValue
		}
		state.parameterTool.zwaveParameters["$cmd.parameterNumber"].lastRetrievedValue = cmd.scaledConfigurationValue
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
    if(!state.parameterTool.firmware)
	{
        state.parameterTool.firmware = [:]
		def deviceVersion = "${device.getDataValue("firmwareVersion")}".split("\\.")
		if(deviceVersion)
		{
			state.parameterTool.firmware.main =deviceVersion[0].toInteger()
			state.parameterTool.firmware.sub = deviceVersion[1].toInteger()
			if (logEnable) log.debug "Firmware Version is: ${state.parameterTool.firmware}"
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
       if (logEnable) log.debug "Firmware Version already exist: ${state.parameterTool.firmware}"
    }
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
    if (logEnable) log.debug "For ${device.displayName}, Received V3 version report: ${cmd}"
	if (state.parameterTool == null) state.parameterTool = [:]
    state.parameterTool.put("firmware", [main: cmd.firmware0Version, sub:cmd.firmware0SubVersion])
	
	if (logEnable) log.debug "state is: ${state }, parameterTool is ${state.parameterTool}"

}
//////////////////////////////////////////////////////////////////////
//////                  Z-Wave Helper Functions                ///////
//////   Format messages, Send to Device, secure Messages      ///////
////////////////////////////////////////////////////////////////////// 

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    // hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)

	Map parseMap = state.commandVersions.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
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


