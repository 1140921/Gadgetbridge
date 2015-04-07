Gadgetbridge
============

Gadgetbridge is a Android (4.4+) Application which will allow you to use your
Pebble without the vendors closed source application and without the need to
create an account and transmit any of your data to the vendors servers.

We plan to add support for the Mi Band and maybe even more devices.

Features:

* Incoming calls notification and display (caller, phone number)
* Outgoing call display
* Reject/hangup calls
* SMS notification (sender, body)
* K-9 Mail notification support (sender, subject, preview)
* Support for generic notificaions (above filtered out)
* Apollo playback info (artist, album, track)
* Music control: play/pause, next track, previous track
* List and remove installed apps/watchfaces
* Install .pbw files (EXPERMIENTAL)

How to use:

1. Pair your Pebble though the Android Bluetooth Settings
2. Start Gadgetbridge, tap on the device you want to connect to
3. To test, chose "Debug" from the menu and play around

Known Issues:

* Android 4.4+ only, we can only change this by not handling generic
  notifications or by using AccessibiltyService. Don't know if it is worth the
  hassle.


## Download

[![Gadgetbridge on F-Droid](/Get_it_on_F-Droid.svg.png?raw=true "Download from F-Droid")](https://f-droid.org/repository/browse/?fdid=nodomain.freeyourgadget.gadgetbridge)