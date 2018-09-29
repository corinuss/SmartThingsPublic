/**
 *  Run the bathroom fan!
 *
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
 */
definition(
    name: "Fan Humidity Control",
    namespace: "corinuss",
    author: "Eric Will",
    description: "Manage a fan in humid conditions.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences
{
    section("Devices to monitor:")
    {
        input "fanSwitch", "capability.switch", title: "Fan Switch to control", required: true
        input "humiditySensor", "capability.relativeHumidityMeasurement", title: "Humidity Sensor to monitor", required: true
    }
    section("Triggers:")
    {
        input "highHumidity", "number", title: "Start fan at this humidity %", required: true
        input "lowHumidity", "number", title: "Run fan until this humidity % is reached", required: true
        input "minRunTime", "number", title: "Minutes to run fan after target humidity is reached", required: true
    }
}

def installed()
{
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated()
{
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize()
{
	subscribe(humiditySensor, "humidity", humidityChangeHandler)
	subscribe(fanSwitch, "switch", switchChangeHandler)
    
    state.fanShouldRun = false
    state.ownsSwitch = false
    // TODO Force a humidity check here?
}

def humidityChangeHandler(evt)
{
    log.debug "Humidity $evt.name: $evt.value"
    
	if (evt.value < settings.lowHumidity)
    {
    	if (state.fanShouldRun)
        {
        	if (settings.minRunTime > 0)
            {
            	// If a timer isn't already running, set a callback so we can stop the fan after minRunTime has expired.
            	if (!state.containsKey('fanTimerStart'))
                {
                    state.fanTimerStart = now()
					runIn(60*settings.minRunTime, fanTimerExpiration)
                }
            }
            else
            {
            	// We don't have a minimum run time.  Turn the fan off now.
            	state.fanShouldRun = false
                switchTurnOff()
            }
        }
    }
    else
    {
    	// Humidity didn't stay below its minimum threshold, so disable the timer to prevent the fan from turning off.
    	state.remove('fanTimerStart')
        
	    if (evt.value >= settings.highHumidity)
    	{
    		state.fanShouldRun = true
            switchTurnOn()
    	}
    }
}

def fanTimerExpiration()
{
    log.debug "fan timer expired"

	if (state.containsKey('fanTimerStart'))
    {
    	def millisecondsSinceFanStart = now() - state.fanTimerStart
        if (millisecondsSinceFanStart > 60000*settings.minRunTime)
        {
        	state.remove('fanTimerStart')
        	state.fanShouldRun = false
            switchTurnOff()
        }
    }
}

def switchChangeHandler(evt)
{
    log.debug "Switch $evt.name: $evt.value"
    
    if (state.fanShouldRun && evt.value == "off")
    {
    	if (state.containsKey('switchOnStart') && 
        	(now() - state.switchOnStart < 60000))
        {
        	// User has turned off the switch within a minute of us turning it back on.
            // They're forcing it off, so let them.
        	state.remove('switchOnStart')
            state.ownsSwitch = false
        }
        else
        {
            // Turn the fan switch back on.
        	switchTurnOn()
        }
    }
}

def switchTurnOn()
{
    log.debug "switchTurnOn"

	if (fanSwitch.switchState == 'off')
    {
    	state.switchOnStart = now()
        state.ownsSwitch = true
        fanSwitch.on()
    }
}

def switchTurnOff()
{
    log.debug "switchTurnOff"

	// Only turn off the switch if we actually own it
	if (state.ownsSwitch && fanSwitch.switchState == 'on')
    {
    	state.remove('switchOnStart')
        state.ownsSwitch = false
        fanSwitch.off()
    }
}