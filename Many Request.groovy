metadata {
        definition (name: "Many Configuration Request",namespace: "jvm", author: "jvm") {
		capability "Initialize"
		capability "Refresh"
		
		command "cleanup"
    }
    preferences 
	{
        input name: "advancedEnable", type: "bool", title: "Enable Advanced Configuration", defaultValue: true
        
        if (advancedEnable)
        {
			input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
			input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: true
        }
    }
}

//////////////////////////////////////////////////////////////////////
//////      Initialization, update, and uninstall sequence          ///////
////////////////////////////////////////////////////////////////////// 
void refresh() {
	if(txtEnable) "Refreshing device ${device.displayName} status .."
    initialize()
}

void initialize()
{	
	if (logEnable) log.debug "Initialize function called"

	getZwaveClassVersions()
}

void updated()
{
	if (logEnable) log.debug "Updated function called"
}

void cleanup()
{
	state.remove("inclustersAdvanced")
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

	state.inclustersAdvanced = [:]
	
	if(logEnable) log.debug "Current Command Class version state is: ${state.inclustersAdvanced}"
	
	// All the inclusters suppored by the device
	List<Integer> deviceInclusters = getDataValue("inClusters").split(",").collect{ hexStrToUnsignedInt(it) as Integer }
		deviceInclusters << 32 // add Basic!

    deviceInclusters.each {
			if(logEnable) log.debug "Requesting Command class version for class 0x${intToHexStr(it)}"
			cmds.add(zwave.versionV3.versionCommandClassGet(requestedCommandClass:it.toInteger()))
    }
    if(cmds) sendToDevice(cmds)
}

//////////////////////////////////////////////////////////////////////
//////                 Process command class report           ///////
////////////////////////////////////////////////////////////////////// 

// There are 3 versions of command class reports - could just include only the highest and let Groovy resolve, but this 

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionCommandClassReport cmd) {
	if(logEnable) log.debug "Processing command class report V1 for received command ${cmd}."
	state.inclustersAdvanced.put((cmd.requestedCommandClass as String), (cmd.commandClassVersion as Integer))
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionCommandClassReport cmd) {
	if(logEnable) log.debug "Processing command class report V2 for received command ${cmd}."
	state.inclustersAdvanced.put((cmd.requestedCommandClass as String), (cmd.commandClassVersion as Integer))
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionCommandClassReport cmd) {
	if(logEnable) log.debug "Processing command class report V3 for received command ${cmd}."
	state.inclustersAdvanced.put(cmd.requestedCommandClass, cmd.commandClassVersion)

}

//////////////////////////////////////////////////////////////////////
//////                  Z-Wave Helper Functions                ///////
//////   Format messages, Send to Device, secure Messages      ///////
////////////////////////////////////////////////////////////////////// 

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    // hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)

	Map parseMap = state.inclustersAdvanced?.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
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

