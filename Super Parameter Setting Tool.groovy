import groovy.json.JsonSlurper

metadata {
        definition (name: "Super Parameter Tool",namespace: "jvm", author: "jvm") {
           capability "Initialize"
        
    	command "getParameterInfo"
        command "uninstall"
    }
    preferences 
	{
        input name: "advancedEnable", type: "bool", title: "Enable Advanced Configuration", defaultValue: false
        
        if (advancedEnable)
        {
			input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
			input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: false
			state.parameterTool?.zwaveParameters?.each { input it.value.input }

        }
    }
}

void getParameterInfo()
{
log.debug "getting Parameter information"
  
	String manufacturer = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("manufacturer").toInteger(), 2)
	String deviceType = 	hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceType").toInteger(), 2)
	String deviceID = 		hubitat.helper.HexUtils.integerToHexString( device.getDataValue("deviceId").toInteger(), 2)
	
    log.debug " manufacturer: ${manufacturer}, deviceType: ${deviceType}, deviceID: ${deviceID}, Version: ${state.parameterTool.firmware.main}, SubVersion: ${state.parameterTool.firmware.sub}"

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
            // log.info "Response Data: ${resp.data.parameters}"
			log.info "Processing data for device model: ${resp.data?.label}, Manufacturer: ${resp.data?.manufacturer?.label}"
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


void initialize()
{

    if(!state.parameterTool)  state.parameterTool = [:] 
    if(!state.parameterTool.zwaveParameters) state.parameterTool.zwaveParameters =[:]
    getFirmwareVersion() // sets the firmware version in state.parameterTool.firmware[main: ??,sub: ??]
    getParameterInfo()
}

void uninstall()
{
    state.remove("parameterTool")
}


void updated()
{
log.info "Updated function called"

settings.each{ log.info "setting: ${it}"}

state.parameterTool.zwaveParameters.each{ k , v -> log.info "Key: ${k}, Value: ${v}"}
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

void getFirmwareVersion()
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
	
	log.debug "state is: ${state }, parameterTool is ${state.parameterTool}"

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

void setParameter(parameterNumber = null, size = null, value = null){
    List<hubitat.zwave.Command> cmds=[]
    if (parameterNumber == null || size == null || value == null) {
		log.warn "incomplete parameter list supplied..."
		log.info "syntax: setParameter(parameterNumber,size,value)"
		return
    } 
	
	cmds.add(secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: parameterNumber, size: size)))
	cmds.add(secure(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber)))
	sendToDevice(cmds)
}

//////////////////////////////////////////////////////////////////////


