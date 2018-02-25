/**
 *  Copyright 2018 Eric Will
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Monoprice 4-in-1 Motion Sensor with Temperature, Humidity, and Light Sensors
 *
 *  Author: Eric Will
 *  Date: 2018-01-21
 */

metadata {
	definition (name: "Monoprice 4-in-1 Motion Sensor", namespace: "corinuss", author: "Eric Will") {
    	capability "Actuator"
		capability "Battery"
        capability "Configuration"
		capability "Health Check"
        capability "Illuminance Measurement"
		capability "Motion Sensor"
        capability "Relative Humidity Measurement"
		capability "Sensor"
        capability "Temperature Measurement"

		fingerprint mfr: "0109", prod: "2021", model: "2101", deviceJoinName: "Monoprice 4-in-1 Motion Sensor"
    }

	simulator {
    	// TODO
		status "inactive": "command: 3003, payload: 00"
		status "active": "command: 3003, payload: FF"
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"motion", type: "generic", width: 6, height: 4){
			tileAttribute("device.motion", key: "PRIMARY_CONTROL") {
				attributeState("active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc")
				attributeState("inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff")
			}
		}
		valueTile("temperature", "device.temperature", decoration: "flat", width: 2, height: 2) {
			state("temperature", label:'${currentValue}°F', unit:"", icon: "st.Weather.weather2", backgroundColors:[
                [value: 31, color: "#153591"],
                [value: 44, color: "#1e9cbb"],
                [value: 59, color: "#90d2a7"],
                [value: 74, color: "#44b621"],
                [value: 84, color: "#f1d801"],
                [value: 95, color: "#d04e00"],
                [value: 96, color: "#bc2323"]
            ])
		}
		valueTile("humidity", "device.humidity", decoration: "flat", width: 2, height: 2) {
			state("humidity", label:'${currentValue}%', unit:"", icon: "st.Weather.weather12")
		}
		valueTile("illuminance", "device.illuminance", decoration: "flat", width: 2, height: 2) {
			state("illuminance", label:'${currentValue}%', unit:"", icon: "st.illuminance.illuminance.light"
 //           backgroundColors:[
 //           [value: 0, color: "#ffffff"],
 //           [value: 100, color: "#ffff00"],
 //           ]
            )
		}
		valueTile("battery", "device.battery", decoration: "flat", width: 2, height: 2) {
			state("battery", label:'${currentValue}% battery', unit:"")
		}
        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state("configure", label:'', action:"configure", icon:"st.secondary.configure")
		}

		//main("motion")
		main("humidity")
		details(["motion", "temperature", "humidity", "illuminance", "battery", "configure"])
	}

    preferences {
        input "tempUpdateInterval", "decimal", title: "Temperature Delta (0.1-5°F)", description: "Delta required before Temperature is updated.", range: "0.1..5"
        input "humidityUpdateInterval", "number", title: "Humidity Delta (1-50%)", description: "Delta required before Humidity is updated.", range: "1..50"
        input "luxUpdateInterval", "number", title: "Illuminance Delta (1-50%)", description: "Delta required before Illuminance is updated.", range: "1..50"
        input "motionRetrigger", "number", title: "Motion Retrigger Time (1-255 minutes)", description: "How many minutes between motion sensor triggers?", range: "1..255"
        input "motionSensitivity", "number", title: "Motion Sensitivity (1-7)", description: "How sensitive is the motion sensor?", range: "1..7"
        input "ledMode", "enum", title: "LED Mode", description: "LED behavior when motion is detected", options: ["Off", "Flash once", "Breathe"]
        input "wakeUpPeriod", "number", title: "Wake Up Period (minutes)", description: "Minutes between forced wake-ups (10 minute intervals)"
	}
}

def installed() {
// Device wakes up every 4 hours, this interval allows us to miss one wakeup notification before marking offline
	sendEvent(name: "checkInterval", value: 8 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}

def updated() {
// Device wakes up every 4 hours, this interval allows us to miss one wakeup notification before marking offline
	sendEvent(name: "checkInterval", value: 8 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}

private getCommandClassVersions() {
	[
    	0x20: 1,	// Basic
        0x30: 1,	// Sensor Binary (TODO - Confirm)
        0x31: 5,	// Sensor Multilevel (up to V7)
        0x80: 1,	// Battery
        0x84: 2,	// Wake Up
        0x71: 3,	// Notification (up to V4)
        0x70: 1, 	// Configuration
        0x98: 1		// Security
	]
}

def parse(String description) {
	if (!state.configured) {
	    log.debug "Configuration is pending"
    }
    
	def result = null
	if (description.startsWith("Err")) {
	    result = createEvent(descriptionText:description)
	} else {
		def cmd = zwave.parse(description, commandClassVersions)
        if (cmd) {
        	result = zwaveEvent(cmd)
            log.debug "Parsed ${cmd} to ${result.inspect()}"
        } else {
            log.debug "Non-parsed event: ${description}"
        	result = createEvent(value: description, descriptionText: description, isStateChange: false)
        }
    }
	return result
}

def sensorValueEvent(value, source) {
	if (value) {
		createEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion")
	} else {
		createEvent(name: "motion", value: "inactive", descriptionText: "$device.displayName motion has stopped")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
	sensorValueEvent(cmd.value, "BasicReport")
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
	sensorValueEvent(cmd.value, "BasicSet")
}

//def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
//{
//	sensorValueEvent(cmd.value, "SwitchBinaryReport")
//}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd)
{
	sensorValueEvent(cmd.sensorValue, "SensorBinaryReport")
}

//def zwaveEvent(physicalgraph.zwave.commands.sensoralarmv1.SensorAlarmReport cmd)
//{
//	sensorValueEvent(cmd.sensorState, "SensorAlarmReport")
//}

def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd)
{
	def result = []
	if (cmd.v1AlarmType == 0x07) {
    	// Also reports motion, but we don't need double the reports...
		//result << sensorValueEvent(cmd.v1AlarmLevel, "NotificationReport(7)")
//	} else if (cmd.notificationType == 0x07) {
//    	// Not sure what these report...
//		if (cmd.event == 0x01 || cmd.event == 0x02 || cmd.event == 0x07 || cmd.event == 0x08) {
//			result << sensorValueEvent(1, "NotificationReport(1)")
//		} else if (cmd.event == 0x00) {
//			result << sensorValueEvent(0, "NotificationReport(0)")
//		} else if (cmd.event == 0x03) {
//			result << createEvent(name: "tamper", value: "detected", descriptionText: "$device.displayName covering was removed", isStateChange: true)
//			result << response(zwave.batteryV1.batteryGet())
//		}
	} else if (cmd.notificationType) {
		def text = "Notification $cmd.notificationType: event ${([cmd.event] + cmd.eventParameter).join(", ")}"
		result << createEvent(name: "notification$cmd.notificationType", value: "$cmd.event", descriptionText: text, isStateChange: true, displayed: false)
//	} else {
//		def value = cmd.v1AlarmLevel == 255 ? "active" : cmd.v1AlarmLevel ?: "inactive"
//		result << createEvent(name: "alarm $cmd.v1AlarmType", value: value, isStateChange: true, displayed: false)
	}

//	if (cmd.notificationType == 0x07) {
//		if (cmd.v1AlarmType == 0x07) {  // special case for nonstandard messages from Monoprice ensors
//			result << sensorValueEvent(cmd.v1AlarmLevel, "NotificationReport(7)")
//		} else if (cmd.event == 0x01 || cmd.event == 0x02 || cmd.event == 0x07 || cmd.event == 0x08) {
//			result << sensorValueEvent(1, "NotificationReport(1)")
//		} else if (cmd.event == 0x00) {
//			result << sensorValueEvent(0, "NotificationReport(0)")
//		} else if (cmd.event == 0x03) {
//			result << createEvent(name: "tamper", value: "detected", descriptionText: "$device.displayName covering was removed", isStateChange: true)
//			result << response(zwave.batteryV1.batteryGet())
//		} else if (cmd.event == 0x05 || cmd.event == 0x06) {
//			result << createEvent(descriptionText: "$device.displayName detected glass breakage", isStateChange: true)
//		}
//	} else if (cmd.notificationType) {
//		def text = "Notification $cmd.notificationType: event ${([cmd.event] + cmd.eventParameter).join(", ")}"
//		result << createEvent(name: "notification$cmd.notificationType", value: "$cmd.event", descriptionText: text, isStateChange: true, displayed: false)
//	} else {
//		def value = cmd.v1AlarmLevel == 255 ? "active" : cmd.v1AlarmLevel ?: "inactive"
//		result << createEvent(name: "alarm $cmd.v1AlarmType", value: value, isStateChange: true, displayed: false)
//	}
	result
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd)
{
    log.debug "WakeUpNotification received" 
	def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: true)]

//	if (state.MSR == "011A-0601-0901" && device.currentState('motion') == null) {  // Enerwave motion doesn't always get the associationSet that the hub sends on join
//		result << response(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId))
//	}
	if (!state.lastbat || (new Date().time) - state.lastbat > 53*60*60*1000) {
		result << response(secure(zwave.batteryV1.batteryGet()))
		result << response("delay 1200")
	}

    if (!state.configured) {
    	// we're still in the process of configuring a newly joined device
        log.debug("late configure")
		result << createEvent(descriptionText: "Applying configuration for ${device.displayName}", isStateChange: true)
        result += response(commitConfigure())
	}
//	else {
//		result += response(secureSequence([
//			zwave.wakeUpV2.wakeUpIntervalCapabilitiesGet(),
//			zwave.wakeUpV2.wakeUpIntervalGet(),
//			zwave.wakeUpV2.wakeUpIntervalSet(seconds:60, nodeid:zwaveHubNodeId)
//		]))
//	}

    result << response(secure(zwave.wakeUpV2.wakeUpNoMoreInformation()))
    result << createEvent(descriptionText: "${device.displayName} going back to sleep.", isStateChange: true)
    
	result
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpIntervalCapabilitiesReport cmd)
{
	def result = []

	log.debug "defaultWakeUpIntervalSeconds = ${cmd.defaultWakeUpIntervalSeconds}"
	result << createEvent(descriptionText: "defaultWakeUpIntervalSeconds is ${cmd.defaultWakeUpIntervalSeconds}", isStateChange: true)

	log.debug "maximumWakeUpIntervalSeconds = ${cmd.maximumWakeUpIntervalSeconds}"
	result << createEvent(descriptionText: "maximumWakeUpIntervalSeconds is ${cmd.maximumWakeUpIntervalSeconds}", isStateChange: true)

	log.debug "minimumWakeUpIntervalSeconds = ${cmd.minimumWakeUpIntervalSeconds}"
	result << createEvent(descriptionText: "minimumWakeUpIntervalSeconds is ${cmd.minimumWakeUpIntervalSeconds}", isStateChange: true)

	log.debug "wakeUpIntervalStepSeconds = ${cmd.wakeUpIntervalStepSeconds}"
	result << createEvent(descriptionText: "wakeUpIntervalStepSeconds is ${cmd.wakeUpIntervalStepSeconds}", isStateChange: true)

	result
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpIntervalReport cmd)
{
	log.debug "Current wakeup interval = ${cmd.seconds} seconds"
    createEvent(descriptionText: "Current wakeup interval is ${cmd.seconds}", isStateChange: true)
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	log.debug "Current battery level = ${cmd.batteryLevel}"
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
		map.isStateChange = true
	} else {
		map.value = cmd.batteryLevel
	}
	state.lastbat = new Date().time
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd)
{
	def map = [ displayed: true, value: cmd.scaledSensorValue.toString() ]
	switch (cmd.sensorType) {
		case 1:
			map.name = "temperature"
			def cmdScale = cmd.scale == 1 ? "F" : "C"
			map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, 0)
			//map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
			map.unit = getTemperatureScale()
			break;
		case 3:
			map.name = "illuminance"
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = "%"
			break;
		case 5:
			map.name = "humidity"
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = "%"
			break;
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
	// log.debug "encapsulated: $encapsulatedCommand"
	if (encapsulatedCommand) {
		state.sec = 1
		zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd)
{
	// def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
	def version = commandClassVersions[cmd.commandClass as Integer]
	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (encapsulatedCommand) {
		return zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	def result = null
	def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
	log.debug "Command from endpoint ${cmd.sourceEndPoint}: ${encapsulatedCommand}"
	if (encapsulatedCommand) {
		result = zwaveEvent(encapsulatedCommand)
	}
	result
}

def zwaveEvent(physicalgraph.zwave.commands.multicmdv1.MultiCmdEncap cmd) {
	log.debug "MultiCmd with $numberOfCommands inner commands"
	cmd.encapsulatedCommands(commandClassVersions).collect { encapsulatedCommand ->
		zwaveEvent(encapsulatedCommand)
	}.flatten()
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	createEvent(descriptionText: "$device.displayName: $cmd", displayed: false)
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	def result = []

	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	log.debug "msr: $msr"
	updateDataValue("MSR", msr)

	result << createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)
	result
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	secure(zwave.batteryV1.batteryGet())
}

def configure() {
	log.debug "configure()"
	state.configured = false
    null
}

def commitConfigure() {
	log.debug "commitConfigure()"
	def request = []
    
	def wakeUpPeriodSeconds = (wakeUpPeriod ?: 60) * 60
	request << zwave.wakeUpV2.wakeUpIntervalSet(seconds: wakeUpPeriodSeconds, nodeid:zwaveHubNodeId)

	// DO NOT TOUCH!!!  (also needs a delay after setting)
	// enable/disable notification-style motion events
	//request << zwave.notificationV3.notificationSet(notificationType: 7, notificationStatus: 0xFF)

	// Set Temperature to °F
	request << zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: 1)

	// Set Temperature update interval in units of 0.1°F.
	def scaledTempUpdateInterval = Math.min(Math.max((int)Math.round((tempUpdateInterval?.floatValue() ?: 1) * 10), 1), 50)
	request << zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: scaledTempUpdateInterval)

	// Set Humidity update interval in units of 1%.
	request << zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: humidityUpdateInterval ?: 10)

	// Set Illumination update interval in units of 1%.
	request << zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: luxUpdateInterval ?: 10)

	// Set Motion Retrigger time in minutes
	request << zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: motionRetrigger ?: 3)

	// Set Motion Sensitivity (1-7)
	request << zwave.configurationV1.configurationSet(parameterNumber: 6, size: 1, scaledConfigurationValue: motionSensitivity ?: 4)

	// Set LED Mode (1-3)
    def ledModeValue = 3
    switch (ledMode)
    {
    	case "Off":
        	ledModeValue = 1
            break
        case "Breathe":
        	ledModeValue = 2
            break
        case "Flash once":
        	ledModeValue = 3
            break
    }
	request << zwave.configurationV1.configurationSet(parameterNumber: 7, size: 1, scaledConfigurationValue: ledModeValue)

	request << zwave.sensorBinaryV2.sensorBinaryGet(sensorType: 0x0C) //motion
	request << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 0x01, scale: 0x1) //temperature
	request << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 0x03) //illuminance
	request << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 0x05) //humidity

	state.configured = true

	def fullRequest = secureSequence(request)
   	fullRequest << response("delay 2000")
    fullRequest
}

private secure(physicalgraph.zwave.Command cmd) {
	zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private secureSequence(commands, delay=1000) {
	delayBetween(commands.collect{ secure(it) }, delay)
}
