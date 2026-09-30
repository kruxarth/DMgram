# DMGram handoff

Claude reads this instead of the codebase. Phase 0 is **not** finished: the app builds, but `docs/FINDINGS.md` does not exist yet.

## 1. Status

| Phase | State | Why |
|---|---|---|
| 0 Scaffold and reality check | partial | Debug app and `tools/cdp.mjs` build. No phone was attached, so install, login, and Q1–Q22 were not done. |
| 1 App frame | not started | |
| 2 Navigation rules | not started | |
| 3 Content cleanup | not started | |
| 4 Media and input | not started | |
| 5 Rules, updates, About | not started | |
| 6 Release | not started | |

## 2. Deviations

- `compileSdk` is API 37.2 (`minorApiLevel = 2`), `targetSdk` is 37. Compose BOM `2026.09.00` requires compile SDK 37 or newer. Only platform 36 was installed, so platform `android-37.2` was installed. It is the latest stable platform.
- AGP 9.4.1 uses built-in Kotlin. Kotlin 2.4.20 is pinned with a buildscript classpath. The `org.jetbrains.kotlin.android` plugin is not applied.
- Gradle wrapper is 9.8.0 (AGP 9.4 requires 9.6+).
- Portrait lock, `configChanges`, and `adjustResize` are already on the activity so the keyboard during login does not recreate the WebView. Back handling, insets, and tabs are still Phase 1.
- `WebSettingsCompat.setRequestedWithHeaderOriginAllowList` is a no-op in androidx.webkit 1.17.1 (the origin trial ended). It is still called when that feature is reported as supported.
- `assets/inject/polish.css` and `polish.js` are empty placeholders. No other inject scripts yet.

## 3. Architecture map

- `DMGramApp.kt` — enables WebView debugging in debug builds.
- `MainActivity.kt` — one full-screen WebView on `https://www.instagram.com/?variant=following`. Recreates it if the renderer dies. Shows "Update Android System WebView" when document-start script or web-message listener is missing.
- `web/WebViewFactory.kt` — WebView settings, cookies, Chrome user agent and client hints.
- `web/UserAgent.kt` — reduced Chrome UA from the device's Chrome major version.
- `web/DMGramWebViewClient.kt` — `onRenderProcessGone` returns true; logs main-frame errors.
- `ui/theme/*` — Material 3 defaults. Colors, type, shape, and spacing live here.
- `tools/cdp.mjs` — DevTools CLI (`targets`, `eval`, `css`). Forwards to `app.dmgram.debug`.
- `app/src/test/.../UserAgentTest.kt` — UA parsing and the reduced Chrome string.

## 4. How to run

```
./gradlew installDebug
adb shell am start -n app.dmgram.debug/app.dmgram.MainActivity
node tools/cdp.mjs targets
node tools/cdp.mjs eval 'location.href'
node tools/cdp.mjs css some.css --id live
./gradlew test
```

`cdp.mjs` sets up `adb forward tcp:9222` itself. It errors if there is no device, the debug app is not running, or there are no WebView targets.

## 5. Known issues

- Install and DevTools were not exercised on a device.
- Q1–Q22 are unanswered. Do not start Phase 1 until `FINDINGS.md` exists and has been reviewed.

## 6. What the UX layer must know

- Nothing on Instagram is hidden yet. `polish.css` and `polish.js` are empty and injected by nothing.
- Native UI is default Material 3, including the WebView-update screen.
- WebView background comes from `ui/theme/Color.kt` (white / black) so the first frame is not a white flash in dark mode.
