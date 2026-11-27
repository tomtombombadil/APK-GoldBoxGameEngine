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
