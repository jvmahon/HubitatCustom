metadata {
	definition (name: "[Beta] Test Creating Reports",namespace: "jvm", author: "jvm") {
        command "failedReports"
        command "workingReports"
        command "outdated"
    }
}


void testCreateCommand(String inputString)
{

	String commandString = inputString.startsWith("0x") ? inputString.substring(2) : inputString
	
    log.debug "substrings: ${commandString.substring(0,2)}, ${commandString.substring(2,4)}, ${commandString.substring(4)}."
    
    Short commandClass =    hubitat.helper.HexUtils.hexStringToInt(commandString.substring(0,2))
    Short command =         hubitat.helper.HexUtils.hexStringToInt(commandString.substring(2,4))
    List<Short> payload =     hubitat.helper.HexUtils.hexStringToIntArray(commandString.substring(4))

	def cmd = zwave.getCommand(commandClass, command, payload)  
		log.debug "Created command is: ${cmd} with a format() hex string: ${cmd.format()}"			
}


void outdated()
{
    log.warn "These are reports that appear to have been removed from the z-wave command classes!"
    		
	// SecurityPanelModeSupportedReport   Get:  Report:0x2402
testCreateCommand("0x2402")
		
	// SecurityPanelZoneSupportedReport  Get:  Report:0x2E02
testCreateCommand("0x2E0201")	
}

void failedReports()
{
      
	// CommandRecordsSupportedReport  Get:  Report:0x9B02
testCreateCommand("0x9B0201")
		
	// DcpListSupportedReport  Get:  Report:0x3A02
testCreateCommand("0x3A0201")
		// HrvControlModeSupportedReport  Get:  Report:0x390B
testCreateCommand("0x390B0103")
		
	// HrvStatusSupportedReport  Get:  Report:0x3704
testCreateCommand("0x370403")
    
    	// ThermostatModeSupportedReport  Get:  Report:0x4005
testCreateCommand("0x4005FF")
		
	// ThermostatOperatingLoggingSupportedReport  Get:  Report:0x4204
testCreateCommand("0x420403")
		
	// ThermostatSetpointSupportedReport  Get:  Report:0x4305
testCreateCommand("0x43050F")
    
    
	// MeterTblStatusSupportedReport  Get:  Report:0x3D08
testCreateCommand("0x3D080F0100F0")	    

    	// RateTblSupportedReport  Get:  Report:0x4902
testCreateCommand("0x49020103")
    
	// ScheduleSupportedReport  Get:  Report:0x5302
testCreateCommand("0x53020701")
    
	// ScheduleEntryTypeSupportedReport  Get:  Report:0x4E0A
	// Deprecated
testCreateCommand("0x4E0A01")
		
	// SensorAlarmSupportedReport  Get:  Report:0x9C04
	// Deprecated
testCreateCommand("0x9C040103")
    
}

void workingReports()
{
    	// MultiChannelEndPointReport Get:0x6007  Report:0x6008
testCreateCommand("0x6008400302") 
		
	// AlarmTypeSupportedReport Get:  Report:0x7108
	// Deprecated
testCreateCommand("0x71080107")
		
	// CentralSceneSupportedReport Get:0x5B01  Report:0x5B02
	// Per standards, Central Scene should never have an endpoint!
testCreateCommand("0x5B02028307")

	// EventSupportedReport  Get:0x7101  Report:0x7102
testCreateCommand("0x7102010307FF01")

	// DoorLockLoggingRecordsSupportedReport  Get:  Report:0x4C02
testCreateCommand("0x4C0216")

	// IndicatorSupportedReport  Get:0x8704  Report:0x8705
testCreateCommand("0x87050103010F")
		
	// MeterSupportedReport  Get:0x3203  Report:0x3204
testCreateCommand("0x32040107")

	// NotificationSupportedReport  Get:0x7107  Report:0x7108
testCreateCommand("0x710801FF")		

	// SwitchMultilevelSupportedReport  Get:0x2606  Report:0x2607
	// Only supported by V3 or later devices!
testCreateCommand("0x26070101")

	// ProtectionSupportedReport  Get:0x7504  Report:0x7505
testCreateCommand("0x75050301020102")
    
	// SimpleAvControlSupportedReport  Get:  Report:0x9405
testCreateCommand("0x94050103")
		
	// SwitchColorSupportedReport  Get:  Report:0x3302
testCreateCommand("0x33020707")
		
	// ThermostatFanModeSupportedReport  Get:  Report:0x4405
testCreateCommand("0x44050F03")    
    
    
	// PrepaymentSupportedReport  Get:  Report:0x3F04
testCreateCommand("0x3F0401")
}
