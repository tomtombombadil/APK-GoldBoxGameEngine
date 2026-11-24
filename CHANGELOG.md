# Changelog

Every version of Gold Box Game Engine for Android. A release's notes are its
part of this file.

## [0.3.0] - 2026-11-24

Built on PM-GoldBoxGameEngine 0.15.0 and the CYD engine 0.216.2. The redesign's second stage.

- **The menu is a drawer from the left** (the MENU handle, or a controller's Start), in Android's letters and
  look: a page a screen with a back arrow and a close button, switches drawn as switches, a choice with < and >
  beside its value, the highlighted line's explanation under it. Tap beside it, swipe it to the left, or press
  Back to put it away. A controller still moves through it, and the drawer follows.
- **Audio - Volume**: a slider, - and + (5 % a press), and a mute button (unmuting brings the level back).
- **Screen Rotation** is now on the menu's first page; the Display page is gone (it had nothing else left).
- The Button Guide, Device Info, notices, the journal list and Quick Actions are still the game's own screens:
  the drawer steps aside while they are up.
- From PM 0.14.1 / engine 0.216.2: Treasures heals the party at the ending and after the demo's fight, as the
  original does.

Checked on the Android emulator by touch: upright - the top page, Audio opened, the volume muted and unmuted
(the saved level stayed 70), closed by a tap beside it; sideways - the drawer over the dimmed game list, Game
Play with its switch and pages, the back arrow. Not tried there: the deeper pages, a switch flipped, a
controller in the drawer.

## [0.2.0] - 2026-11-24

Built on PM-GoldBoxGameEngine 0.14.0 and the CYD engine 0.216.1. The first stage of the redesign (the mockups
Tom approved): the controls round the game are now the app's own, in Android's look.

- **The pad**: 8 ways with OK in the middle, Back beside it, a pin. The corner (diagonal) keys light up in a fight,
  when a fighter moves or aims. A key gives a little buzz when pressed; a thumb can slide from key to key.
- **Phone held sideways**: the game at the left; beside it the Companion's map at the top - tap it for the party
  and back - and the pad under it. **Held upright**: the game, the Companion (map and party) under it, the pad at
  the bottom. **Tablet sideways**: the game fills the screen; the Companion and the pad come out on demand.
  **Tablet upright**: as the phone.
- **Handles on the edges**: left - the menu; right - the Companion; bottom - the controls. Tap one, or swipe in
  from it.
- **Pins**: a pinned Companion or pad stays out and is remembered for that way of holding the device. Unpinned,
  it is swiped away (the pad down or to the side, the Companion sideways) or put away by touching the game.
- **A controller**: the pad goes away while one is used; a touch brings it back. Y brings the Companion out
  and puts it away.
- Gone from the menu: Display - Picture and the on-screen button lines (the old see-through buttons are
  replaced by the pad). Display - Screen Rotation stays until the menu drawer comes (the next stage).
- From PM 0.13.2 / engine 0.216.1: Treasures stops training at the game's last levels; Darkness's and Treasures'
  Cloaks of Protection give nothing over magic armour, as in the originals.

Checked on the Android emulator (2280 x 1080, a camera hole cut out) with Curse: the scan, the game list, the
title screens and the demo by the pad's OK; sideways - the game at the left, the Companion's map over the pad,
the MENU handle; upright - the game under the cutout, the demo's fight with the battlefield and the party under
it, the pad at the bottom. Not tried there: the pins, swiping panels away, the handles' swipes, the map / party
tap in a game of one's own, a fight's corner keys, a controller, a tablet's shape. The front end's side of the
layouts also with Curse on the PC build.

## [0.1.5] - 2026-11-24

Built on PM-GoldBoxGameEngine 0.13.1 and the CYD engine 0.216.0.

- Clean Up Game Files' report names each folder it removed (the engine's newest).

## [0.1.4] - 2026-11-24

Built on PM-GoldBoxGameEngine 0.13.0 and the CYD engine 0.216.0.

- **Start - Clean Up Game Files** (beside Rescan Game Files): frees space on the phone. A whole GOG or Steam
  install copied over brings DOSBox, the installer's and the Steam launcher's files and DOS sound drivers the
  engine never reads; they go. Every game file the engine reads, every save, every PDF (manuals, cluebooks,
  journals - to read on the phone) and any file it doesn't know stay. A report: GoldBox › games › _CYD ›
  CLEANUP.TXT.

## [0.1.3] - 2026-11-23

Built on PM-GoldBoxGameEngine 0.12.19 and the CYD engine 0.215.2. Tom's Galaxy Tab S2: the game was small in the
middle, the button columns took the sides.

- **The on-screen buttons are an overlay**: the game as large as the screen allows, the buttons over it,
  see-through - in the empty band beside the game when there is one (a wide phone), else over its sides, above
  the game's menu line. A touch on a button is the button's, anywhere else the game's.
- **The arrow at the top left** hides the buttons and brings them back.
- **Start › Controls › Button Size** (Small to Huge) and **Button Opacity** (20% to 100%).
- **Display › Picture**: **4:3 + Companion Right** (the new default - the Companion beside the game, the D-Pad in
  the band left of it on a wide phone), **4:3 + Companion Below** (for holding it upright), or plain **4:3** (the
  Companion slides out on Y).
- **The Companion as large as its room allows**: with Companion Below on a phone held upright, the map and the
  party side by side under the game - each about four times the size of before.

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
