# TODO - Tom's and Claude's open items

Tick off with the version that did it. Never ask Tom the same thing twice.

## Tom
- [ ] 0.1.0 on your phone: install the APK, the folder screen, Play (All Files Access), a game copied in from
      the PC over USB, the scan, then play by touch alone; then with a Bluetooth controller (the on-screen
      buttons go away; a touch brings them back).
- [ ] Choose Folder… with a folder of your own (and an SD card, if the phone has one).
- [x] Release signing key: made 2026-11-23 (SHA-256 4D:B3:EE:83:...:41:00:B6); Tom has the .jks and the secrets
      file and added the four GOLDBOX_* secrets; Claude's copy deleted.
- [x] v0.1.0 published (Tom, 2026-11-23) and installed; his findings fixed in 0.1.1.
- [x] v0.1.1 published (Tom, 2026-11-23).
- [x] v0.1.3 published (Tom, 2026-11-23). v0.1.4 by the APK chat session (2026-11-24).
- [ ] Publish v0.4.0 on GitHub's site (Releases > Draft a new release > tag v0.4.0 on main > Publish) - it has
      everything of 0.1.5 - 0.3.0, so those needn't be published.
- [ ] 0.4.0: the chooser upright / sideways on the Pixel and as the grid on the Tab S2 (Steam box art and GOG
      logos), the scan's page, a Yes / No notice (Exit Game, Clean Up), Device Info, Quick Actions with a party,
      Controls - Button Size on the pad.
- [ ] **Continue (the last save) on the chooser** (accepted with the mockups): needs the engine to open a game at a
      save - a new `play::` call, not a bug fix, so Tom's OK for the CYD repo first (asked 2026-11-24).
- [ ] 0.3.0: the menu drawer (MENU handle, swipe in) - every page, a switch, a choice's < >, the volume (slider,
      - / +, mute), Back / a tap beside it / a swipe left to close; with a controller: Start, the D-pad, A / B.
- [ ] 0.2.0 on the Pixel and the Tab S2 (the redesign's first stage): sideways and upright - the pad (the corners
      light in a fight), the Companion's map / party tap, the three handles (tap and swipe in), both pins (pinned:
      out again after the app restarts; unpinned: swiped away, or a touch on the game puts it away), a controller
      (the pad goes; a touch brings it back; Y the Companion). Then the button layouts you want cleaned up.
      if it wasn't (0.1.3 has everything of 0.1.2).
- [ ] F-Droid: the submission (docs/FDROID.md) - needs your GitLab account.

## Claude
- [ ] **Redesign** (Tom, 2026-11-24; CLAUDE.md "The redesign"), in stages:
  1. (0.2.0 - seen on the emulator: the layouts, the pad's OK; the rest is Tom's to try) The game screen: the app's pad (8-way + OK, Back, pin), the Companion column / stack, edge handles,
     layouts by device and orientation, controller hides the pad (front end: `src/shell.*`).
  2. (0.3.0 - seen on the emulator: the top page, Audio, the volume) The menu drawer from the left, the app's own (the front end's menu through JNI); Audio sliders,
     - / +, mute; Display menu gone.
  3. (0.4.0 - seen on the emulator; Continue waits for the engine call) The game chooser, the app's own: phone cards (art by orientation), tablet 3 x 3 grid, Continue, shortcuts.
  4. Edge-to-edge, predictive Back, splash screen, adaptive icon, backup of saves, the folder screen restyled.
- [ ] No game played on Android yet (no game files in this session): the scan and a game on the emulator when
      Tom's files are here, GOG and Steam.
- [ ] Choose Folder… tried only in code review: the picker on the emulator, a folder made by hand.
- [ ] Store screenshots: the app's own screens only (never game graphics) - a game-play shot is not allowed in
      the repo.
