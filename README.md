# HubitatCustom

## Current Plans . . .
Feb. 23, 2021: Update - Code has been reworked with a focuse on better Endpoint handling and improved concurrency handling

# Advanced Zwave Plus Dimmer driver  and Switch driver- Beta Releases!

The file "Advanced Zwave Plus Dimmer Driver.groovy" is a dimmer driver file, and Advanced Zwave Plus Switch Driver.groovy" a Switch driver that can identify all the parameters for a device and provides input controls allowing the setting of each parameter.

The way this works is that the driver queries the opensmarthouse.com database using the device's manufacturer, device type, and device ID information to retrieve a database record identifying all the parameters for the device. That information is then saved in the device's "state".

## Central Scene - yeah! More than 2 Taps!

The Central Scene handling code now supports up to 5 button taps through the attribute "multiTapButton".

The attribute "multiTapButton" can be used in rule machine. Whenever a button is tapped, there will be a "multiTapButton" event (in addition to "traditional" pushed and doubleTap events, which you can still use).  The multiTapButton attribute value is specified in decimal notation, with the whole-number part  (i.e., before the decimal) being the button number, and the decimal part indicating the number of taps. Thus, for example, multiTapButton = 4.3  means that button 4 was tapped 3 times.

## Supervision Support
This driver now supports Z-Wave's command supervision class. The Supervision command class allows the Hubitat hub to receive confirmation from a device that a command was received and processed. If the hub doesn't receive that confirmation, it will re-send the command. What this means is improved reliability (for devices that implement the Supervision command class).

## EndPoint Support
The driver now provides better support for endpoints.

On first boot, the driver checks if the device has endpoint support and if any endpoints exist. If so, the driver checks if their deviceNetworkId is in the format expected by the driver. IF it isn't the driver will delete and replace the endpoint child devices (after this is done, you may have to reset rules using those child devices).  Note that the "proper" format is one ending in "-epXXX" where the XXX is replaced by the endpoint number (e.g., "-ep001").

The endpoint code will replace the endpoint driver with either a switch, metering switch, or dimmer component driver.

## Fan Support
###Inovelli LZW36
The driver supports fan control using the Inovelli LZW36 controller.
To support the  Inovelli LZW36, install and initialize the Dimmer driver, then change endpoint #2's driver to "Generic Component Fan Driver". DO NOT use the "Hampton Bay Fan Component" driver.

## Window Shade Support (Maybe)

This is relatively untested, but the driver Should work with window shades. To use with window shades:
* Cut/paste a copy of the driver into Hubitat as a new driver
* Change the name in the metadata to indicate this copy is for Window Shades
* Uncomment the "WindowShade" capability, and comment out the "Switch", "SwitchLevel", and "ChangeLevel" capabilities.



### This is still a work-in-progress. 

Some tips:
* Install the driver on the "Driver Code" page. It will appear with the name "Advanced Zwave Plus Dimmer
* Go to the device that you want to work on and select this driver as the device's driver "Type".
* Click on the "Reset Driver State Data" button to clear the stored "state" inforamtion from the prior driver.
* Click on the "Initialize" control to pull the data from the database and to poll your device for its current parameter settings.
* After this completes, then click on "Save Preferences" and the web page will now refresh. After it refreshes, the "Preferences" area should now show controls for all of the parameters.  If everything worked right, the controls should show the current settings of your device
* You should now be able to change / udpate the parameters.

* Its recommended that you reboot after assigning the driver to your devices.


## Donate
Some have asked if they could donate something to me for this work.  I am not asking for donations for myself. However, if this driver helped you, I ask instead that you take whatever you think you would have donated and find food bank or other charity in your area and donate it to them instead (there are plenty of online foodbank sites). Here's one I can suggest: https://foodbankcenc.org/ . I opened an issue "Donate"- let's hear from those who have given something in response!

# Known Bugs
Version 0.1.0
A Hubitat bug exist in parsing meter reports. You may see occasional parsing errors in the log related to this.

# Version History
0.1.0 - This was a complete rewrite to better handle concurrency and better handle endpoint support.
0.0.7 - Numerous fixes for setting of parameters. Range checking on parameter inputs. Fixed errors in handling meter reports from devices with multiple endpoints. Additional support for multi-endpoint devices.
0.0.6 - Numerous fixes for setting of parameters. Fix to avoid trying to set a "null" parameter. If currently stored paramenter is a "null" then try to re-get parameters on an Update.


