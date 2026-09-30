# DMGram handoff

Claude reads this instead of the codebase. Phase 0 is done. Do not start Phase 1 until the user has reviewed `docs/FINDINGS.md`.

## 1. Status

| Phase | State | Why |
|---|---|---|
| 0 Scaffold and reality check | done | Debug app installs, login survives force-stop, `cdp.mjs` works, Q1–Q22 are in `FINDINGS.md`. |
| 1 App frame | not started | |
| 2 Navigation rules | not started | |
| 3 Content cleanup | not started | |
| 4 Media and input | not started | |
| 5 Rules, updates, About | not started | |
| 6 Release | not started | |

## 2. Deviations

- `compileSdk` is API 37.2 (`minorApiLevel = 2`), `targetSdk` is 37. Compose BOM `2026.09.00` requires compile SDK 37+. Platform `android-37.2` was installed for that.
- AGP 9.4.1 built-in Kotlin, Kotlin 2.4.20 pinned via buildscript classpath. Gradle wrapper 9.8.0.
- Portrait, `configChanges`, and `adjustResize` are already on the activity so login and the keyboard do not recreate the WebView. Back, insets, and tabs are still Phase 1. Android back currently leaves the app; it does not `goBack()`.
- `setRequestedWithHeaderOriginAllowList` is a no-op in androidx.webkit 1.17.1. It is still called when the feature is reported.
- `tools/cdp.mjs` prefers port 9222. If that port is taken (desktop Chrome was), it uses the next free port through 9241.
- `polish.css` and `polish.js` are empty. Nothing is injected yet.
- Product vs site: `/?variant=following` has no stories bar and, in this sample, no ads. The real home `/` has stories plus ads and suggestions, and Instagram drops `variant=following` on its own home navigations. DM reels and `/reels/<code>/` swipes do not change the URL. Details in `FINDINGS.md`.

## 3. Architecture map

- `DMGramApp.kt` — enables WebView debugging in debug builds.
- `MainActivity.kt` — one full-screen WebView on `https://www.instagram.com/?variant=following`. Recreates it if the renderer dies. Shows "Update Android System WebView" when document-start script or web-message listener is missing.
- `web/WebViewFactory.kt` — WebView settings, cookies, Chrome user agent and client hints.
- `web/UserAgent.kt` — reduced Chrome UA from the device Chrome major version.
- `web/DMGramWebViewClient.kt` — `onRenderProcessGone` returns true; logs main-frame errors.
- `ui/theme/*` — Material 3 defaults. Colors, type, shape, and spacing live here.
- `tools/cdp.mjs` — DevTools CLI (`targets`, `eval`, `css`).
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

`cdp.mjs` sets up `adb forward` itself. Debug application id is `app.dmgram.debug`. Xiaomi/HyperOS shows an "Install via USB" prompt; Deny is the countdown button.

## 5. Known issues

- One WebView only. A relaunch once left a second DevTools target alive in the same process (see Q22). Phase 1 should own WebView lifetime explicitly.
- File chooser and microphone are not implemented, so DM photo/voice send was not exercised.
- Facebook login and the stock WebView user agent were not A/B tested.

## 6. What the UX layer must know

- Nothing on Instagram is hidden yet. `polish.css` and `polish.js` are empty and are not injected.
- Native UI is default Material 3, including the WebView-update screen.
- WebView background comes from `ui/theme/Color.kt` (white / black).
- Instagram's own bottom bar (66px, five links) and home header (44px) are still visible. So is a "Use the app" strip on most pages except the Following feed.
- Stories live on `/`, not on `/?variant=following`.
- A reel opened from a DM is a full-screen Suggested viewer that never changes the thread URL. Swipes advance.
- Stable hooks are `href` and English `svg[aria-label]` (`Home`, `Explore`, `Reels`, `Messages`, `Notifications`, `Back`, `Close`, `Similar accounts`). Do not style obfuscated classes. Theme token on `<html>` is `__fb-light-mode`.
