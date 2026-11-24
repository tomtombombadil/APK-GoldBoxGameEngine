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
- **Swipe-outs** - left: the menu drawer; right: the Companion; bottom: the controls (8-way pad - diagonals -
  with OK in its centre, Back; no menu button: the menu is the left swipe). The right and bottom ones have a
  **pin**: pinned they stay out (and are remembered); unpinned they can be swiped back (and close when the game
  is touched). Each opens from a **handle** on its edge (system Back / Home gestures own the edges:
  `setSystemGestureExclusionRects`, ~200dp an edge). Opening one makes the game smaller to make room.
- **Default layouts by aspect ratio and orientation** - phone (16:9 or wider) landscape: the game at the left,
  beside it a column: **the Companion's map at the top - a tap flips it to the party and back - and the
  controls under it** (Tom, 2026-11-24, taking the trade-off: the controls in place of the Companion's party
  rather than a tray that shrinks the game). Phone portrait: the game, the Companion under it, the controls at
  the bottom. Tablet (near 4:3) landscape: the game maximised, everything swiped out on demand. Tablet
  portrait: as the phone's.
- **Audio**: each volume a slider, - and + buttons and a mute button. The Display menu goes away in favour of
  the app's own controls.
- **Game chooser**: art shaped for the device (upright box art on a phone held upright, wide art sideways); a
  tablet shows all nine games at once.
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
- Release signing: the four `GOLDBOX_*` repository secrets (docs/FDROID.md
  step 1). The same key forever - a new key means players must uninstall.

## The app
- `SetupActivity` - the folder screen (its own process, `:setup`); the
  first start, a folder gone or unreadable, or the game's Games Folder line.
  Folder kinds: default (Internal storage › GoldBox), chosen (any path; the
  picker's tree URI turned into a path - All Files Access reads it), private
  (the app's own). A chosen folder with no `games` folder but game folders in
  it is the games folder itself.
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
