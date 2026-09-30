# DMGram handoff

Claude reads this instead of the codebase. Phases 0–5 are in. Do not start Phase 6 until this note is updated.

## 1. Status

| Phase | State | Why |
|---|---|---|
| 0 Scaffold and reality check | done | Tag `phase-0`. |
| 1 App frame | done | Two warm WebViews, header, tab bar, back, keyboard, theme, crash recovery. |
| 2 Navigation rules | done | Same rules in Kotlin and `router.js`. 81 fixtures. Reel lock is approach A. |
| 3 Content cleanup | done | Home ends at "You're all caught up". Instagram chrome and nags are hidden, not removed. |
| 4 Media and input | done | Photo picker, microphone permission, download listener, fullscreen hooks. |
| 5 Rules, updates, About | done | Sanitizer, local rules server, update banner. No GitHub repo is configured. |
| 6 Release | not started | Needs the user (keystore, repo). |

`./gradlew test` and `node tools/test-router.mjs` pass (81 fixtures).

## 2. Deviations

- Home is `https://www.instagram.com/`. Stories stay. Ads and suggestions are hidden with `data-dmgram-hidden`. Nodes are never removed. Network-level ad filtering was skipped.
- `WebView.canGoBack()` ignores same-document history. Back uses `__dmgram.back()` / `go()`.
- DM reel lock counts an on-screen, painted viewport video. Android back taps the top-most, then left-most, hit-testable icon, then Escape, then unlocks. The stacked "Suggested" viewer was not on screen.
- The photo button's file input is overlaid, not reparented. Accept extensions go to the Photo Picker. Script `click()` does not open it.
- Voice Clip's recorder trips the reel lock. Back still closes it. No HTML5 fullscreen control was found. A download was not triggered.
- `dmgram.githubRepo` is empty, so remote rules and the update check are skipped and the log says so. Debug overrides are Gradle `-P` properties (`dmgram.rulesUrl`, `dmgram.updatesUrl`), not entries in `gradle.properties`. A debug override fetches on every start so a restarted local server can be seen inside the 6 h / 24 h windows. Release builds ignore those URLs.
- Cleartext is allowed only in debug, and only for `localhost` and `127.0.0.1`.
- Remote rules are the original JSON string (markers stay). Kotlin parses `schema`, `version`, `minAppVersionCode`, `hide`, and `css` for the checks.
- `polish.css` and `polish.js` are empty. Do not write them.

## 3. Device checks (phase 5)

- Local `rules.json` version 2 hid the story plus badge. Changing it to version 3 and force-stopping applied the story-row hide with no reinstall.
- Banner: "Update available · v9.9.9". About: rules version 3, Install, GitHub, Report a problem. Install opened Firefox on the `.apk` URL.
- The default APK was reinstalled. Log: rules fetch skipped, update check skipped. About: rules version 1, "Up to date". Stories are visible.

## 4. Architecture map

- `tabs/TabController.kt` — WebViews, back, applies remote rules with `applyRules`, owns the update banner state.
- `rules/RulesRepository.kt` `RulesSanitizer.kt` — bundled asset, cache in `filesDir`, fetch when a URL exists.
- `update/UpdateChecker.kt` `Version.kt` `UpdateDecision.kt` — semver against `VERSION_NAME`. Install is `ACTION_VIEW` on the apk URL. No `REQUEST_INSTALL_PACKAGES`.
- `update/AboutLinks.kt` — report body is app, rules, Android, and WebView versions only.
- `web/DMGramWebViewClient.kt` — main-frame errors only. Offline or load failure shows `ErrorState` with Retry. The WebView is not sent to `about:blank`.
- `ui/AboutSheet.kt` `UpdateBanner.kt` — plain Material 3.
- `rules/rules.json` — bundled rules. `hide` and `css` ship fixes without an APK. `assets/inject/cleanup.js` writes them into `style#dmgram-remote`.

## 5. How to run

```
./gradlew installDebug
adb shell am start -n app.dmgram.debug/app.dmgram.MainActivity
adb logcat -s DMGram:V
./gradlew test
node tools/test-router.mjs
```

Local rules check (do not commit the `-P` values):

```
adb reverse tcp:8000 tcp:8000
./gradlew installDebug \
  -Pdmgram.rulesUrl=http://127.0.0.1:8000/rules.json \
  -Pdmgram.updatesUrl=http://127.0.0.1:8000/latest.json \
  -Pdmgram.githubRepo=example/dmgram
```

Reinstall without those properties afterward. Debug id is `app.dmgram.debug`.

## 6. Known issues

- Logged-out nags, the eight-video Suggested viewer, a feed reel, HTML5 fullscreen, and a real download were not exercised.
- Both WebViews share one renderer. An incoming VIEW without a component opens the official Instagram app.
- A main-frame load error was not provoked on the phone. The overlay is in place.
- Phase 6 needs a keystore and a GitHub repo before updates and rules can come from the network in a release build.

## 7. What the UX layer must know

- Do not write `polish.css` or `polish.js`.
- `data-dmgram-hidden` means `display: none`. Do not remove those nodes.
- Reel lock attributes: `data-dmgram-reel-lock`, `data-dmgram-reel-host`, `data-dmgram-reel-scroller`, `data-dmgram-reel-extra`.
- Native colors, type, shape, and spacing come from `ui/theme/`.
- The blocked notice string is "Not available in DMGram".
- The banner string is "Update available · vX.Y.Z".
