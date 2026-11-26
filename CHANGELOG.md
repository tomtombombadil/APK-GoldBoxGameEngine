# Changelog

Every version of Gold Box Game Engine for Android. A release's notes are its
part of this file.

## [0.8.0] - 2026-11-25

Built on PM-GoldBoxGameEngine 0.19.0 and the CYD engine 0.217.0. Tom's notes from the S8+, the Pixel 10a and the S2.

- **The Companion lays itself out on every screen**: from the room left beside or under the game and the controls,
  it tries the map and the party stacked, side by side, the party in two columns, or each in a room of its own,
  with its buttons down the side or across - and keeps whichever shows them largest. The pictures sit next to the
  game; the buttons at your hand's side, below the pictures when upright, never between them. One picture with the
  tap to switch only when both won't fit legibly. A new button swaps the map's and the party's places.
- **Journal entries in Android's own viewer**: a picture opens fitted whole - pinch to zoom, drag to move it,
  double-tap to zoom in and back; a text entry in a scrolling page in a proper typeface. No more "X Width  B Back"
  or entry-number lines; with a controller, X and B show as round buttons.
- **Display › Journal Font**: Journal (Fondamento, like the printed journal's hand), Book (Alegreya), Serif
  (Liberation Serif) or Plain (Roboto).
- **Menu › System › About**: the versions, the three GitHub repos, the licence and credits.

## [0.7.0] - 2026-11-25

Built on PM-GoldBoxGameEngine 0.18.1 and the CYD engine 0.217.0. Tom's notes from his Galaxy Tab S8+.

- **Tablets are told by Android's own measure** (the screen's smaller side 600dp or more), not by their shape: a
  16:10 tablet (the Tab S8+, 2800 x 1752) is a tablet now. Sideways a 16:10 tablet has the Companion and the
  controls out beside the game by default; a 4:3 one keeps the game maximised.
- **The chooser's toggle** in its bar (where the folder icon was): all the games at once (tiles) or one at a time
  (cards), on any device; also **Display › Game Chooser** (Auto / Tiles / Cards). The games folder: System › Games
  Folder.
- **The folder screen finds the Steam versions**: it looked for the games' files only one folder down; a Steam copy
  has them two down (<game>/GAME/<SHORT>). It counts as the game's scan does now (the Krynn files too; not the
  Launcher folder).
- **The menu**: "Gold Box Game Engine" at its top; Rescan Game Files and Clean Up Game Files under System.
- **The menu's tab** is at the hand's side with the Companion's, in the app's navy with a gold edge (it had blended
  into the game's frame); with the Companion out, its strip has a menu button. The menu slides in from that side,
  and all of it changes sides with the swap arrow.
- **The Companion beside the game**: the map as wide as the column at the top, the party right under it and as wide
  (its members' rows; the empty ones below cut off) when there is room for it - no tap to switch then. Only when both don't fit does the tap switch them, and the map is never made
  smaller to fit the party. The column stays the same width with the controls put away.

## [0.6.0] - 2026-11-24

Built on PM-GoldBoxGameEngine 0.17.1 and the CYD engine 0.217.0. Tom's review of 0.5.0.

- **Left or right handed** (as the CYD's): a swap arrow (in the Companion's strip, under its pin; on the controls
  when no Companion is out) moves the controls to the other side. Sideways the Companion and the controls are a
  column at that side, the game opposite. Upright the keys sit at that side; **a tablet upright has the controls
  in that corner and the Companion beside them, the full height and larger**.
- **The controls**: no drag bar - the keys are larger and sit toward the game; beside them the pin, Back under it,
  and a down arrow under that which puts them away (a swipe still does). When put away, their handle sits where
  they come out, not in the middle.
- **Pins** are quieter: a faint ring, gold when pinned.
- **The Companion**: its pin and the swap arrow in a narrow strip at its outer edge, clear of a phone's rounded
  corners; "tap for the party / map" turned along that strip on a phone (the map larger for it), above the picture
  on a tablet.
- **The game chooser**: "Gold Box Game Engine" in its bar; "GOG Version" / "Steam Version"; the phone's cards follow
  the finger and glide to the next game (a tap on a card no longer starts it); **Continue from Last Save** and
  **Play** (the game's own start - its Load or a new party); the games by series, Krynn's three last (a tablet's
  last row). Android's **Back at the chooser closes the app**.
- The menu drawer keeps clear of a camera cutout when the phone is sideways (either way round).

Checked on the Android emulator: the phone chooser upright (the swipe there and back, the labels), Continue into
PoolRad; in the game upright (the keys at the right, the small buttons inside, the Companion's strip) and
sideways (the column, the turned words, the pin clear of the corner), the swap arrow (all to the left); a tablet
upright at the S2's size and density (the controls in the left corner, the Companion beside them, larger) and its
grid with Continue. Not tried there: Back at the chooser, the drawer beside a cutout, the put-away button, the
handle's place, a tablet sideways.

## [0.5.0] - 2026-11-24

Built on PM-GoldBoxGameEngine 0.17.0 and the CYD engine 0.217.0.

- **Continue** (Tom, 2026-11-24): a game with a saved game shows **Continue** on the chooser - one tap and you are
  back in your newest save, past the title screens and the party menu. **Start from the beginning** under it
  plays the titles as before. On a tablet, a gold Continue on the game's tile; a tap elsewhere on the tile
  starts from the beginning. The chooser learns that a game has a save once the game has been opened (the copies'
  sample parties count) - the save itself is looked for at the tap, the newest by its file's date.

Checked: through the PC build, Continue with the nine GOG copies (eight into their newest save; Gateway's copy
has none and says so); on the Android emulator, upright, PoolRad's Continue from the chooser - straight into the
game at its save with the Companion and the pad. Not tried there: the tablet tile's Continue, Start from the
beginning.
- From the engine 0.216.3 / .4: a flight's runners can fight again; Resist Fire / Resist Cold halve their damage
  in Secret, Champions, Darkness, Treasures and Death Knights.

## [0.4.0] - 2026-11-24

Built on PM-GoldBoxGameEngine 0.16.0 and the CYD engine 0.216.2. The redesign's third stage, and what 0.3.0 missed.

- **The game chooser is the app's own**, shaped for the device: a phone held upright shows one game a card (its
  picture upright, the neighbours peeking - swipe sideways), with a big Play button; held sideways, the picture
  wide at the left and the name, Play and < > at the right; a tablet shows every game at once (three across; two
  when there are four or fewer) - tap one to play. The top bar has the games folder and the menu.
  No games: where to copy them, Look Again and Choose Folder.
- **The pictures are made once, when a game is found**: the Steam box art decoded at full quality, or the game's
  own logo. Games found before get theirs at the next start ("Preparing the picture for ..."); turning the
  device no longer makes pictures again.
- **The scan's page** (finding games, Clean Up Game Files) and **every notice and message** (Yes / No questions,
  results, Please wait) are in Android's look now.
- **In the menu drawer**: Device Info, the Button Guide, the journal list and Quick Actions are drawer pages now
  (they were the old screens).
- **Put back** (Tom: "you missed some menus and menu items"): the Display page (Picture: 4:3, Square Pixels or
  Stretch; Screen Rotation), Controls - Button Size (it sizes the pad), Back to Game. Button Opacity is not
  offered: the pad has a room of its own and never covers the game.

Checked on the Android emulator: the scan's page and its Continue; the chooser upright (a swipe to the next game),
sideways, and as a tablet (2048 x 1536) with three GOG games and the Steam box art; the drawer's Display,
Controls and Button Guide pages; Journal's notice and its Close; Quick Actions. Not tried there: Play from the
chooser into a game (the tablet tap did start Champions), the empty chooser, Device Info's page, Button Size's
effect, a Yes / No notice.

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
