# Releasing, and getting into F-Droid

Everything here is done in a web browser or PowerShell on Windows.

## What is ready in this repo

- **The build recipe** F-Droid needs: `fdroid/io.github.tomtombombadil.goldbox.yml`.
  Checked here with F-Droid's own tools (fdroidserver 2.4.5, 2026-11-23):
  `fdroid readmeta`, `lint`, `rewritemeta` (no changes), and **`fdroid build`
  built 0.1.0 from the tag** - its source scanner passed once SDL's Xcode
  folder (a Mac `.DS_Store` file, a "binary" to the scanner) is deleted before
  the build (`scandelete`). The gradle wrapper jars are removed by F-Droid
  itself, as always.
- **The store page**: `fastlane/metadata/android/en-US/` - title, short and
  full description, icon, screenshots (the app's own screens only - never
  game graphics), and a changelog per version (`changelogs/<versionCode>.txt`).
- **Nothing non-free**: no Google Play Services, Firebase, analytics or ads;
  MIT (the app, front end, engine), zlib (SDL); no prebuilt binaries.
- **Versions F-Droid can follow**: `version.properties` (versionName,
  versionCode) and a tag `v<versionName>` per release. F-Droid checks the tags
  for new versions by itself (`UpdateCheckMode: Tags`) and builds each.

## 1. The release key (once, before the first release)

Your own releases (the GitHub Releases page) are signed with your key, and
every later version must be signed with the **same** key or Android refuses
to install it over the old one. Make it once and keep it safe (a password
manager, and a copy off the PC).

In PowerShell (keytool comes with Android Studio; if you don't have Android
Studio, any Java JDK has it):

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore "$HOME\Documents\goldbox-release.jks" -alias goldbox `
  -keyalg RSA -keysize 4096 -validity 36500
```

It asks for a password (use the same one for the key when it asks again) and
your name - "tomtombombadil" is fine for every question.

Then give it to GitHub as four **repository secrets**: the repo on GitHub ›
**Settings** › **Secrets and variables** › **Actions** › **New repository
secret**:

| Name | Value |
|--|--|
| `GOLDBOX_KEYSTORE_B64` | the file as text - in PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\Documents\goldbox-release.jks")) \| Set-Clipboard`, then paste |
| `GOLDBOX_KEYSTORE_PASSWORD` | the password |
| `GOLDBOX_KEY_ALIAS` | `goldbox` |
| `GOLDBOX_KEY_PASSWORD` | the password |

## 2. A release

A release is the tag `v<version>` (Claude does this when you say "release
it"): CI builds `GoldBoxGameEngine-<version>.apk`, signs it with the key
above, and publishes it on the Releases page with that version's changelog.
Without the secrets, the release run stops and says so.

## 3. Submitting to F-Droid (once)

F-Droid adds apps through its metadata repository, `fdroiddata`, on GitLab.
You submit one file - the recipe above. All in the browser:

1. **A GitLab account**: https://gitlab.com/users/sign_up (free).
2. **Fork fdroiddata**: open https://gitlab.com/fdroid/fdroiddata and press
   **Fork** (top right) › Fork project (your namespace, defaults).
3. **Add the file**: in your fork, switch to a new branch: the branch menu ›
   **New branch** named `io.github.tomtombombadil.goldbox`. Then open the
   folder `metadata`, **+** › **New file**, name it
   `io.github.tomtombombadil.goldbox.yml`, and paste in the whole content of
   this repo's `fdroid/io.github.tomtombombadil.goldbox.yml`. Commit message:
   `New App: io.github.tomtombombadil.goldbox`. Commit to that branch.
4. **Merge request**: GitLab offers **Create merge request** after the
   commit. Target: `fdroid/fdroiddata`, branch `master`. Title:
   `New App: Gold Box Game Engine`. The description is a checklist template -
   tick what applies: you are the author; the app is open source (MIT); no
   tracking or non-free libraries; it builds (`fdroid build` passed here).
   Under "notes" say, plainly:
   *"A game engine for SSI's Gold Box games. It ships no game data - the
   player copies in their own GOG / Steam files. It asks for All Files Access
   (MANAGE_EXTERNAL_STORAGE) so the player can keep the games in a folder of
   their own that a PC can reach over USB; it reads and writes only in that
   folder, and offers the app's private folder instead (no permission)."*
5. **The pipeline**: GitLab builds the recipe itself (the merge request's
   Pipelines tab). If it fails, the log says why - send it to Claude.
6. **Reviewers**: an F-Droid volunteer reads it and may ask questions or
   change things in the file - answer in the merge request. Typical: they may
   add the **Non-Free Dependencies** anti-feature (the app needs the games,
   which are not free) - that is their call and only a label on the store page.
7. **Published**: after the merge, the app appears in F-Droid within a few
   days. From then on F-Droid builds each new tag by itself.

The release key (step 1) must exist and the tag `v0.1.0` must be on GitHub
before step 3 - the recipe builds that tag.

## Good to know

- **F-Droid signs its own builds with its own key.** An APK from F-Droid and
  one from the Releases page can't update each other: a player switching
  uninstalls first (saved games in Internal storage › GoldBox stay). Later,
  F-Droid can ship your own signature instead ("reproducible builds") if the
  two builds come out byte-identical - something to try after it is in.
- F-Droid needs nothing from you for each new version: a new tag is enough.
- Players can also install straight from GitHub with **Obtainium**, which
  follows the Releases page.
