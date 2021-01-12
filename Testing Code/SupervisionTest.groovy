
metadata {
	definition (name: "Supervision Tester",namespace: "jvm", author: "jvm") {

		command "test"
    }
}

void parse(String description) {
	hubitat.zwave.Command cmd = zwave.parse(description, parseMap)
    if (cmd) { zwaveEvent(cmd) }
}

void sendToDevice(List<hubitat.zwave.Command> cmds) { sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds), hubitat.device.Protocol.ZWAVE)) }
void sendToDevice(hubitat.zwave.Command cmd) { sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE)) }
void sendToDevice(String cmd) { sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE)) }
String secure(String cmd){ return zwaveSecureEncap(cmd) }
String secure(hubitat.zwave.Command cmd){ return zwaveSecureEncap(cmd) }
List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) { return delayBetween(cmds.collect{ it }, delay) }


void test()
{
   Integer session = 32 * Math.random() // For testing purposes, us a random number for Supervision session ID testing!
	
    def cmd = zwave.basicV1.basicSet(value:50)
    log.debug "Command is: " + cmd
    log.debug "Formatted original command: " + cmd.format() // This works fine.
	
    def supervised = zwave.supervisionV1.supervisionGet(sessionID: session , statusUpdates: false, commandClassIdentifier: cmd.commandClassId , commandIdentifier: cmd.commandId , commandLength: (2 + cmd.payload.size()), commandByte: cmd.payload)
    log.debug "Supervised Command is: " + supervised
    log.debug "Formatted Supervised Command is: " + supervised.format()
	
    def secured = secure(supervised)
    log.debug "Supervised command after security encapsulation: "  + secured
    log.debug "Formatted secured command: " + secured.format() // This generates a null pointer error!
	
    sendToDevice(secured)
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    log.debug "For ${device.displayName}, skipping command: ${cmd}"
}

//////////////////////////////////////////////////////////////////////
//////                  Handle Supervision request            ///////
////////////////////////////////////////////////////////////////////// 
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    log.debug "Device ${device.displayName}: Supervision get: ${cmd}"
	

    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand([:])
	
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
    sendToDevice(secure((new hubitat.zwave.commands.supervisionv1.SupervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0))))
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionReport cmd) {
    log.debug "Device ${device.displayName}: Received a Supervision Report: ${cmd}"
}
	

