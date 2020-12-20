metadata {
        definition (name: "Multi Choice Input",namespace: "jvm", author: "jvm") {

    }
    preferences 
	{
		input name: "Enter a number", type: "integer", title: "Enter a Number"

		input name: "Single Choice", type: "enum", title: "Choose One Item", multiple: false, options: [[1:"One"],[2:"Two" ],[4:"Four"],[8:"Eight"]]
        input name: "Multi-Choice", type: "enum", title: "Choose Multiple Items", multiple: true, options: [[1:"One"],[2:"Two" ],[4:"Four"],[8:"Eight"]]
        input name: "configParam031", multiple:true, options:[[1: "LED 1 Blink Status (bottom)"], [2: "LED 2 Blink Status - Blink"], [4: "LED 3 Blink Status - Blink"], [16: "LED 5 Blink Status - Blink"], [8: "LED 4 Blink Status - Blink"], [64: "LED 7 Blink Status - Blink"], [32: "LED 6 Blink Status - Blink"]], title: "(31) Choose Multiple", type: "enum"
    }
}

void updated()
{
	settings.each
	{
	log.debug "Setting 'it' value is: ${it}"
	}

}

