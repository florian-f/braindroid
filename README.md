# braindroid
Android App for EEG and BCI

Right now it works with [OpenBCI](http://openbci.com/). At the moment it is only able to record to file (and you have to access the files via adb pull, sorry for that :).

You need the [FTDI android drivers](http://www.ftdichip.com/Drivers/D2XX.htm) to get this App to work. Simply put the d2xx.jar in the libs folder and gradle should take care of everything else.

If you experience any problems please message me and I will do my best to help get you set up.

A nice UI and the ability to share files with gDrive/ email/ usb/ whatever will be added soon, as well as pretty real-time visualisations.

I hope to eventually support devices other than OpenBCI.

