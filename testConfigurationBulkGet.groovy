/*
*Simple Bulk Get Tester
*/

import groovy.transform.Field
@Field def driverVersion = 0.1

metadata {
    definition (name: "Testing Configuration Bulk Get", namespace: "jvm", author:"jvm") {

        capability "Configuration"
    }

}


@Field static Map CMD_CLASS_VERS=[0x70:2]
// 0x70 = 112 == 

//////////////////////////////////////////////////////////////////////
//////     code to Test Configuration Bulk Get Capabilities   ///////
////////////////////////////////////////////////////////////////////// 


void zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationBulkReport cmd) {
log.debug "Configuration Buk Report ${cmd}"
}

void getBulk()
{
    List<hubitat.zwave.Command> cmds = []

    cmds.add(zwave.configurationV2.configurationBulkGet(numberOfParameters:4, parameterOffset: 11))
    sendToDevice( cmds )
}




//////////////////////////////////////////////////////////////////////
//////        Handle Startup and Configuration Tasks           ///////
//////   Refresh, Initialize, Configuration Capabilities       ///////
////////////////////////////////////////////////////////////////////// 



void configure() {
getBulk()
}


//////////////////////////////////////////////////////////////////////
//////                  Z-Wave Helper Functions                ///////
//////   Format messages, Send to Device, secure Messages      ///////
////////////////////////////////////////////////////////////////////// 

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void parse(String description) {
    log.debug "parse:${description}"
    hubitat.zwave.Command cmd = zwave.parse(description, CMD_CLASS_VERS)
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

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) {
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
    if (logEnable) log.debug "skip:${cmd}"
}
