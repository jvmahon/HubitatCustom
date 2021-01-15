/*
	Generic Component Lock
	For locks, the setCodeLength function is device-specific. Accordingly, most functionality is implemented
	at the parent level, but a separate component driver "stub" is provided, and a child device, created
	for the actual lock. The setCodeLength function is implemented in the stub.
*/
metadata {
    definition(name: "Generic Component Lock", namespace: "jvm", author: "jvm", component: true) {
        capability "Actuator"
		capability "Sensor"
		capability "Lock"
        capability "LockCodes"
		capability "Battery"
		capability "TamperAlert"
    }
    preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

void updated() {
    log.info "Updated..."
    log.warn "description logging is: ${txtEnable == true}"
}

void installed() {
    log.info "Installed..."
    refresh()
}

void parse(String description) { log.warn "parse(String description) not implemented" }

void parse(List<Map> description) {
    description.each {
        if (it.name in ["lock","codeChanged", "codeLength", "lockCodes", "maxCodes", "battery" "tamper"]) {
            if (txtEnable) log.info it.descriptionText
            sendEvent(it)
        }
    }
}

void lock() {
    parent?.componentLock(this.device)
}

void unlock() {
    parent?.componentUnlock(this.device)
}

void deleteCode(codeposition) {
    parent?.componentDeleteCode(this.device,codeposition)
}

void getCodes() {
    parent?.componentGetCodes(this.device)
}

void setCode(codeposition, pincode, name) {
    parent?.componentSetCode(this.device,codeposition, pincode, name)
}

void refresh() {
    parent?.componentRefresh(this.device)
}

void setCodeLength(pincodelength) {
    parent?.componentSetCodeLength(this.device,pincodelength)
}

def setSchlagePinLength(newValue = null )
{
	def cmds = null
	if ((newValue == null) || (newValue == 0))
	{
		// just send a request to refresh the value
		cmds = secure(zwave.configurationV2.configurationGet(parameterNumber: 0x10))
	}
	else if (newValue <= 8)
	{
		sendEvent(descriptionText: "$device.displayName attempting to change PIN length to $newValue", isStateChange: true)
		cmds = secure(zwave.configurationV2.configurationSet(parameterNumber: 10, size: 1, configurationValue:[newValue]))
	}
	else
	{
		sendEvent(descriptionText: "$device.displayName UNABLE to set PIN length of $newValue", displayed: true, isStateChange: true)
	}
	log.debug "setPinLength sending ${cmds.inspect()}"
	cmds
}