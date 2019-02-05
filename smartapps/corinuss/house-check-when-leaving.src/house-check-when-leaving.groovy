/**
 *  Is the house locked?
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
    name: "House Check when Leaving",
    namespace: "corinuss",
    author: "Eric Will",
    description: "When someone leaves, check if any doors are unlocked or opened.",
    category: "My Apps",
    iconUrl: "http://cdn.device-icons.smartthings.com/Transportation/transportation12-icn.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/Transportation/transportation12-icn@2x.png",
    iconX3Url: "http://cdn.device-icons.smartthings.com/Transportation/transportation12-icn@2x.png")


preferences
{
    section("Check when these people leave:")
    {
        input "people", "capability.presenceSensor", title: "Which people?", multiple:true, required: true
        input "delay", "number", title: "Minutes after they leave", required: false
    }
    section("Doors to check:")
    {
        input "garageDoors", "capability.garageDoorControl", title: "Which garage doors?", multiple:true, required: false
        input "doorLocks", "capability.lock", title: "Which door locks?", multiple:true, required: false
    }
    section("Notify these people:")
    {
        input "recipients", "contact", title: "Send notifications to", multiple:true, required: false
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
	subscribe(people, "presence.not present", presenceChangeHandler)
}

def presenceChangeHandler(evt)
{
    log.debug "evt.name: $evt.value"
    runIn(60*settings.delay, checkHouse, [data: [user: evt.displayName]])
}

def checkHouse(data)
{
	def needNotify = false;
    def message = "These doors are open:"
    
	for (garageDoor in garageDoors)
    {
    	def doorState = garageDoor.doorState.value
	    log.debug "${garageDoor.displayName}: ${doorState}"
    	if (doorState == "open" || doorState == "opening")
        {
        	needNotify = true;
            message += " '${garageDoor.displayName}'"
        }
    }

	for (doorLock in doorLocks)
    {
    	def doorState = doorLock.lockState.value
	    log.debug "${doorLock.displayName}: ${doorState}"
    	if (doorState == "unlocked" || doorState == "unlocked with timeout")
        {
        	needNotify = true;
            message += " '${doorLock.displayName}'"
        }
    }

	if (needNotify)
    {
        log.debug "recipients configured: $recipients"
        if (location.contactBookEnabled && recipients)
        {
            log.debug "Contact Book enabled!"
            sendNotificationToContacts(message, recipients)
        }
        else
        {
            log.debug "Contact Book not enabled"
            sendPush(message)
        }
	}
}