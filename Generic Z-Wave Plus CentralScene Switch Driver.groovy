/*
*	Zen21 Central Scene Switch
*	version: 1.1
*	Originally from: https://github.com/dds82/hubitat/blob/master/Drivers/zooz/zen21-switch.groovy
*/

import groovy.transform.Field
@Field def driverVersion = 0.1

metadata {
    definition (name: "Testing Zwave Plus Central Scene Switch", namespace: "jvm", author:"jvm") {
        capability "Switch"
        capability "Refresh"
        capability "Actuator"
        capability "Sensor"
        capability "Configuration"
        capability "PushableButton"
        capability "HoldableButton"
        capability "ReleasableButton"
        capability "DoubleTapableButton"	
		capability "Initialize"		
        // capability "Indicator"
		
		command "push", ["NUMBER"]
        command "hold", ["NUMBER"]
        command "release", ["NUMBER"]
        command "doubleTap", ["NUMBER"]
		

        fingerprint mfr:"027A", prod:"B111", deviceId:"1E1C", inClusters:"0x5E,0x25,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x5B,0x6C,0x9F,0x7A", deviceJoinName: "Zooz Zen21 Switch" //US
        fingerprint inClusters:"0x5E,0x25,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x5B,0x6C,0x9F,0x7A", deviceJoinName: "HomeSeer HS-WS100+" //US

		

    }
    preferences {
        configParams.each { input it.value.input }
        // input name: "associationsG2", type: "string", description: "To add nodes to associations use the Hexidecimal nodeID from the z-wave device list separated by commas into the space below", title: "Associations Group 2"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: false
    }
}
@Field static Map configParams = [
        1: [input: [name: "configParam1", type: "enum", title: "On/Off Paddle Orientation", description: "", defaultValue: 0, options: [0:"Normal",1:"Reverse",2:"Any paddle turns on/off"]], parameterSize: 1],
        // 2: [input: [name: "configParam2", type: "enum", title: "LED Indicator Control", description: "", defaultValue: 0, options: [0:"Indicator is on when switch is off",1:"Indicator is on when switch is on",2:"Indicator is always off",3:"Indicator is always on"]], parameterSize: 1],
        // 3: [input: [name: "configParam3", type: "enum", title: "Auto Turn-Off Timer", description: "", defaultValue: 0, options: [0:"Timer disabled",1:"Timer Enabled"]], parameterSize: 1],
        // 4: [input: [name: "configParam4", type: "number", title: "Auto Off Timer", description: "Minutes 1-65535", defaultValue: 60, range:"1..65535"], parameterSize:4],
        // 5: [input: [name: "configParam5", type: "enum", title: "Auto Turn-On Timer", description: "", defaultValue: 0, options: [0:"timer disabled",1:"timer enabled"]],parameterSize:1],
        // 6: [input: [name: "configParam6", type: "number", title: "Auto On Timer", description: "Minutes 1-65535", defaultValue: 60, range:"1..65535"], parameterSize: 4],
        // 7: [input: [name: "configParam7", type: "enum", title: "Association Reports", description: "", defaultValue: 15, options:[0:"none",1:"physical tap on ZEN26 only",2:"physical tap on 3-way switch only",3:"physical tap on ZEN26 or 3-way switch",4:"Z-Wave command from hub",5:"physical tap on ZEN26 or Z-Wave command",6:"physical tap on connected 3-way switch or Z-wave command",7:"physical tap on ZEN26 / 3-way switch / or Z-wave command",8:"timer only",9:"physical tap on ZEN26 or timer",10:"physical tap on 3-way switch or timer",11:"physical tap on ZEN26 / 3-way switch or timer",12:"Z-wave command from hub or timer",13:"physical tap on ZEN26, Z-wave command, or timer",14:"physical tap on ZEN26 / 3-way switch / Z-wave command, or timer", 15:"all of the above"]],parameterSize:1],
        // 8: [input: [name: "configParam8", type: "enum", title: "On/Off Status After Power Failure", description: "", defaultValue: 2, options:[0:"Off",1:"On",2:"Last State"]],parameterSize:1],
        // 9: [input: [name: "configParam9", type: "enum", title: "Enable/Disable Scene Control", defaultValue: 0, options:[0:"Scene control disabled",1:"scene control enabled"]],parameterSize:1],
        // 11: [input: [name: "configParam11", type: "enum", title: "Smart Bulb Mode", defaultValue: 1, options:[0:"physical paddle control disabled",1:"physical paddle control enabled",2:"physical paddle and z-wave control disabled"]],parameterSize: 1],
        // 12: [input: [name: "configParam12", type: "enum", title: "3-Way Switch Type", defaultValue: 0, options:[0:"Normal",1:"Momentary"]],parameterSize:1],
        // 13: [input: [name: "configParam13", type: "enum", title: "Report Type Disabled Physical", defaultValue:0, options: [0:"switch reports on/off status and changes LED indicator state even if physical and Z-Wave control is disabled", 1:"switch doesn't report on/off status or change LED indicator state when physical (and Z-Wave) control is disabled"]], parameterSize:1],
]

// Following works for both Zooz and HomeSeer
@Field static Map CMD_CLASS_VERS=[0x5B:3,0x86:3,0x72:2,0x8E:3,0x85:2,0x59:1,0x70:1]

// Next line is device specific - Devices always have a first group.
// Some may have a second or more.
@Field static int numberOfAssocGroups=1

// @Field static int numberOfAssocGroups=2

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
    if (logEnable) log.debug "parse:${description}"
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
//////////////////////////////////////////////////////////////////////

void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}



List<hubitat.zwave.Command> runConfigs() {
    List<hubitat.zwave.Command> cmds=[]
    configParams.each { param, data ->
        if (settings[data.input.name]) {
            cmds.addAll(configCmd(param, data.parameterSize, settings[data.input.name]))
        }
    }
    return cmds
}


List<hubitat.zwave.Command> configCmd(parameterNumber, size, scaledConfigurationValue) {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: parameterNumber.toInteger(), size: size.toInteger(), scaledConfigurationValue: scaledConfigurationValue.toInteger()))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber.toInteger()))
    return cmds
}




void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    if(configParams[cmd.parameterNumber.toInteger()]) {
        Map configParam=configParams[cmd.parameterNumber.toInteger()]
        int scaledValue
        cmd.configurationValue.reverse().eachWithIndex { v, index -> scaledValue=scaledValue | v << (8*index) }
        device.updateSetting(configParam.input.name, [value: "${scaledValue}", type: configParam.input.type])
    }
}

void eventProcess(Map evt) {
    if (device.currentValue(evt.name).toString() != evt.value.toString() || !eventFilter) {
        evt.isStateChange=true
        sendEvent(evt)
    }
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    if (logEnable) log.debug "Supervision get: ${cmd}"
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
    sendToDevice(new hubitat.zwave.commands.supervisionv1.SupervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0))
}

//////////////////////////////////////////////////////////////////////
//////         Setup functions for Indicator Capability        ///////
////////////////////////////////////////////////////////////////////// 
// The following values are used by Zooz


/*
void indicatorNever() {
    sendToDevice(configCmd(2,1,2))
}

void indicatorWhenOff() {
    sendToDevice(configCmd(2,1,0))
}

void indicatorWhenOn() {
    sendToDevice(configCmd(2,1,1))
}
*/

// The following values are used by HomeSeer WS100
void indicatorNever() {
    sendToDevice(configCmd(3,1,2))
}

void indicatorWhenOff() {
    sendToDevice(configCmd(3,1,0))
}

void indicatorWhenOn() {
    sendToDevice(configCmd(3,1,1))
}

//////////////////////////////////////////////////////////////
//////              Learn About the Device             ///////
////////////////////////////////////////////////////////////// 

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
    if (logEnable) log.debug "Device Specific Report: ${cmd}"
    switch (cmd.deviceIdType) {
        case 1:
            // serial number
            def serialNumber=""
            if (cmd.deviceIdDataFormat==1) {
                cmd.deviceIdData.each { serialNumber += hubitat.helper.HexUtils.integerToHexString(it & 0xff,1).padLeft(2, '0')}
            } else {
                cmd.deviceIdData.each { serialNumber += (char) it }
            }
            device.updateDataValue("serialNumber", serialNumber)
            break
    }
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
    if (logEnable) log.debug "version3 report: ${cmd}"
    device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${cmd.firmware0SubVersion}")
    device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
    device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
}

List<hubitat.zwave.Command> pollConfigs() {
    List<hubitat.zwave.Command> cmds=[]
    configParams.each { param, data ->
        if (settings[data.input.name]) {
            cmds.add(zwave.configurationV1.configurationGet(parameterNumber: param.toInteger()))
        }
    }
    return cmds
}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.versionV3.versionGet())
    cmds.add(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
    cmds.addAll(processAssociations())
    cmds.addAll(pollConfigs())
    sendToDevice(cmds)
}


//////////////////////////////////////////////////////////////////////
//////        Handle Startup and Configuration Tasks           ///////
//////   Refresh, Initialize, Configuration Capabilities       ///////
////////////////////////////////////////////////////////////////////// 

void refresh() {
	if(txtEnable) "Refreshing device status .."
    List<hubitat.zwave.Command> cmds=[]
	
	if( state?.commandVersions?.get('38') > 1 )
	{
		cmds.add(zwave.switchMultilevelV2.switchMultilevelGet())
	}
	else
	{
		cmds.add(zwave.basicV1.basicGet())
	}
    sendToDevice(cmds)
}

void installed() {
    if (logEnable) log.debug "installed ${device.label?device.label:device.name} ..."
	state.clear()
	getZwaveClassVersions()
	pollDeviceData()
	runIn(10,getCentralSceneInfo)
	state.installCompleted = true
}


void configure() {
	if (logEnable) log.debug "Current device data state: ${device}"
	if (logEnable) log.debug "Current state data state: ${state}"
	
	if(state.installCompleted != true )
	{
		installed()
	}
	getZwaveClassVersions()
	runIn(10,getCentralSceneInfo)
	state.configured = true
}

void  initialize()
{
	getZwaveClassVersions()
	
    log.info "Driver Version is ${driverVersion}"
	if (state?.driverVersion != driverVersion)
	{
		log.info "Driver version has changed - redoing configuration and install"
		configure()
		
	}
	
	state.driverVersion = driverVersion
	
	// first run only
    state.initialized = true
	if ( state.configured != true )
	{
	configure()
	}
	
	def time = new Date().getTime()
	
	log.info "Initialize ${device.label?device.label:device.name} at time: ${time}" 
    state.initializedTime = time
    runIn(5, refresh)
}


void updated() {
    log.info "updated ${device.label?device.label:device.name} ..."
    log.warn "debug logging is: ${logEnable == true}"
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    List<hubitat.zwave.Command> cmds=[]
    cmds.addAll(processAssociations())
    cmds.addAll(runConfigs())
    sendToDevice(cmds)
}

//////////////////////////////////////////////////////////////////////
//////        Handle Basic Reports and Device Functions        ///////
////////////////////////////////////////////////////////////////////// 

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (logEnable) log.debug cmd
    switchEvents(cmd)
}

private void switchEvents(hubitat.zwave.Command cmd) {
    String value = (cmd.value ? "on" : "off")
    String description = "${device.displayName} was turned ${value}"
    if (txtEnable) log.info description
    eventProcess(name: "switch", value: value, descriptionText: description, type: state.isDigital ? "digital" : "physical")
    state.isDigital=false
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (logEnable) log.debug cmd
    switchEvents(cmd)
}


void on() {
    state.isDigital=true
	List<hubitat.zwave.Command> cmds = []
    cmds.add(secure(zwave.basicV1.basicSet(value: 0xFF)))
	cmds.add(secure(zwave.basicV1.basicGet()))	
	sendToDevice(cmds)
}

void off() {
    state.isDigital=true
	List<hubitat.zwave.Command> cmds = []
    cmds.add(secure(zwave.basicV1.basicSet(value: 0x00)))
	cmds.add(secure(zwave.basicV1.basicGet()))	
	sendToDevice(cmds)
}

//////////////////////////////////////////////////////////////////////
////////////        Handle Z-Wave Associations        ////////////////
////////////////////////////////////////////////////////////////////// 

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationGroupingsReport cmd) {
    if (logEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
    log.info "${device.label?device.label:device.name}: Supported association groups: ${cmd.supportedGroupings}"
    state.associationGroups = cmd.supportedGroupings
}


List<hubitat.zwave.Command> setDefaultAssociation() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId))
    cmds.add(zwave.associationV2.associationGet(groupingIdentifier: 1))
    return cmds
}

List<hubitat.zwave.Command> processAssociations(){
    List<hubitat.zwave.Command> cmds = []
    cmds.addAll(setDefaultAssociation())
    for (int i = 2; i<=numberOfAssocGroups; i++) {
        if (logEnable) log.debug "group: $i dataValue: " + getDataValue("zwaveAssociationG$i") + " parameterValue: " + settings."associationsG$i"
        String parameterInput=settings."associationsG$i"
        List<String> newNodeList = []
        List<String> oldNodeList = []
        if (getDataValue("zwaveAssociationG$i") != null) {
            getDataValue("zwaveAssociationG$i").minus("[").minus("]").split(",").each {
                if (it != "") {
                    oldNodeList.add(it.minus(" "))
                }
            }
        }
        if (parameterInput != null) {
            parameterInput.minus("[").minus("]").split(",").each {
                if (it != "") {
                    newNodeList.add(it.minus(" "))
                }
            }
        }
        if (oldNodeList.size > 0 || newNodeList.size > 0) {
            if (logEnable) log.debug "${oldNodeList.size} - ${newNodeList.size}"
            oldNodeList.each {
                if (!newNodeList.contains(it)) {
                    // user removed a node from the list
                    if (logEnable) log.debug "removing node: $it, from group: $i"
                    cmds.add(zwave.associationV2.associationRemove(groupingIdentifier: i, nodeId: Integer.parseInt(it, 16)))
                }
            }
            newNodeList.each {
                cmds.add(zwave.associationV2.associationSet(groupingIdentifier: i, nodeId: Integer.parseInt(it, 16)))
            }
        }
        cmds.add(zwave.associationV2.associationGet(groupingIdentifier: i))
    }
    if (logEnable) log.debug "processAssociations cmds: ${cmds}"
    return cmds
}


void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
    if (logEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
    List<String> temp = []
    if (cmd.nodeId != []) {
        cmd.nodeId.each {
            temp.add(it.toString().format( '%02x', it.toInteger() ).toUpperCase())
        }
    }
    updateDataValue("zwaveAssociationG${cmd.groupingIdentifier}", "$temp")
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

///////////////////////////////////////////////////////////////////////////////////////////////
////////////        Learn the Z-Wave Class Versions Actually Implemented        ////////////////
/////////////////////////////////////////////////////////////////////////////////////////////// 


void   getZwaveClassVersions(){
    List<hubitat.zwave.Command> cmds = []
	
	List<Integer> ic = getDataValue("inClusters").split(",").collect{ hexStrToUnsignedInt(it) }
    ic.each {
	
		if (it) 
		{
			if (!state.commandVersions.get(it as String))
			{
			log.info "Requesting Command class version for class ${it}"

			cmds.add(zwave.versionV3.versionCommandClassGet(requestedCommandClass:it.toInteger()))
			}
		}
    }
    if(cmds) sendToDevice(cmds)
}


void zwaveEvent(hubitat.zwave.commands.versionv1.VersionCommandClassReport cmd) {
    log.info "CommandClassReport- class:${ "0x${intToHexStr(cmd.requestedCommandClass)}" }, version:${cmd.commandClassVersion}"	

    if (state.commandVersions == undefined) state.commandVersions = [:]
    
    state.commandVersions.put((cmd.requestedCommandClass).toInteger(), (cmd.commandClassVersion).toInteger())    
	
}
///////////////////////////////////////////////////////////////////////////////////////////////
///////////////                  Central Scene Processing          ////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////

void getCentralSceneInfo()
{
    List<hubitat.zwave.Command> cmds = []
	
	switch(state.commandVersions.get('91'))
	{
	case 1:
		cmds.add( zwave.centralSceneV1.centralSceneSupportedGet() )
		break 
	case 2:
		cmds.add( zwave.centralSceneV2.centralSceneSupportedGet() )
		break 
	case 3:
		cmds.add( zwave.centralSceneV3.centralSceneSupportedGet() )
		break
	}
	
	sendToDevice(cmds)

}
void setButtonCount( cmd )
{
	if ( cmd.identical != true) log.warn "Central Scene Code not configured to handle non-identical scene buttons"
	
	int buttons
	if (cmd.supportedKeyAttributes[0].keyPress5x == true)
	{
		buttons = ( cmd.supportedScenes * 5)
	}
	else if (cmd.supportedKeyAttributes[0].keyPress4x == true)
	{
		buttons = ( cmd.supportedScenes * 4)
	}
	else if (cmd.supportedKeyAttributes[0].keyPress3x == true)
	{
		buttons = ( cmd.supportedScenes * 3)
	}
	else if (cmd.supportedKeyAttributes[0].keyPress2x == true)
	{
		buttons = ( cmd.supportedScenes * 2)
	}
	else  if (cmd.supportedKeyAttributes[0].keyPress1x == true)
	{
		buttons = ( cmd.supportedScenes * 1)
	}
    sendEvent(name: "numberOfButtons", value: buttons)
}

void zwaveEvent(hubitat.zwave.commands.centralscenev2.CentralSceneSupportedReport  cmd) {
    log.debug "Central Scene V2 Supported Report Info ${cmd}"	
	state.centralScene = cmd
	setButtonCount(cmd)

}
	
void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneSupportedReport  cmd) {
    log.debug "Central Scene V3 Supported Report Info ${cmd}"	
	state.centralScene = cmd
	setButtonCount(cmd)
	}
	

	
// This next 2 functions operates as a backup in case a release report was lost on the network
// It will force a release to be sent if there has been a hold event and then
// a release has not occurred within the central scene hold button refresh period.
// The central scene hold button refresh period is 200 mSec for old devices (state.slowRefresh == false), else it is 55 seconds.
// Thus, adding in extra time for network delays, etc., this forces release after either 1 or 60 seconds 
void forceReleaseHold01(){
    if (state.Button_1_LastState == "held")
	{
		// only need to force a release hold if the button state is "held" when the timer expires
		log.warn "Central Scene Release message not received before timeout - Faking a release message!"
		sendEvent(name:"released", value:1 , type:"digital", isStateChange:true)
		state.Button_1_LastState == "released"
	}
}
void forceReleaseHold02(){
    if (state.Button_2_LastState == "held")
	{
		// only need to force a release hold if the button state is "held" when the timer expires
		log.warn "Central Scene Release message not received before timeout - Faking a release message!"
		sendEvent(name:"released", value:2 , type:"digital", isStateChange:true)
		state.Button_2_LastState == "released"
	}
}

int tapCount(attribute)
{
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

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd) {
    Map evt = [type:"physical", isStateChange:true]
	if(logEnable) log.debug "Received Central Scene Notification ${cmd}"
	
	def taps = tapCount(cmd.keyAttributes)
	
	if(logEnable) log.debug "Mapping of key attributes to Taps: ${taps}"
	
    if (cmd.sceneNumber==1) {

		switch(taps)
		{
			case -1:		
				evt.name = "released" 
				evt.value = cmd.sceneNumber
				evt.descriptionText="${device.displayName} button ${evt.value} released"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)
				break

			case -2:	
				evt.name = "held" 
				evt.value = cmd.sceneNumber

				
					if (state."Button_${cmd.sceneNumber}_LastState" != "held")
					{
						evt.descriptionText="${device.displayName} button ${evt.value} held"
						if (txtEnable) log.info evt.descriptionText
				
						state."Button_${cmd.sceneNumber}_LastState" = evt.name
						sendEvent(evt)
						// Force a release hold if you don't get a refresh within the slow refresh period!
						runIn( 60,forceReleaseHold01)		   
					}
					else
					{
						if (txtEnable) log.info "Still Holding button ${cmd.sceneNumber}"
						runIn( 60,forceReleaseHold01)		   
					}	
				break
				
			case 1:
				evt.name = "pushed"
				evt.value= 1
				evt.descriptionText="${device.displayName} button ${evt.value} pushed"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)
				break				
	 
			case 2:
				evt.name = "pushed" 
				evt.value=3
				evt.descriptionText="${device.displayName} button ${evt.value} pushed"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)
				evt.name = "doubleTapped" 
				evt.value=1
				evt.descriptionText="${device.displayName} button ${cmd.sceneNumber} doubleTapped"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)			
				break
			
			case 3:
				evt.name = "pushed"
				evt.value=5
				evt.descriptionText="${device.displayName} button ${evt.value} pushed"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)
				break
			
			case 4:
				evt.name = "pushed"
				evt.value=7
				evt.descriptionText="${device.displayName} button ${evt.value} pushed"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)
				break
			
			case 5:
				evt.name = "pushed"
				evt.value=9
				evt.descriptionText="${device.displayName} button ${evt.value} pushed"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)
				break
		}
    } else if (cmd.sceneNumber==2) {
		switch(taps)
		{
			case -1:		
				evt.name = "released" 
				evt.value = cmd.sceneNumber
				evt.descriptionText="${device.displayName} button ${evt.value} released"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)
				break
				
			case -2:	
				evt.name = "held" 
				evt.value = cmd.sceneNumber
				
					if (state."Button_${cmd.sceneNumber}_LastState" != "held")
					{
						evt.descriptionText="${device.displayName} button ${evt.value} held"
						if (txtEnable) log.info evt.descriptionText
				
						state."Button_${cmd.sceneNumber}_LastState" = evt.name
						sendEvent(evt)
						// Force a release hold if you don't get a refresh within the slow refresh period!
						runIn( 60,forceReleaseHold02)		   
					}
					else
					{
						if (txtEnable) log.info "Still Holding button ${cmd.sceneNumber}"
						runIn( 60,forceReleaseHold02)		   
					}	
				break	
				
			case 1:
				evt.name = "pushed"
				evt.value=2
				evt.descriptionText="${device.displayName} button ${evt.value} pushed"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)
				break
	 
			case 2:
				evt.name = "pushed" 
				evt.value=4
				evt.descriptionText="${device.displayName} button ${evt.value} pushed"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)
				evt.name = "doubleTapped" 
				evt.value=1
				evt.descriptionText="${device.displayName} button ${cmd.sceneNumber} doubleTapped"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)			
				break
			
			case 3:
				evt.name = "pushed"
				evt.value=6
				evt.descriptionText="${device.displayName} button ${evt.value} pushed"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)
				break
			
			case 4:
				evt.name = "pushed"
				evt.value=8
				evt.descriptionText="${device.displayName} button ${evt.value} pushed"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)
				break
			
			case 5:
				evt.name = "pushed"
				evt.value=10
				evt.descriptionText="${device.displayName} button ${evt.value} pushed"
				if (txtEnable) log.info evt.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = evt.name
				sendEvent(evt)
				break
		}
    } else {
		log.warn "Central Scene number ${cmd.sceneNumber} received, but code only supports scenes 1 and 2"
	}
}

