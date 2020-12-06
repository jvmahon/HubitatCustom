metadata {
        definition (name: "Firmware Get Failure",namespace: "jvm", author: "jvm") {
	
		capability "Initialize"

		command "EraseState"

    }
}

// Firmware version gets used in the next function
void getDeviceDataFromDatabase()
{
    log.debug " manufacturer: ${manufacturer}, deviceType: ${deviceType}, deviceID: ${deviceID}, Version: ${state.firmware.main}, SubVersion: ${state.firmware.sub}"
}

void EraseState()
{
	state.clear()
}

void initialize()
{
    getFirmwareVersionFromDevice() // sets the firmware version in state.firmware[main: ??,sub: ??]
	pauseExecution(2000)
	getDeviceDataFromDatabase()
}


//////////////////////////////////////////////////////////////////////
//////                  Get Device Firmware Version            ///////
////////////////////////////////////////////////////////////////////// 
void queryForFirmwareReport()
{
	log.debug "Querying for firmware report"

    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.versionV3.versionGet())
    sendToDevice(cmds)
}

void getFirmwareVersionFromDevice()
{
	log.debug "Calling getFirmwareVersionFromDevice with !state.firmware ${!state.firmware} and its value is: ${state.get("firmware")}"
    if(!(state.firmware && state?.firmware.main && state?.firmware.sub))
	{
		queryForFirmwareReport()
	}
}

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    log.debug "For ${device.displayName}, Received V1 version report: ${cmd}"
	if (! state.firmware) state.firmware = [:]
	state.put("firmware", [main: cmd.applicationVersion, sub: cmd.applicationSubVersion])
	log.info "Firmware version is: ${state.get("firmware")}"
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
    log.debug "For ${device.displayName}, Received V2 version report: ${cmd}"
	if (! state.firmware) state.firmware = [:]
	state.put("firmware", [main: cmd.firmware0Version, sub: cmd.firmware0SubVersion])
	log.info "Firmware version is: ${state.get("firmware")}"
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
    log.debug "For ${device.displayName}, Received V3 version report: ${cmd}"
	if (! state.firmware) state.firmware = [:]
	state.put("firmware", [main: cmd.firmware0Version, sub: cmd.firmware0SubVersion])
	log.info "Firmware version is: ${state.get("firmware")}"
}

//////////////////////////////////////////////////////////////////////
//////                  Z-Wave Helper Functions                ///////
//////   Format messages, Send to Device, secure Messages      ///////
////////////////////////////////////////////////////////////////////// 

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    // hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)

	Map parseMap = state.ZwaveClassVersions?.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(parseMap)
	
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void parse(String description) {
    // if (logEnable) log.debug "For ${device.displayName}, parse:${description}"
	Map parseMap = state.ZwaveClassVersions?.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}

    hubitat.zwave.Command cmd = zwave.parse(description, parseMap)

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

