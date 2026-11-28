# CLAUDE.md - working rules for this repo

Gold Box Game Engine for Android: an APK of the CYD Gold Box Game Engine with
the PortMaster port's SDL2 front end. Repo `tomtombombadil/APK-GoldBoxGameEngine`
(Tom, 2026-11-23: its own repo, "especially considering F-Droid's requirements").
The player brings their own game files.

## The developer
- Tom works on **Windows**: never Linux / bash commands in instructions for
  him - PowerShell or a GUI. A Python tool on his side: a venv and
  `python -m pip install`.
- He does not want to repeat instructions: anything decided goes in this
  file the moment it is decided; open items in `docs/TODO.md`.

## Decisions (Tom, 2026-11-23)
- The app's name is **Gold Box Game Engine**; the APK file
  **`GoldBoxGameEngine-<version>.apk`** (app/build.gradle names it). Package
  `io.github.tomtombombadil.goldbox` - never change it (installs and F-Droid
  are tied to it).
- **Not for the Play Store**: open source, side-loaded (Releases) and F-Droid.
  Nothing non-free in the build: no Google Play Services, Firebase, analytics,
  ads; no prebuilt binaries (F-Droid's rules, `docs/FDROID.md`).
- **Controls: "compatible with touch and/or a controller (via bluetooth or USB)
  or an onscreen overlay representing the controller"** - all three, any mix.
  Lives in the front end (PM repo: `src/touchpad.*`, `touch_tap` /
  `touch_swipe`; its SPEC 4 "Touch").
- **The folder must be plain to the player** ("GoldBox/games doesn't tell me
  where that folder is on the device"): every path on screen as the phone's
  file manager and a Windows PC show it - "Internal storage › GoldBox ›
  games", never /storage/emulated/0 (`SetupActivity.shown`, the front end's
  `shown_path`).
- **A camera cutout / notch is kept clear** (Tom, 2026-11-23, Pixel 10a): `GoldBoxActivity.cutoutInsets()`
  gives the safe margins; the front end lays out inside them. Emulator check: `cmd overlay enable
  com.android.internal.display.cutout.emulation.hole`.
- **Display › Screen Rotation: Auto / Landscape / Portrait** (Tom, 2026-11-23); the front end's.
- **The player can point the app at their own folder** ("a goldbox folder of
  their own creation"): Choose Folder… on the folder screen; Start › System ›
  Games Folder in the game brings that screen back.

## The redesign (Tom, 2026-11-24: "It needs a serious glow up ... not like a native Android app")
Mockups (approved, "I like your mockups and suggestions"; button layouts to be cleaned up later):
https://claude.ai/artifact/RRdN3okeHz21B9hQMBorqB. Tom accepted every suggestion made with them.
- **Android's own UI for everything but the game**: the app's views (Roboto, Material 3 look: navy #0B1024,
  surfaces #141B36, gold #E8B840, cream #F0E6C8) round SDL's picture; SDL draws the game and the Companion's
  pictures only. Built in stages (`docs/TODO.md` "Redesign").
- **Swipe-outs** - the hand's side: the menu drawer and the Companion (2026-11-25; the menu had been at the left); bottom: the controls (8-way pad - diagonals -
  with OK in its centre, Back; no menu button on the pad: the menu is its tab). The right and bottom ones have a
  **pin**: pinned they stay out (and are remembered); unpinned they can be swiped back (and close when the game
  is touched). Each opens from a **handle** on its edge (system Back / Home gestures own the edges:
  `setSystemGestureExclusionRects`, ~200dp an edge). Opening one makes the game smaller to make room.
- **Default layouts by aspect ratio and orientation** - phone (16:9 or wider) landscape: the game at the left,
  beside it a column: **the Companion's map at the top - a tap flips it to the party and back - and the
  controls under it** (Tom, 2026-11-24, taking the trade-off: the controls in place of the Companion's party
  rather than a tray that shrinks the game). Phone portrait: the game, the Companion under it, the controls at
  the bottom. Tablet (near 4:3) landscape: the game maximised, everything swiped out on demand (a 16:10 tablet: as the phone's). Tablet
  portrait: as the phone's.
- **Audio**: each volume a slider, - and + buttons and a mute button. The Display menu goes away in favour of
  the app's own controls.
- **Game chooser**: art shaped for the device (upright box art on a phone held upright, wide art sideways); a
  tablet shows all nine games at once.
- **Tom's review of 0.5.0 (2026-11-24)**:
  - **Handedness** (as the CYD's): one setting, the swap arrow (the Companion's strip, under its pin; on the pad when
    no Companion is out). Sideways: the Companion's column - the Companion over the pad - at the hand's side, the
    game opposite. Upright: the pad's keys at the hand's side (a phone: the pad across the foot, its small buttons
    mirrored); **a tablet upright: the pad in the hand's corner, the Companion opposite it, the room's full height
    and larger**.
  - **The pad**: no drag bar (a swipe still puts it away); keys larger, toward the game; beside them a column -
    the pin, Back under it, a down arrow under that which puts the controls away. **Pins subdued** (a faint ring,
    gold when pinned).
  - **The Companion's strip** at its outer edge (pin, swap arrow) clear of the phone's rounded corners (the
    pin had gone off the corner); "tap for the party / map": turned along the strip on a phone, above the picture
    on a tablet - never where it seems the pad's.
  - **The controls' handle** (put away) sits where they come out - the hand's side - not centred.
  - **The chooser**: "Gold Box Game Engine" in its bar; "Steam Version" / "GOG Version"; the phone's carousel
    follows the finger and glides (a tap on a card does nothing - Play is the button); **"Continue from Last
    Save"** and **"Play"** (Play: the game's start as usual - its own Load or a new party); the order by series
    (Gateway, Treasures, then the Krynn three - a tablet's last row). Tom loves the tablet's tiles with the Steam
    art and the phone layouts. The menu drawer keeps clear of the cutout sideways. **Android's Back at the
    chooser closes the app** (nothing over it; elsewhere Back is the game's B).
- **Tom's notes from his Galaxy Tab S8+ (2026-11-25)**:
  - **A tablet is Android's own measure**: `smallestScreenWidthDp >= 600` (`Shell.tablet`), never the screen's
    shape (the S8+ - 2800 x 1752, 16:10 - had been taken for a phone). Sideways a tablet of 16:10 or wider has the
    column out by default (room beside the game); near 4:3, the game maximised.
  - **The chooser's bar: a toggle tiles / cards** in place of the folder icon (any device); the same as
    **Display › Game Chooser: Auto / Tiles / Cards** (the front end's `chooser`; op 14). Auto: tiles on a tablet.
  - **The folder screen counts games as the scan does** (Steam: <game>/GAME/<SHORT>, two down; Krynn's TLB / GLB;
    not Launcher / GBC) - it had said "No games in it yet" with nine Steam games. **Its rework**: suggestions
    made, Tom to choose (docs/TODO.md).
  - **The menu**: titled **"Gold Box Game Engine"** on its top page (as the chooser's bar - every place the app
    names itself says it in full); Rescan / Clean Up under System (Android only; a handheld keeps them on top).
  - **The menu's tab at the hand's side** with the Companion's - stacked, the menu's first - in navy with a gold
    edge and gold words (not see-through: it had blended into the game's frame); with the Companion out, a menu
    button in its strip (under the swap arrow; on a phone sideways, where the turned "tap for the party" words
    would then not fit along the strip, the menu button goes to the bottom of the pad's column instead). The drawer slides in from that side. All of it swaps sides with
    the swap arrow.
  - **The Companion is smart**: sideways in the column the map as wide as the column at the top, the party right under
    it, as wide as the map, its members' rows only (the empty rows cut, never shrunk and centred) when there's
    room (three quarters of the map's height: the front end's view 3) - no tap then; only when
    both don't fit, one with the tap. **Never shrink the map to fit the party.** **The column's width is the same
    with the controls away** ("Making the map smaller isn't a good look").
- **Tom's notes, 2026-11-25 afternoon** (screenshots from the S8+, Pixel 10a, Tab S2):
  - **The journal on Android, Android's own** (part of the glow-up): no "X Width  B Back" line, no entry number
    / % line drawn by the game; X and B as round buttons, and only with a controller (or the pad in controller
    use). **A picture entry**: an Android image viewer - pinch to zoom, drag to pan, double-tap to zoom in / back
    to fit; it opens fitted whole (no zoom to width on a tablet). **A text entry**: Android's type in a
    scrolling pop-up, not the game's pixel font (that stays for PortMaster); a setting for the journal's font,
    with an open-source font like the printed journal's.
  - **The Companion fills its room, intelligently, on every device**: the map and the party as large as they
    fit without taking from the game or the pad; the strip's buttons (pin, swap, menu) down the side or across
    the top / bottom - whichever leaves more room; the party may go above the controls, beside them, or in two
    columns (the S2 upright) - try layouts and pick per room. **Upright on every device: map and party side by
    side, the controls at the foot**; when they don't fit, the tap toggle. **Keep the phone-sideways tap toggle.**
    Tom: "It needs to be just right or the app will look really bad."
  - **The Companion's layout, by rule, never by device** (Tom, 2026-11-25 evening: "I can't test this on every
    device ... the layout needs to be smart about it by design/logic"): from the room it has (the screen less the game
    and the pad), candidates - the strip down the outer side or across the edge away from the game; map and party
    stacked, side by side, the party in two columns, or each in a room of its own (different sizes); one picture with
    the tap when both don't fit legibly (and, phone sideways, when one picture is much larger) - the largest
    pictures win. **The pictures nearest the game** (upright: right under it, the strip below them; sideways: against
    the game's side, the strip at the screen's edge); **the strip never between the pictures**, at the hand's side.
    **A fourth strip button swaps the map's and the party's places** (when both are shown). Simulated per device
    before building (Pixel 10a, S8+, S2, both ways round).
  - **Journal fonts** (Display › Journal Font): Journal - Fondamento Italic (default, the printed journal's
    chancery hand), Book - Alegreya Italic, Serif - Liberation Serif (Times New Roman's shape; Tinos not to be had as a file), Plain - Roboto. Bundled under
    their free licences (OFL / Apache 2.0), credited in About.
  - **About** in the menu (System › About): the engine's information, Tom's GitHub repo address (the rest of his
    list was cut off); built: the app's, front end's and engine's versions and repos, MIT, credits.
  - **Built as** (0.8.0): `CompLayout` (plain Java, the rules above; the front end draws each picture where the app
    says - view 4, `nativePictures`; channel 4 says what the pictures hold); `JournalView` (channel 5; fonts in
    `assets/fonts` with their OFL texts). Under 150dp a picture, the Companion starts put away (its own default).
- **Tom, 2026-11-26 evening** (what's next):
  - **The Steam journals' OCR misreadings are corrected** ("That's important!"): the Steam PDFs' own text layers
    misread the scans ("~i INTRODUCTION ~", "part}'1"); the engine corrects them by a table per journal.
  - **The polish**: edge-to-edge and predictive Back, Android backup of saves - yes. **No splash screen** ("I've
    never liked them"). **The adaptive icon**: proposals first. **No home-screen shortcut per game made by
    itself** - a menu line to add a game to the home screen is fine. **Foldables / split screen**: mock-ups first
    (Tom thinks the layouts by rule already cover them).
  - **The folder screen: tabled** - Tom will find the earlier suggestions in the chat.
  - **Built** (0.9.0): the engine's `journal_fixes.cpp` (188 fixes, three rules - CYD 0.221.6); the chooser's hold menu
    **Add to Home Screen** (`Shortcuts`; a pinned shortcut to SetupActivity with the game's folder -> the front
    end's `--game-folder`; tapped while the app runs: at the chooser it starts again on that game, in a game the
    game comes back as it was - a shortcut never ends a game unsaved); predictive Back (`GoldBoxActivity.updateBack`: the app takes Back except at the chooser
    with nothing over it - there Android's own gesture closes it); SetupActivity edge to edge; backup rules
    (`res/xml`: cloud - the preferences and the private folder's conf; device transfer - the private folder whole).
    Saves in Internal storage › GoldBox are beyond any app's backup (Android's rule for shared storage).
- **Tom, 2026-11-26 night**: the Dark Queen's Steam Introduction (the General's letter - its text layer
  unreadable) is **a picture of the page, as the GOG journals' pages are**. **The icon stays the treasure chest**,
  made to look like **real metallic gold** and more like a treasure chest - options shown to Tom first.
- **Tom, 2026-11-27**: **the icon is A** - the chest in metallic gold, centred (the old one sat a little low).
  **Steam journal text entries: paragraphs indented, a little space between them** (they ran together). **The
  Journal menu: the journal PDF itself, a page at a time** (a book spread split into its two pages, never the
  double layout), **and the game's other PDFs** (manual, rules, cluebook ...) - all in the Journal menu.
  **Built** (0.9.2): the icon (`drawable-v24/ic_launcher_fg/_bg`, gradients - Android 7+; `drawable/ic_launcher`
  flat for 5 - 6; `ic_launcher_mono` themed); `JournalView.paragraphs` (a line a paragraph, indent, half-line gaps,
  capitals headings centred, broken sentences joined); `PdfView` (Android's PdfRenderer - nothing added to the
  build; a page 1.2 - 1.7 times as wide as tall is a spread: two pages; the last page per file kept), opened by the
  front end's `android_open_pdf` from the Journal list's Documents (PM 0.19.12, `jour::documents`).
- **Tom, 2026-11-27 afternoon** ("The Steam PDFs look great!"): **Back from a PDF returns to the Journal list**
  (it went to the main menu). Document names in title case ("codewheel-pictures" -> "Codewheel Pictures").
  **Page turns without the wait**: Android has the memory - pages rendered ahead / the PDF held in memory.
  **Sideways: two pages side by side as an open book; upright: one page** (as now). The cover alone even sideways,
  then the pages paired as a book's. **A scan whose first PDF page is the cover front and back as one spread**
  (the back cover shown first): detected and put right. **The chooser's tiles appear sooner** after the app opens.
  **The Companion's maps from the Cluebooks' maps** - accurate, with their legends' markers (special events, items,
  places); an engine change (CYD, PortMaster, Android). Its flaws seen: secret doors that aren't.
  **Built** (0.9.3): `PdfView.makeBook` (a first page that is a spread: its right half first, its left last) and
  `makeScreens` (sideways two book pages a screen, the cover / a wide page alone); pages rendered whole once into an
  LruCache (Android 8+: half the heap's size or 256 MB of bitmaps), the next two screens and the one before rendered
  ahead; Back and B close it on the key's release (its down had closed it and its release reached the game). The
  start: PM 0.19.21's platform layer lists a folder without opening its entries, remembers found folders and keeps a
  listing 3 s (forgotten on any write) - the engine's scan (game_find.h, the boards' way) re-lists folders.
- **Tom, 2026-11-27 (15:04)**: **no scanning when the app opens** - "the whole purpose of the 'Scan the SD Card'
  phase ... is to be run ONCE and not needed again unless the user adds or deletes a game's files ... It should all
  already be prepared and waiting for the app." The games found (each folder's game and where its files are) are
  kept from the scan and read back at a start (PM repo). He has added the Steam PDFs (cluebooks, manuals, quick
  start guides) to the Project - Curse's in two zips, Secret's cluebook compressed; the GOG ones to follow, to
  compare: both kinds must work. **Built** (0.9.4, PM 0.19.22): `library::scan_kept` - a scan writes
  games/_CYD/GAMES.TXT (the top folders it saw, each game: folder, data_dir, game, hlib, files); a start reads it
  when the top folders' names are the same and each data_dir exists, else scans.
- **Tom, 2026-11-27 (17:00)**: the cluebook work goes on - examine the maps, develop, implement and test. **The
  cluebooks name the areas**: the Companion's maps get those names (long wanted). **GameBanshee's maps** to compare
  against, for the markers and symbols. The checker: the engine repo's `tools/cluebook` (its docs/TODO.md).
- **Tom, 2026-11-27 (17:39)**: **both map kinds** - a toggle in the Enhancements menu: on, the Companion shows the
  **cluebook's own map picture** (from the player's PDF) **with the party's arrow placed on it**; off, our own drawn
  map. **PortMaster and Android only** (the CYD's resolution can't). Archways drawn as archways, not doors (he knew;
  "you can pass through unhindered" either way, but shown properly is better). **17:44: doors, archways and
  entrances to another map / area each drawn differently** on the Companion's map.
- **Tom, 2026-11-27 (17:54)** - the cluebooks' symbols: **a secret door is a wall line with an S laid over it** (the
  S's upper half one side of the line, its lower half the other) - not a hook or an arch; **an archway is a U turned
  across the wall, bisecting the line** (which way the U opens means nothing - a convention, the same through a
  map). **One-way doors**: they open from one side only, are often seen only from that side, and once through there
  is no way back - to be shown as such. (18:05, Tom: **a one-way door is not a concealed door** - he had misspoken;
  the key says "One-way door". **19:46, Tom - AD&D's terms: a concealed door is an ordinary door hidden from view by
  something (behind a tapestry, a curtain); a secret door is built to blend into the architecture - looked at, never
  seen as a door.** The Steam cluebooks name no concealed door (their text searched); the map data can't tell one
  from a secret door.)
- **Tom, 2026-11-27 (17:58)**: **door codes 2 and 3 are not told apart on the map** (one red "Locked"). **A concealed
  (one-way) door: half a normal door on the side it is entered from, and an arrow pointing away from the wall on
  the side it comes out** - mocked up for Tom before it is coded. **18:05**: the mock-up good but **on a wall running
  up and down the S is turned 90 degrees**, and **every door (the concealed one too) leaves a little wall at each
  end** - else half a door can't be told from a whole one. **18:06**: **an archway's wall ends get a little serif**
  (a short tick across the line where each piece of wall ends, forming the arch). **18:08**: a little wall **between
  each serif and the corner** - the opening narrower, so a serif never meets the side wall.
- **Tom, 2026-11-27 (20:14)**, from the mock-ups: **no legend under the map** - no Locked / Secret / One-way words (the
  symbols say it; gold is every door's colour). **An entrance to another area: the square shaded** (option B), its
  key word **"Exit"**. **The cluebook's markers**: numbers in their squares; the **key a Companion view of its own on
  a handheld - Map > Key > Party > Map**; on Android **a tap on a marker shows its text, a tap elsewhere on the map
  the whole key**. **The cluebooks' scans curve near the binding** (pages not pressed flat): the map pictures
  **straightened**, the party's arrow placed by the straightened grid. Picture mode with the cluebook's area name
  (mock-up 1) - no objection.
- **Tom, 2026-11-28 (00:05 - 00:08)**: **the markers from a matrix of our own in the engine repo** - game, map, area
  name, square, marker number (facts we derived; "you don't need a better process, just a smarter one"); **no
  legend text in it** - option 1: the scan reads each numbered entry's words from the player's own cluebook PDF
  (its text layer, a fix table for misreads), as the area names. Editions with no text layer (GOG Gateway, GOG
  Secret): our own short wording.
- **Tom, 2026-11-28 (00:17)**: **the cluebook maps dark** - a display option to invert them (the game is black, the
  white maps stand out) unless the processing can; **the row and column numbers round the maps kept in the picture**
  (the games show the coordinates while walking). Gateway's Steam cluebook not recognised on his S2 (pieced pages -
  known gap). Exits still wanted.
- **Tom, 2026-11-28 (07:37 - 07:39)**: "somehow the send logs function is missing from the menus! ... Check your
  work!" - **Send Logs and Reply Address in the app** (they had been left out on Android since PM 0.12.13; built 0.11.1:
  `LogSender` posts sendlogs.sh's fields to the same relay, `ReplyDialog` - Android's box - for the address; the
  INTERNET permission, used for nothing else, README says so). **Every menu line the handhelds have, the app has,
  unless Tom has said otherwise.** **Quick Actions: Android's Back returns to the menu**, not the game. **Check an
  upgrade, not only a fresh folder** (Gateway's book was missed on a folder scanned by 0.10: its old "no cluebook"
  note was trusted - lists now carry their maker's version).
- **Tom, 2026-11-28 (08:59 - 09:15) - logs on Android go by the player's own email, not the relay** (to be built;
  0.11.1's relay sender and the INTERNET permission come out): "we should have discussed how best to do it" - **ask
  before choosing a way when Android offers several.** The menu line is **"Report a Problem"** (was Send Logs); **no
  Reply Address** on Android (the mail is from the player). An email intent (email apps only; none: **Android's Share
  menu**) to **cyd.classic.games.logs@gmail.com** (Tom may make a repo's own address later), **one zip attached**:
  the log, settings, the engine's notes **and a text file holding what the handhelds' relay email's body holds**
  (versions, device, Android, screen, memory ...). The email's body: **the player's answers** - questions such as
  the problem, which game, what area, what happened, what should have happened - either leading lines in the body
  or **asked in a "Report a Problem" dialog that then makes the email** (Tom leans to the dialog; mock-up first). **Say
  plainly what is sent: no personal information, only troubleshooting info** (make and model, Android version,
  memory ...).
- **Tom, 2026-11-28 (09:27)**: **the Report a Problem dialog** (mock-up approved) - the gold button **"Send via Email"**.
  **Title Case always for button text and menu item names** ("Report a Problem", "Send via Email", "Cancel").
  **A checkbox to include a save of the current game**: made at that moment for the report alone - like the engine's
  own save, but **never in a save slot, never overwriting anything**. Report a Problem **on the menu, available at any
  point the app is running** (also the chooser's hold menu). **The handhelds stay as they are** (Send Logs by the
  relay) - this is the Android app's alone. **Built** (0.12.0, PM 0.22.1, engine 0.226.5): `ReportDialog` (the form),
  `Report` (the zip - REPORT.TXT, DEVICE.TXT, log.txt, settings.cfg, engine/..., save/... - and the intents: a
  mailto selector, else the Share menu, else the zip in the GoldBox folder), `ReportProvider` (our own content
  provider for the zip, the cache's `reports` folder only - no androidx); the front end's `android_report` fills it
  in and makes the save (`play::snapshot` into games/_CYD/_REPORT/SAVE); the chooser's hold menu: op 18. LogSender,
  ReplyDialog and the INTERNET permission gone.
- **Tom, 2026-11-28 (10:54)**, from 0.12.0 on his Pixel 10a ("The Report a Problem function works" - in a game and at
  the chooser):
  - **Every screen of the app keeps clear of the camera hole / notch** - the scan's page didn't. A new view takes
    `GoldBoxActivity.cutoutInsets()` (or the window's insets) in the version that adds it; check with the emulator's
    cutout overlay (above).
  - The scan's page: **"Processing Game Files"** (not "Looking for Games").
  - **See What's Sent: each text file tappable**, showing its contents (DEVICE.TXT and the rest).
  - **Device Info**: each of the nine games **"Steam Version" / "GOG Version" / "Unknown Version" / "Not Present"**;
    and **what the app can see of the device - the same as a report's DEVICE.TXT**. Its version line with the **v on
    each version** ("engine v0.226.5").
  - **The menu's X closes the menu and returns to the game** (never Back - the arrow by the title is Back). **Quick
    Actions has the back arrow too.**
  - **Clean Up Game Files closes the menu as it starts** (its lines were hidden); **its Continue returns to the menu**,
    not the chooser. **Rescan Game Files the same** - it left the menu locked open.
  - **A menu line with a set of choices** (Game Sound ...): **a drop-down list or radio buttons**, not Left / Right taps.
    **Tapping a line's name shows its help text**; a switch changes only by its switch side. **Journal Font: each
    font's name shown in that font.**
  - **The chooser's bar: the version shown only when it fits** (the Pixel 10a's didn't); **the app's version always at
    the foot of the main menu**.
  - **Device Info, each game** (10:56): **the journal** found, recognised and processed; **the cluebook** found,
    recognised and processed; **the music** (the games that have it) found and processed. **Laid out** (10:57): each
    game's title across the full width in gold, its lines under it slightly indented, in white.
  - **About** (10:59): "Gold Box Game Engine" in its name column broke badly - **"Gold Box" / "Game Engine"** on two
    lines (0x1F in a line's name is the app's line break).
  - **"Companion" is the Gold Box Companion's name, not ours** - a new name for those features in all three repos;
    names proposed to Tom first.
  - **Built** (0.13.0, PM 0.23.0): `Shell.relayout` pads the scan's page and notices by the cutout; MenuDrawer's
    kinds G (a group's gold title, its T lines indented) and a choice's values (the row's 5th field, 0x1F between ->
    a PopupMenu with the one set ticked; a pick is op 19 with how many places on; `FaceSpan` for Journal Font), a
    tap on a choice's / switch's name shows its help, the switch alone flips; the foot's version; ChooserView's version
    hidden when it doesn't fit; `Report.device` (DEVICE.TXT's and Device Info's device lines -
    `GoldBoxActivity.deviceInfo` for the front end); ReportDialog's file rows open a viewer.
- Also accepted: edge-to-edge and immersive, predictive Back, haptics on the pad, Continue (the last save)
  first, a home-screen shortcut per game, layouts by window size (foldables, split screen), a splash screen
  and adaptive icon, Android backup of saves, a controller hides the pad.

## The repos
- `pm/` - **PM-GoldBoxGameEngine**, a submodule pinned to a commit; its
  `cyd/` is the engine (CYD-GoldBoxGameEngine). **Never edit files under
  `pm/` from here**: front-end changes are made, versioned and pushed in the
  PM repo (its CLAUDE.md, its smoke test), then the pin here moves. The
  Android-only parts of the front end are `#ifdef __ANDROID__` there.
- `SDL/` - SDL 2 at `release-2.32.10` (branch SDL2). SDL's own Java
  (`SDL/android-project/app/src/main/java`) is used as it is; a newer SDL 2
  release: move the pin, build, try on the emulator.
- When the PM repo or the engine changes something the app shows, move the
  `pm` pin, bump this app's version, and say which PM / engine versions the
  release is built on (CHANGELOG).

## Versions
- `version.properties`: versionName and versionCode (major * 1000000 +
  minor * 1000 + patch), both changed together; the game list shows
  versionName. A release: CHANGELOG entry, the fastlane changelog
  `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (F-Droid
  shows it; 500 characters at most), then tag `v<versionName>` - CI builds
  and publishes the release.
- **Claude tags and releases** (Tom, 2026-11-27 18:49: "I grant you permission to tag and release the APK repo"):
  each new version, once main's CI for its release commit is green - tag `v<versionName>` on that commit, push the
  tag, check the release and its APK. Never move or delete a tag. This session type can't push tags or make
  releases itself (2026-11-27: the git proxy's 403, the releases API refused), so **Claude releases by the Build
  workflow's dispatch** (Tom OK'd it, 18:51): `gh api -X POST repos/tomtombombadil/APK-GoldBoxGameEngine/actions/workflows/build.yml/dispatches
  -f ref=main -f 'inputs[release_tag]=v<versionName>'` - the tag made on main's commit then, the APK signed and
  attached; refused if the tag exists or isn't the versionName.
- F-Droid's yml (`fdroid/`): a build's `commit:` is the release commit's **full hash**, not the tag (Tom,
  2026-11-25, F-Droid's instructions) - so the release commit first, then a commit that writes its hash there.
  It must be the commit the `v` tag is on: F-Droid reads the store page (descriptions, screenshots) at that commit,
  so edits made after it (Tom's to the descriptions, 0.7.0) are missed by an older hash. Check with
  `git rev-parse v<version>^{commit}` once the tag exists. The fdroiddata copy: LF line ends, a final newline.
  Only the latest version's build in it (fdroiddata's rule for a new app's merge request).
  Categories **Emulator** and **Role-Playing Game** (fdroiddata's config/categories.yml - "Games" is no longer one,
  lint fails; ScummVM is Emulator, EasyRPG Player Role-Playing Game). Before
  a merge request: `fdroid lint`, `fdroid rewritemeta` (no change) and `fdroid scanner` run here in a venv against
  the fdroiddata fork's config (2026-11-25: all clean).
- **F-Droid's merge request !59131 was closed** (2026-11-26) by a reviewer: the project is under three months old
  (the repo dates from 2026-11-23). **Resubmit from 2027-02-23 on** - a new merge request, the recipe on the
  latest release then (its full hash; only that build). Until then: GitHub releases only. Never move or delete a
  `v` tag or rewrite history anyway (F-Droid's update check builds the tags once it is in). IzzyOnDroid is no
  way round it: its policy turns down games and AI-written code.
- **F-Droid signs its own builds with its own key** (Tom, 2026-11-25): no `Binaries` / `AllowedAPKSigningKeys`
  in the recipe, so no reproducible-build upkeep. The GitHub releases (Tom's key) and F-Droid's are separate
  installs: moving from one to the other needs an uninstall (the GoldBox folder and its saves stay; the private
  folder's don't). README says so.
- Release signing: the four `GOLDBOX_*` repository secrets (docs/FDROID.md
  step 1). The same key forever - a new key means players must uninstall.

## The app
- `SetupActivity` - the folder screen (its own process, `:setup`); the
  first start, a folder gone or unreadable, or the game's Games Folder line.
  Folder kinds: default (Internal storage › GoldBox), chosen (any path; the
  picker's tree URI turned into a path - All Files Access reads it), private
  (the app's own). A chosen folder with no `games` folder but game folders in
  it is the games folder itself.
- The redesign's views over SDL's surface (`Shell` lays them out): `Shell` (the pad, the Companion's room, the
  edge handles), `MenuDrawer` (the menu and its pages, Device Info, the Button Guide, the journal list, Quick
  Actions), `Screens` (notices / messages, the scan's page), `ChooserView` (the game chooser). Their words come
  from the front end as text (`onShellText`, channels 0 - 5 - 4 the Companion's content, 5 the journal; formats in the PM repo's main.cpp `android_*_text`),
  their taps go back as its buttons (`nativeButton`) or ops (`nativeMenu`). **A screen of the port's own is
  never drawn by SDL here** - a new one the front end adds needs its view here in the same version.
  `saveArt` is the scan's step for the chooser's pictures (APPART.IMG).
- `GoldBoxActivity` - SDL's activity; passes `--games --conf --log`; ends
  its process when it closes (the front end keeps state in statics);
  `chooseFolder()` is called by the front end (JNI).
- `app/jni/CMakeLists.txt` - libSDL2.so and libmain.so (engine + front
  end; the same sources as the PM repo's CMakeLists - keep the lists in
  step when that one changes).

## Checking work
- Build: `./gradlew assembleDebug` (CI also assembleRelease).
- **The Android emulator runs here** with software emulation (no KVM):
  `ANDROID_I_WANT_MY_TCG=yes emulator -avd <x86_64 image> -accel off -no-window`
  - an Android 11 image boots in ~5 minutes; each `adb shell input tap`
  takes about a minute; screencap with long timeouts. Enough to check the
  folder screen, the game list, a menu by touch.
- The front end's own checks (smoke test, touch steps) run in the PM repo.
- Bring your own game: never commit game files or screenshots of game
  graphics (store screenshots show the app's own screens only).
- **Store screenshots** (Tom, 2026-11-25; as ScummVM, RetroArch, Lemuroid and the rest - no commercial game's
  pictures in their listings): `fastlane/metadata/android/en-US/images/phoneScreenshots/` and
  `tenInchScreenshots/`, numbered in the listing's order (F-Droid reads them at the release's commit). Real
  captures of the app with stand-ins wherever a game would show: the game's area a plain "Game Displayed Here"
  panel, edge to edge (no frame or rounding - the game is maximised there); the Companion an invented map (an
  ordinary building: walls joined, a door in every room) and an invented party - our own names and our own
  24 x 24 EGA icons in the games' style (never SSI's, nor SSI's altered), statuses varied (one plain OK; no
  party-wide ones like Blessed; not "Stoned"); the chooser stand-in covers (the game's name in plain type).
  Drawn by the front end's own Companion code fed that data - a scratch tool, not in any repo.
