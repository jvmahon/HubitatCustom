# HubitatCustom
# Super Parameter Setting Tool - Beta!

This is a parameter setting tool that uses the device's manufacturer, device type, and device ID information to retrieve parameter setting information from a dabase.

#### This is still a work-in-progress. Estimate first release by Dec. 15, 2020. Work is still being done to handle bitmap parameters (so recommendation is that you don't fork this repository for your own use yet!)

#### Update: 2020.12.03 - Slight change in direction - I expect to consolidate the code for this parameter tool into a single "driver" codebase which, by commenting / uncommenting code sections, will be usable as a "generic" paramter setting tool, a dimmer driver, or a switch driver.

Some tips:
* Install the driver on the "Driver Code" page. It will appear with the name "Super Parameter Setting Tool"
* Go to the device that you want to work on and select this tool as its driver.
* Click on the "Initialize" control to pull the data from the database and to poll your device for its current parameter settings.
* The "Preferences" area should now show controls for all of the parameters.  If everything worked right, the controls should show the current settings of your device
* You should now be able to change / udpate the parameters.
* Before switching back to your original driver, you can click on the "Uninstall" button to clean the State Variable data if you want to remove it.



# Universal Z-Wave Driver 

This is a device driver (dimmers & switches mostly) that will incoprorate the features of the Super Parameter Setting tool.

This is still in "beta" and isn't ready for general use. I'll update this page with some more information once I get time to develope it further.
