# HubitatCustom
# Advanced Zwave Plus Dimmer driver - Alpha Release!

The file "Advanced Zwave Plus Dimmer Driver.groovy" is a dimmer driver file that can identify all the parameters for a device and provides input controls allowing the setting of each parameter.

The way this works is that the driver queries the opensmarthouse.com database using the device's manufacturer, device type, and device ID information to retrieve a database record identifying all the parameters for the device. That information is then saved in the device's "state".

#### This is still a work-in-progress. 

Some tips:
* Install the driver on the "Driver Code" page. It will appear with the name "Advanced Zwave Plus Dimmer
* Go to the device that you want to work on and select this driver as the device's driver "Type".
* Click on the "Reset Driver State Data" button to clear the stored "state" inforamtion from the prior driver.
* Click on the "Initialize" control to pull the data from the database and to poll your device for its current parameter settings.
* After this completes, then click on "Save Preferences" and the web page ill now refresh. After it refreshes, the "Preferences" area should now show controls for all of the parameters.  If everything worked right, the controls should show the current settings of your device
* You should now be able to change / udpate the parameters.

# Known Problems
* Operation is a bit dicey when dealing with parameters that are expressed as bitmap fields!.  This is due to a bug in the Hubitat input control which I've reported to Hubitat. What happens is that the bitfield parameter input should allow you to click-select multiple inputs (i.e., to select multipe ones of the "bit" choices), but sometimes it only operates for single choice selectin

# Advanced Zwave Plus Switch driver - Work in Progress!

The Advanced Zwave Plus Dimmer driver code was written to be usable for both switches and dimmers. The only real difference is which capabilities are left commented / uncommented.  I plan to add metering support making this "truly" universal, and will then release a switch version (as well as an updated dimmer version).
