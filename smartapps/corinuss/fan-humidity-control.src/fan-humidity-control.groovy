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
        input "highHumidity", "number", title: "Start fan at this humidity %"
        input "lowHumidity", "number", title: "Run fan until this humidity % is reached"
        input "minRunTime", "number", title: "Minutes to run fan after target humidity is reached"
        input "runFanAfterTarget", "bool", title: "Should the fan run after target humidity is reached?"
    }
    section("Activate relative to another sensor:"){
        input "referenceSensor", "capability.relativeHumidityMeasurement", title: "Humidity Sensor to compare against"
        input "highHumidityDelta", "number", title: "Start fan when our humidity is this much higher than our reference"
        input "lowHumidityDelta", "number", title: "Run fan until our humidity has dropped to this much higher than our reference"
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
	subscribe(fanSwitch, "switch.off", switchChangeHandler)
    
    if (fanSwitch.hasCapability("Button"))
    {
    	log.debug "Double-tap switch detected."
		subscribe(fanSwitch, "button.pushed", switchDoubleTapHandler)
    }
    
    state.fanShouldRun = false
    state.ownsSwitch = false
    state.fanTimerRunning = false
    
    checkHumidity()
}

def humidityChangeHandler(evt)
{
    log.debug "[Humidity] $evt.name: $evt.value"
    checkHumidity()
}

def checkHumidity()
{
    def currentHumidity = humiditySensor.latestValue("humidity")
    log.debug "currentHumidity $currentHumidity"

	if (isLowHumidity(currentHumidity))
    {
    	if (state.fanShouldRun)
        {
        	if (settings.minRunTime != null && settings.minRunTime > 0)
            {
            	// If a timer isn't already running, set a callback so we can stop the fan after minRunTime has expired.
            	if (!state.fanTimerRunning && settings.runFanAfterTarget)
                {
                	sendNotificationEvent("Running fan '${fanSwitch.getDisplayName()}' for $settings.minRunTime minutes after low humidity ${currentHumidity}% reached.")
	                fanTimerStart()
                }
            }
            else
            {
            	// We don't have a minimum run time.  Turn the fan off now.
				sendNotificationEvent("Turning off fan '${fanSwitch.getDisplayName()}' after low humidity ${currentHumidity}% reached.")
            	state.fanShouldRun = false
                switchTurnOff()
            }
        }
        else
        {
	        if (state.containsKey('switchOnStart'))
            {
            	// FIXME - This shouldn't be required.  Likely a flaw in my logic somewhere.
                state.remove('switchOnStart')
                state.ownsSwitch = false
                sendNotificationEvent("DEBUG: Cleaning up 'switchOnStart' for fan '${fanSwitch.getDisplayName()}', since we don't own this anymore.")
            }
        }
    }
    else
    {
    	// Humidity didn't stay below its minimum threshold, so disable the timer to prevent the fan from turning off.
        if (state.fanTimerRunning)
        {
			sendNotificationEvent("Clearing fan '${fanSwitch.getDisplayName()}' timer after humidity ${currentHumidity}% raising back above minimum.")
	    	state.fanTimerRunning = false
        }
        
	    if (isHighHumidity(currentHumidity))
    	{
        	if (!state.fanShouldRun)
            {
	    		state.fanShouldRun = true
				sendNotificationEvent("Enabling fan '${fanSwitch.getDisplayName()}' due to high humidity ${currentHumidity}% on sensor '${humiditySensor.getDisplayName()}'")
			}
            
   	        switchTurnOn()
    	}
    }
}

def isHighHumidity(humidityLevel)
{
	if (settings.highHumidity != null && humidityLevel >= settings.highHumidity)
    {
	    log.debug "[isHighHumidity] Passed due to currentHumidity ($currentHumidity) >= settings.highHumidity ($settings.highHumidity)"
    	return true
    }
    
    if (settings.referenceSensor != null && settings.highHumidityDelta != null)
    {
    	def referenceHumidity = referenceSensor.latestValue("humidity")
        
        if (humidityLevel >= referenceHumidity + settings.highHumidityDelta)
        {
		    log.debug "[isHighHumidity] Passed due to currentHumidity ($currentHumidity) >= referenceHumidity ($referenceHumidity) + settings.highHumidityDelta ($settings.highHumidityDelta)"
        	return true;
        }
    }
    
    log.debug "[isHighHumidity] Failed"
    return false
}

def isLowHumidity(humidityLevel)
{
	if (settings.lowHumidity != null && currentHumidity >= settings.lowHumidity)
    {
	    log.debug "[isLowHumidity] Failed due to currentHumidity ($currentHumidity) >= settings.lowHumidity ($settings.lowHumidity)"
    	return false
    }
    
    if (settings.referenceSensor != null && settings.lowHumidityDelta != null)
    {
    	def referenceHumidity = referenceSensor.latestValue("humidity")
        
        if (humidityLevel >= referenceHumidity + settings.lowHumidityDelta)
        {
		    log.debug "[isLowHumidity] Failed due to currentHumidity ($currentHumidity) >= referenceHumidity ($referenceHumidity) + settings.lowHumidityDelta ($settings.lowHumidityDelta)"
        	return false;
        }
    }
    
    log.debug "[isLowHumidity] Passed"
    return true;
}

def fanTimerStart()
{
    state.fanTimerRunning = true
    runIn(60*settings.minRunTime, fanTimerExpiration, [overwrite: true])
}

def fanTimerExpiration(data)
{
    log.debug "[fan timer expired]"
   	log.debug "state.fanTimerRunning $state.fanTimerRunning"

	if (state.fanTimerRunning)
    {
		sendNotificationEvent("Turning off fan '${fanSwitch.getDisplayName()}' after running $settings.minRunTime minutes.")
    
        state.fanTimerRunning = false
        state.fanShouldRun = false
        switchTurnOff()
    }
}

def switchChangeHandler(evt)
{
    log.debug "[Switch] $evt.name: $evt.value"
    
    if (state.fanShouldRun && evt.value == "off")
    {
        if (state.containsKey('switchOnStart') && 
            (now() - state.switchOnStart < 60000))
        {
            // User has turned off the switch within a minute of us turning it back on.
            // They're forcing it off, so let them.
            state.remove('switchOnStart')
            state.ownsSwitch = false
            sendNotificationEvent("Disabling fan '${fanSwitch.getDisplayName()}' due to user override.")
        }
        else
        {
            // Turn the fan switch back on.
            switchTurnOn()
            sendNotificationEvent("Re-enabling fan '${fanSwitch.getDisplayName()}' after user attempted to turn off.")
        }
    }
}

def switchDoubleTapHandler(evt)
{
    log.debug "[DoubleTap] $evt.name: $evt.value ${evt.jsonData?.buttonNumber}"
    
    if (evt.jsonData?.buttonNumber == 1 && $settings.minRunTime != null)
    {
    	sendNotificationEvent("Running fan '${fanSwitch.getDisplayName()}' for $settings.minRunTime minutes after double-tap received.")
        state.fanShouldRun = true
        switchTurnOn()
    	fanTimerStart()
    }
}

def switchTurnOn()
{
    log.debug "[switchTurnOn]"

    def switchState = fanSwitch.latestValue("switch")
    log.debug "switchState $switchState"

	if (switchState == 'off')
    {
    	state.switchOnStart = now()
        state.ownsSwitch = true
        fanSwitch.on()
    }
}

def switchTurnOff()
{
    log.debug "[switchTurnOff]"

    def switchState = fanSwitch.latestValue("switch")
    log.debug "switchState $switchState"
    log.debug "state.ownsSwitch $state.ownsSwitch"

	// Only turn off the switch if we actually own it
	if (state.ownsSwitch && switchState == 'on')
    {
    	state.remove('switchOnStart')
        state.ownsSwitch = false
        
        log.debug "fanSwitch.off"
        fanSwitch.off()
    }
}