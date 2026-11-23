# Changelog

Every version of Gold Box Game Engine for Android. A release's notes are its
part of this file.

## [0.1.0] - 2026-11-23

The first version. Built on PM-GoldBoxGameEngine 0.12.13 (the front end) and
the CYD engine 0.214.10.

- The engine and the handheld port's front end as an Android app: every game,
  menu and feature the PortMaster port has.
- **The folder screen**: where the games go, named as the phone and a Windows
  PC show it ("Internal storage › GoldBox › games"); Play (the default folder,
  All Files Access), Choose Folder… (a folder of your own, an SD card on
  Android 11 and later), or the app's private folder. In the game: Start ›
  System › Games Folder brings it back.
- **Controls, any mix**: a controller (Bluetooth / USB / a handheld's own),
  the on-screen buttons (Auto / Always / Never - Start › Controls), and touch
  on every screen (tap to pick, tap again to choose, swipe to scroll, two
  fingers = Start, Back = B).
- Left out on Android: Check for Updates and Send Logs (the handhelds'
  scripts) - updates come as a new APK or from F-Droid; the log is `log.txt`
  in the Gold Box folder.

Checked on an Android 11 emulator: the folder screen, Play, the game list,
the menu by touch, Games Folder back to the folder screen. Not yet on a phone;
no game played on Android yet.
