# Changelog

Every version of Gold Box Game Engine for Android. A release's notes are its
part of this file.

## [0.1.2] - 2026-11-23

Built on PM-GoldBoxGameEngine 0.12.16 and the CYD engine 0.215.2. Tom's tries on his phone:

- **Diagonals in a fight**: while a fighter moves or aims, the on-screen D-Pad's corners are the four diagonals
  (two directions at once); a thumb can slide round all eight.
- **The 3D view comes back right after a fight** (the monsters' picture - Curse's Fire Knives - stayed until a
  step redrew it). Fixed in the engine, every game.
- The on-screen **Start reads MENU, Select MOUSE** (Pointer Mode: in a game, an arrow the D-Pad moves; A taps
  where it points).

## [0.1.1] - 2026-11-23

Built on PM-GoldBoxGameEngine 0.12.15 and the CYD engine 0.215.1. Tom's first tries on his phone:

- **One tap chooses** in the menus (was: the first tap picked a line, the second chose it).
- **A tap in the game moves the highlight to what it picks** before acting (the party menu: tapping Modify
  Character left the highlight on Drop Character while it asked for a character; the menu line's words too).
- **The screen turns with the device** (it stayed the way it was held at the start), and **Display › Screen
  Rotation: Auto / Landscape / Portrait** holds it one way.
- **The camera cutout / notch is kept clear** (Tom's Pixel 10a: it sat in the top centre of the game in
  portrait): the game, the on-screen buttons and the Companion stay out of it, in either orientation.
- From the engine: AD&D 1E Rules › Level Drain & Restoration.

Checked on an Android 11 emulator: turning to landscape and back (Auto), and a punch-hole camera (Android's
cutout simulation) in portrait and landscape. The tap highlight not tried in a game here (no game files).

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
