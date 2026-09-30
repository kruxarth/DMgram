# DMGram

Instagram for staying in touch: your friends' posts and stories, your DMs, and nothing the algorithm picked.

DMGram is not affiliated with Instagram or Meta.

## Install

Download `DMGram-vX.Y.Z.apk` from the [GitHub releases](https://github.com/kruxarth/DMgram/releases). Android will ask you to allow installs from your browser. The first install is a normal APK install. Later installs update the same app (`app.dmgram`) only if they are signed with the same key.

Android 8.0 or newer. Portrait only.

## Updates

On launch, at most once a day, DMGram checks this repo's latest GitHub release. If it is newer, Home shows "Update available · vX.Y.Z". Install opens the APK in the browser. DMGram does not install it itself.

Selector fixes ship in [`rules/rules.json`](rules/rules.json). The app fetches that file, at most every 6 hours, while it is in the foreground. Rules are selectors, URL patterns, text markers, and sanitized CSS. They never contain JavaScript.

## What it leaves out

DMGram shows Instagram's own site and hides the algorithmic surfaces:

- Reels tab, Explore grid, hashtags, keyword and location results
- Ads, suggested posts, suggested reels, suggested accounts, and everything after "You're all caught up"
- Notifications, calls, and a settings page

A reel you actually opened plays one at a time. Tapping a blocked link does nothing except the notice "Not available in DMGram".

## Privacy

Nothing leaves the phone except to Instagram, and to GitHub for the update check and the rules file. There is no DMGram server, no analytics, and no crash reporting. The app does no work while it is closed.

## Release key

Updates only install over an older DMGram if they are signed with the same key. Losing `~/keys/dmgram-release.jks` means users can never update. Keep a backup of the keystore and its passwords somewhere that is not this repo.

`keystore.properties` stays on your machine (it is gitignored). Create the key once, if you do not already have it:

```
keytool -genkeypair -v -keystore /absolute/path/to/dmgram-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias your-alias
```

This project already has a keystore at `~/keys/dmgram-release.jks`. Create `keystore.properties` next to `settings.gradle.kts`:

```
storeFile=/absolute/path/to/dmgram-release.jks
storePassword=the-store-password
keyAlias=the-key-alias
keyPassword=the-key-password
```

`storeFile` must be an absolute path. Gradle does not expand `~`. On this machine that is `/home/krutarth/keys/dmgram-release.jks`. `./gradlew assembleRelease` then produces a signed, minified APK. With no `keystore.properties` file, the release APK is unsigned.

The GitHub Actions release workflow reads these repository secrets (Settings → Secrets and variables → Actions). It does not store them in the repo:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 ~/keys/dmgram-release.jks` |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | the key alias |
| `KEY_PASSWORD` | the key password |

Pushing a tag `vX.Y.Z` runs the tests, builds the signed APK, and attaches `DMGram-vX.Y.Z.apk` plus its SHA-256 to the GitHub release.
