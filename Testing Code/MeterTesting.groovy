metadata {
	definition (name: "Metering Test and Development",namespace: "jvm", author: "jvm") {
		capability "Initialize"

		capability "EnergyMeter"
		capability "PowerMeter"
		capability "VoltageMeasurement"
	
		command "meterReset"
		command "meterRefresh"

    }
    preferences 
	{
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: true
	}
}

//////////////////////////////////////////////////////////////////////
//////        Handle Meter Reports and Related Functions        ///////
////////////////////////////////////////////////////////////////////// 

void meterSupportedGet()
{
	List<hubitat.zwave.Command> cmds = []
    cmds << zwave.meterV3.meterSupportedGet()
	if (cmds) sendToDevice(cmds)
}
void meterReset() {
    if (txtEnable) log.info "${device.label?device.label:device.name}: Resetting energy statistics"
	List<hubitat.zwave.Command> cmds = []
    cmds << zwave.meterV3.meterReset()
	if (cmds) sendToDevice(cmds)
}

void meterRefresh() {
    if (txtEnable) log.info "${device.label?device.label:device.name}: refresh()"
	List<hubitat.zwave.Command> cmds = []
    cmds << zwave.meterV3.meterGet(scale: 0)
	//	cmds << zwave.meterV3.meterGet(scale: 1)
	cmds << zwave.meterV3.meterGet(scale: 2)
	//	cmds << zwave.meterV3.meterGet(scale: 3)
	cmds << zwave.meterV3.meterGet(scale: 4)
	cmds << zwave.meterV3.meterGet(scale: 5)
	//	cmds << zwave.meterV3.meterGet(scale: 6)
	//	cmds << zwave.meterV3.meterGet(scale: 7)

	if (cmds) sendToDevice(cmds)
}

void zwaveEvent(hubitat.zwave.commands.meterv2.MeterSupportedReport cmd) {
	log.warn "Meter Supported Report V2 command not implemented! Contents are: ${cmd}"
}
void zwaveEvent(hubitat.zwave.commands.meterv3.MeterSupportedReport cmd) {
	log.warn "Meter Supported Report V3 command not implemented! Contents are: ${cmd}"
}
void zwaveEvent(hubitat.zwave.commands.meterv4.MeterSupportedReport cmd) {
	log.warn "Meter Supported Report V4 command not implemented! Contents are: ${cmd}"
}
void zwaveEvent(hubitat.zwave.commands.meterv5.MeterSupportedReport cmd) {
	log.warn "Meter Supported Report V5 command not implemented! Contents are: ${cmd}"
}

void zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd) {
    if (logEnable) log.debug "Meter Report V3 for ${device.label?device.label:device.name} full contents are: ${cmd}"
	if (cmd.rateType != 1) log.warn "Unexpected Meter rateType received. Value is: ${cmd.rateType}."
	if (cmd.meterType == 1)
	{
		switch (cmd.scale as Integer)
		{
		case 0: // kWh
			sendEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh", isStateChange: true )
			if (txtEnable) log.info "${device.label?device.label:device.name}: Energy report received with value of ${cmd.scaledMeterValue} kWh"
			break
            
		case 1: // kVAh
			log.warn "Received a meter report with unsupported type: kVAh."
            break
            
		case 2: // W
			sendEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W", isStateChange: true )
			if (txtEnable) log.info "${device.label?device.label:device.name}: Power report received with value of ${cmd.scaledMeterValue} W"
			break	
            
		case 3: // Pulse Count
 			log.warn "Received a meter report with unsupported type: Pulse Count."
           break
            
		case 4: // V
			sendEvent(name: "voltage", value: cmd.scaledMeterValue, unit: "V", isStateChange: true )
			if (txtEnable) log.info "${device.label?device.label:device.name}: Voltage report received with value of ${cmd.scaledMeterValue} V"
			break
            
		case 5: // A
			sendEvent(name: "amperage", value: cmd.scaledMeterValue, unit: "A", isStateChange: true )
			if (txtEnable) log.info "${device.label?device.label:device.name}: Amperage report received with value of ${cmd.scaledMeterValue} A"
			break
            
		case 6: // Power Factor
			log.warn "Received a meter report with unsupported type: Power Factor."
            break
            
		case 7: // M.S.T. - More Scale Types
 			log.warn "Received a meter report with unsupported type: M.S.T."
           break
		}
	}
	else{
	log.warn "Received unexpected meter type for ${device.label?device.label:device.name}. Received type: ${cmd.meterType}"
	}
}

//////////////////////////////////////////////////////////////////////
//////      Initialization, update, and uninstall sequence          ///////
////////////////////////////////////////////////////////////////////// 
void initialize()
{
	meterSupportedGet()
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
	Map parseMap = [0x32:3]

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
