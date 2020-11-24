import groovy.transform.Field
@Field def driverVersion = 0.14
if(state.commandVersions == null) state.commmandVersions = [:]
       

metadata {
    definition (name: "Universal Zwave Plus Central Scene Switch", namespace: "jvm", author:"jvm") 
	{

		// Pick one of the following 5 Capabilities. Comment out the remainder.
			// capability "Bulb"
			// capability "Light"
			// capability "Outlet"		
			// capability "RelaySwitch"
			capability "Switch"		
		
		// Include the following for dimmable devices.
			// capability "SwitchLevel"
			
			
			capability "Refresh"

			
			capability "Configuration"
			capability "Initialize"	
		
		// Central Scene functions. Include the "commands" if you want to generate central scene actions from the web interface. If they are not included, central scene will still be generated from the device.
			capability "PushableButton"
				command "push", ["NUMBER"]	
				
			capability "HoldableButton"
				command "hold", ["NUMBER"]
				
			capability "ReleasableButton"
				command "release", ["NUMBER"]
				
			capability "DoubleTapableButton"
					command "doubleTap", ["NUMBER"]
			
		// Capability is not fully implemented.
			// capability "Indicator"
			

		// A generalized function for setting parameters.	
			command "setParameter",[[name:"parameterNumber",type:"NUMBER", description:"Parameter Number", constraints:["NUMBER"]],
					[name:"size",type:"NUMBER", description:"Parameter Size", constraints:["NUMBER"]],
					[name:"value",type:"NUMBER", description:"Parameter Value", constraints:["NUMBER"]]
					]
			
		// The following command is for debugging purposes.
			command "clearStateData"
			
        fingerprint mfr:"027A", prod:"B111", deviceId:"1E1C", inClusters:"0x5E,0x25,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x5B,0x6C,0x9F,0x7A", deviceJoinName: "Zooz Zen21 Switch" //US
		
        fingerprint inClusters:"0x5E,0x25,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x5B,0x6C,0x9F,0x7A", deviceJoinName: "HomeSeer HS-WS100+" //US
    }
	
    preferences 
	{
        configParams.each { input it.value.input }
        // input name: "associationsG2", type: "string", description: "To add nodes to associations use the Hexidecimal nodeID from the z-wave device list separated by commas into the space below", title: "Associations Group 2"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: false
		input name: "confirmSend", type: "bool", title: "Always confirm new value after sending to device (reduces performance)", defaultValue: false
    }
}
@Field Map configParams = [
        3: [input: [name: "configParam3", type: "enum", title: "LED Indicator Control", description: "", defaultValue: 0, options: [0:"Indicator is Off when switch is on",1:"Indicator is On when switch is On",2:"Indicator is always off"]], parameterSize: 1],
        4: [input: [name: "configParam4", type: "enum", title: "On/Off Paddle Orientation", description: "", defaultValue: 0, options: [0:"Normal",1:"Reverse"]], parameterSize: 1],
        // 1: [input: [name: "configParam1", type: "enum", title: "On/Off Paddle Orientation", description: "", defaultValue: 0, options: [0:"Normal",1:"Reverse",2:"Any paddle turns on/off"]], parameterSize: 1],
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
@Field CMD_CLASS_VERS=[
							0x5B:3, // Central Scene, Max is 3
							0x86:3, // version V1, Max is 3
							0x72:2,	// Manufacturere Specific, Max is 2
							0x8E:3, // Multi-Channel Assoication. Max is 4
							0x85:2, // Association, max is 3
							0x59:1, // Association Grp Info, Max is 3
							0x70:1	// Configuration. Max is 2
							]

def that = this

// Next line is device specific - Devices always have a first group.
// Some may have a second or more.
@Field  int numberOfAssocGroups=1

// @Field static int numberOfAssocGroups=2

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
    if (logEnable) log.debug "parse:${description}"

	Map parseMap = state.commandVersions?.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
	
    // hubitat.zwave.Command cmd = zwave.parse(description, CMD_CLASS_VERS)
	// parseMap.each{key, value ->  log.debug "parseMap values in initialize key: key ${key}, key class ${key.class}, value ${value}, value class ${value.class}"  }

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

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=100) {
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

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    if (logEnable) log.debug "Supervision get: ${cmd}"
	
//	hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
		
	Map parseMap = state.commandVersions.collectEntries{k, v -> [(k as Integer) : (v as Integer)]}
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(parseMap)
	
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

void clearStateData()
{
	state.clear()
	state.commandVersions = [:]
}

void refresh() {
	if(txtEnable) "Refreshing device status .."
    List<hubitat.zwave.Command> cmds=[]
	cmds.add(zwave.basicV1.basicGet())
    sendToDevice(cmds)
}

void installed() {
    if (logEnable) log.debug "installed ${device.label?device.label:device.name} ..."

	
	if(logEnable) log.debug "Command class version is ${state.commandVersions}. Gathering update."
	
	getZwaveClassVersions()

	runIn(5, "getCentralSceneInfo")
	runIn(10, "pollDeviceData") 

	state.installCompleted = true
}


void configure() {
	if (logEnable) log.debug "Current device data state: ${device}"
	if (logEnable) log.debug "Current state data state: ${state}"
	
	// set to false to force a re-install
	// state.installCompleted = true
    if(state.installCompleted == true)
	{
		if ( getZwaveClassVersions() != 0)
		{
		pauseExecution(10000)
		}
		getCentralSceneInfo()
	}
	else
	{
		installed()
	}
	state.configured = true
}

void  initialize()
{
	def time = new Date().getTime()
    state.initializedTime = time
    if(logEnable)
	{
		log.debug "Initializing ${device.displayName} at time: ${time}" 
		/*
		state.commandVersions.each
			{key, value -> 
				log.debug "commandVersions key ${key}, key class ${key.class}, value ${value}, value class ${value.class}"
				log.debug "Driver Version is ${driverVersion}"
			}
		*/
	}
    
	if (((state.driverVersion as Integer) != (driverVersion as Integer)) || (state.configured != true))
	{
		log.info "Updating configuration and install values."
		configure()
		state.driverVersion = driverVersion
	}
	else
	{
		if (getZwaveClassVersions())
		{
			if(logEnable) log.debug "Pausing execution for 10 seconds to allow class versions to be gathered"
			pauseExecution(10000)
		}
	}
	
	pollDeviceData()
    refresh()
	state.initialized = true
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
						descriptionText: "Device ${device.displayName} set to ${value}.", type: state.isDigital ? "digital" : "physical" )
	}

    if ((device.hasAttribute("level")  || device.hasCapability("SwitchLevel") ) && (cmd.value != 0))
	{
		eventProcess( 	name: "level", value: cmd.value, 
						descriptionText: "Device ${device.displayName} level set to ${value}%", type: state.isDigital ? "digital" : "physical" )
	}
	state.isDigital=false
}

void zwaveEvent(hubitat.zwave.commands.basicv2.BasicReport cmd) {

    if (logEnable) log.debug "Received BasicReport v2 containing: $cmd}"
	if ((cmd.value != cmd.targetValue) && (cmd.duration == 0)) log.warn "Received a V2 Basic Report with mismatched value and targetValue and non-zero duration: ${cmd}."
    if (device.hasAttribute("switch") || device.hasCapability("Switch")) 
	{
		eventProcess(	name: "switch", value: (cmd.targetValue ? "on" : "off"), 
						descriptionText: "Device ${device.displayName} set to ${value}.", type: state.isDigital ? "digital" : "physical" )
	}

    if ((device.hasAttribute("level")  || device.hasCapability("SwitchLevel") ) && (cmd.targetValue != 0))
	{
		eventProcess( 	name: "level", value: cmd.targetValue, 
						descriptionText: "Device ${device.displayName} level set to ${value}%", type: state.isDigital ? "digital" : "physical" )
	}
	state.isDigital=false
}

//returns on physical v1
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd){

	if (logEnable) log.debug "Received MultiLevel v1 Report containing: $cmd}"
    if (device.hasAttribute("switch") || device.hasCapability("Switch")) 
	{
		eventProcess(	name: "switch", value: (cmd.value ? "on" : "off"), 
						descriptionText: "Device ${device.displayName} set to ${value}.", type: "physical" )
	}

    if ((device.hasAttribute("level")  || device.hasCapability("SwitchLevel") ) && (cmd.value != 0))
	{
		eventProcess( 	name: "level", value: cmd.value, 
						descriptionText: "Device ${device.displayName} level set to ${value}%", type: "physical" )
	}
	state.isDigital=false
}

//returns on physical v2
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelReport cmd){
    if (logEnable) log.debug "SwitchMultilevelV2Report value: ${cmd}"
    if (device.hasAttribute("switch") || device.hasCapability("Switch")) 
	{
		eventProcess(	name: "switch", value: (cmd.value ? "on" : "off"), 
						descriptionText: "Device ${device.displayName} set to ${value}.", type: "physical" )
	}

    if ((device.hasAttribute("level")  || device.hasCapability("SwitchLevel") ) && (cmd.value != 0))
	{
		eventProcess( 	name: "level", value: cmd.value, 
						descriptionText: "Device ${device.displayName} level set to ${value}%", type: "physical" )
	}
	state.isDigital=false	
}

//returns on physical v3
void zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd){
    if (logEnable) log.debug "SwitchMultilevelV3Report value: ${cmd}"
    if (device.hasAttribute("switch") || device.hasCapability("Switch")) 
	{
		eventProcess(	name: "switch", value: (cmd.value ? "on" : "off"), 
						descriptionText: "Device ${device.displayName} set to ${value}.", type: "physical" )
	}

    if ((device.hasAttribute("level")  || device.hasCapability("SwitchLevel") ) && (cmd.value != 0))
	{
		eventProcess( 	name: "level", value: cmd.value, 
						descriptionText: "Device ${device.displayName} level set to ${value}%", type: "physical" )
	}
	state.isDigital=false	
}


void eventProcess(Map event) {
    if (device.currentValue(event.name).toString() != event.value.toString() || !eventFilter) {
        event.isStateChange=true
        sendEvent(event)
    }
}

void on() {
	if (logEnable) log.debug "Executing function on()."
	// state.isDigital=true	

	if (device.hasCapability("SwitchLevel")) {
		Integer levelValue = (device.currentValue("level") as Integer) ?: 99
		sendToDevice(secure(zwave.basicV1.basicSet(value: levelValue )))		
	}
	else {
		sendToDevice(secure(zwave.basicV1.basicSet(value: 255 )))
	}
	if( confirmSend ) sendToDevice (secure(zwave.basicV1.basicGet()))

	sendEvent(name: "switch", value: "on", descriptionText: "Device ${device.displayName} turned on", 
			type: "digital", isStateChange: (device.currentValue("switch") == "on") ? false : true )
}

void off() {
    
	if (logEnable) log.debug "Executing function off()."
	// state.isDigital=true	

	/*
	List<hubitat.zwave.Command> cmds = []
		cmds.add(secure(zwave.basicV1.basicSet(value: 0 )))
	sendToDevice(cmds)
	*/
	
	sendToDevice (secure(zwave.basicV1.basicSet(value: 0 )))
	if( confirmSend ) sendToDevice (secure(zwave.basicV1.basicGet()))
	
	sendEvent(name: "switch", value: "off", descriptionText: "Device ${device.displayName} turned off", 
				type: "digital",  isStateChange: (device.currentValue("switch") == "off") ? false : true )
    
}

void setlevel(level)
{    
    setLevel(level, 0)
}

void setLevel(level, duration)
{
	state.isDigital=true
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
			if (state.commandVersions?.get("38") == 1)
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
			if (state.commandVersions?.get("38") == 1)
			{
				cmds.add(secure(zwave.switchMultilevelV1.switchMultilevelSet(value: level)))
				log.warn "${device.displayName} does not support dimming duration settting command. Defaulting to dimming duration set by device parameters."
			} else {
				cmds.add(secure(zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: duration)))
			}
        	if(cmds) sendToDevice(cmds)

		}
	log.debug "Current switch value is ${device.currentValue("switch")}"
	if (device.currentValue("switch") == "off")
		{	
			log.debug "Turning switch on in setlevel function"
			sendEvent(name: "switch", value: "on", descriptionText: "Device ${device.displayName} turned on", type: "digital", isStateChange: true)
		}
		
	sendEvent(name: "level", value: level, descriptionText: "Device ${device.displayName} set to ${level}%", type: "digital", isStateChange: true)
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

Integer   getZwaveClassVersions(){
    List<hubitat.zwave.Command> cmds = []
	Integer getItems = 0
	
	if(logEnable) log.debug "Current Command Class version state is: ${state.commandVersions}"
	
	List<Integer> ic = getDataValue("inClusters").split(",").collect{ hexStrToUnsignedInt(it) }
	ic << 32 // Add Basic 

    ic.each {
	
		if (it) 
		{
			Integer thisClass = it as Integer
			
			if ( !state.commandVersions?.get(thisClass as Integer) && !state.commandVersions?.get(thisClass as String) )
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
	if(logEnable) log.debug "Processing command class report V1 to update state.commandVersions"
	state.commandVersions?.put((cmd.requestedCommandClass as String), (cmd.commandClassVersion as Integer))
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionCommandClassReport cmd) {
	if(logEnable) log.debug "Processing command class report V2 to update state.commandVersions"
	state.commandVersions?.put((cmd.requestedCommandClass as String), (cmd.commandClassVersion as Integer))
}

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionCommandClassReport cmd) {
	if(logEnable) log.debug "Processing command class report V3 to update state.commandVersions"
	state.commandVersions?.put((cmd.requestedCommandClass as String), (cmd.commandClassVersion as Integer))
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
	log.warn "Central Scene Release message not received before timeout - Faking a release message!"
	sendEvent(name:"released", value:button , type:"digital", isStateChange:true)
	state."Button_${button}_LastState" = "released"
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
	switch(button)
	{
	case 1: unsubscribe("forceReleaseHold01"); break
	case 2: unsubscribe("forceReleaseHold02"); break
	case 3: unsubscribe("forceReleaseHold03"); break
	case 4: unsubscribe("forceReleaseHold04"); break
	case 5: unsubscribe("forceReleaseHold05"); break
	case 6: unsubscribe("forceReleaseHold06"); break
	case 7: unsubscribe("forceReleaseHold07"); break
	case 8: unsubscribe("forceReleaseHold08"); break
	default: log.warn "Attempted to process lost release message code for button ${button}, but this is an error as code handles a maximum of 8 buttons."
	}
}

void setReleaseGuardTimer(button)
{
	// The code starts a release hold timer which will force a "release" to be issued
	// if a refresh isn't received within the slow refresh period!
	// If you get a refresh, executing again restarts the timer!
	// Timer is canceled by the cancelLostReleaseTimer if a "real" release is received.
	switch(button)
	{
	case 1: runIn(60, "forceReleaseHold01"); break
	case 2: runIn(60, "forceReleaseHold02"); break
	case 3: runIn(60, "forceReleaseHold03"); break
	case 4: runIn(60, "forceReleaseHold04"); break
	case 5: runIn(60, "forceReleaseHold05"); break
	case 6: runIn(60, "forceReleaseHold06"); break
	case 7: runIn(60, "forceReleaseHold07"); break
	case 8: runIn(60, "forceReleaseHold08"); break
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
    Map event = [type:"physical", isStateChange:true]
	if(logEnable) log.debug "Received Central Scene Notification ${cmd}"
	
	def taps = tapCount(cmd.keyAttributes)
	
	if(logEnable) log.debug "Mapping of key attributes to Taps: ${taps}"
	
		if (state."Button_${cmd.sceneNumber}_LastState" == "held")
		{
			// if currently holding, and receive anything except another hold, 
			// then cancel any outstanding lost "release" message timer ...
			if (taps != (-2)) 
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
				event.name = "released" 
				event.value = cmd.sceneNumber
				event.descriptionText="${device.displayName} button ${event.value} released"
				if (txtEnable) log.info event.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = event.name
				sendEvent(event)
				break

			case -2:	
				event.name = "held" 
				event.value = cmd.sceneNumber

				if (state."Button_${cmd.sceneNumber}_LastState" == "held")
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
					state."Button_${cmd.sceneNumber}_LastState" = event.name
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
				state."Button_${cmd.sceneNumber}_LastState" = event.name
				sendEvent(event)
				break				
	 
			case 2:
				event.name = "doubleTapped" 
				event.value=cmd.sceneNumber
				event.descriptionText="${device.displayName} button ${cmd.sceneNumber} doubleTapped"
				if (txtEnable) log.info event.descriptionText
				state."Button_${cmd.sceneNumber}_LastState" = event.name
				sendEvent(event)			
				break
			
			case 3: // Key Pressed 3 times
			case 4: // Key Pressed 4 times
			case 5: // Key Pressed 5 times
				log.warn "Received and Ignored key tapped ${taps} times on button number ${cmd.sceneNumber}. Maximum button taps supported is 2"
				break
		}
}

