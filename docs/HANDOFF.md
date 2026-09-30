# DMGram handoff

Claude reads this instead of the codebase. Phases 1 and 2 are in. Stop here for review. Do not start Phase 3.

## 1. Status

| Phase | State | Why |
|---|---|---|
| 0 Scaffold and reality check | done | Tag `phase-0`. |
| 1 App frame | done | Two warm WebViews, header, tab bar, back, keyboard, theme, crash recovery. Device checks below. |
| 2 Navigation rules | done | Same rules in Kotlin and `router.js`. 81 fixtures. Reel lock is approach A. |
| 3 Content cleanup | not started | Instagram's own header, bottom nav, and "Use the app" are still visible. |
| 4 Media and input | not started | |
| 5 Rules, updates, About | not started | About is name + version only. |
| 6 Release | not started | Needs the user (keystore, repo). |

`./gradlew test` and `node tools/test-router.mjs` pass (81 fixtures).

## 2. Deviations

- Home is `https://www.instagram.com/`. Stories are there. Ads and suggestions are still visible; filtering is Phase 3.
- `WebView.canGoBack()`, `goBack()`, and `goBackOrForward()` ignore same-document history on this WebView (Chrome 153) even when `copyBackForwardList()` and the Navigation API show earlier entries. Back and tab reselect call `history.back()` / `history.go()` through `__dmgram`.
- Header search clicks `/explore/`, then `pushState` + `popstate` to `/explore/search/`. Focusing the search field from script does not move the route. A real tap does. `pushState` + `popstate` does render the search page.
- The Phase 2 acceptance line that says `pushState('/explore/')` is blocked is stale. Changes item 5 allows `/explore/` and `/explore/search/`. Keyword search and every other `/explore/*` path are blocked.
- Username is stored in SharedPreferences (`dmgram` / `username`) now, because the Profile root needs it. The plan assigned prefs to Phase 3.
- `onTrimMemory` keeps the visible tab. Destroying it would blank the screen.
- DevTools reports both WebViews `visible: true` even when one is `INVISIBLE`. `cdp.mjs eval` without `--all` can hit the inbox. Use `--all` and branch on `__dmgram.tab`.
- `Page.crash` on one WebView kills the shared renderer, so both tabs reload. The app process stays up.
- A DM reel on this account opened as one viewport-tall video (two others were 0×0) with no "Suggested" header. Swipes did not advance to another account. The lock still arms for that single video and blocks `touchmove` / `wheel`. The bubble image had no reel `href`. Approach B was not needed.
- `polish.css` and `polish.js` are empty. Do not write them.

## 3. navigate() steps

Confirmed on the logged-in account:

| Path | Step |
|---|---|
| `/` | click |
| `/explore/` | click |
| `/explore/search/` | pushState after the explore click |
| `/notifications/` | click when the heart link is in the DOM; pushState still renders it when the link is missing |
| `/<username>/` | click |
| `/direct/inbox/` | not in-page. `navigate()` posts a native switch and does not change the current tab's URL |

## 4. Device checks

- Cold start shows `/` with the stories bar. DMs opens the already-loaded inbox. Home scroll survived the switch. A home video was paused after the switch.
- Reselect: off the root, Home returns to `/`. At the root and scrolled, Home scrolls to the top. At the root and at the top, Home reloads.
- Back: search → explore → home → launcher (`moveTaskToBack`). From a thread opened with Message on a profile, back returns to that profile. The DMs WebView stays on the inbox.
- Keyboard: in a thread the message box sits on the keyboard with no gap, gesture nav and 3-button. Navigation mode was put back to gesture (`2`). System night mode was put back to auto.
- System dark mode did not reload either page.
- `cdp.mjs css` with `body{outline:4px solid red}` applied in both tabs immediately.
- Two minutes in the background with DevTools open: no HTTP requests. Two `/ajax/bz` posts in an earlier sample were from the second before Home.
- A live message from another device was not watched. Ask the user.
- `location.href='/reels/'` stayed on `/` and logged a block. `pushState` to `/explore/search/keyword/?q=%23test` rewound home and showed "Not available in DMGram". `/explore/` and `/explore/search/` stayed.
- A caption hashtag (`/explore/tags/2025/`) did not navigate; the notice showed.
- Message on `/friend_c/` opened `/direct/t/…` in the Home tab.
- A profile-grid reel opened as `/<user>/reel/<code>/` (`REEL_SINGLE`, one video).
- Incoming `VIEW` for a post: see FINDINGS if retested after this note.

## 5. Architecture map

- `MainActivity.kt` — splash, edge-to-edge, insets, owns `TabController`.
- `tabs/Tab.kt` — HOME `/`, DMS `/direct/inbox/`, PROFILE `/<username>/`.
- `tabs/TabController.kt` — WebView lifetime, back, reselect, bridge, Custom Tabs.
- `tabs/FrameUi.kt` — snapshot the UI reads.
- `nav/Route.kt` — pure classifier. Patterns from `rules/rules.json`.
- `nav/NavPolicy.kt` — allow, block, switch, external, system.
- `nav/ChromePolicy.kt` — tab bar, header, fullscreen, pull-to-refresh.
- `web/Bridge.kt` — JSON messages, `https://www.instagram.com` only.
- `web/InjectBundle.kt` — document-start script. Config is a JSON object, not a string.
- `web/DMGramWebViewClient.kt` — renderer death and main-frame overrides.
- `web/DMGramChromeClient.kt` — fullscreen video, progress, title.
- `web/LoginState.kt` — `ds_user_id` cookie.
- `web/JsLiteral.kt` — quotes strings for `evaluateJavascript`.
- `ui/TabBar.kt` `HomeHeader.kt` `BlockedNotice.kt` `LoadingOverlay.kt` `ErrorState.kt` `AboutSheet.kt` `UpdateBanner.kt` — plain Material 3 hooks.
- `assets/inject/bridge.js` — `__dmgram` API.
- `assets/inject/router.js` — history patch, click policy, reel lock.
- `assets/inject/hide.css` — hides Reels and Explore entry points, the explore grid on `/explore/`, and extra reel items.
- `assets/inject/cleanup.js` `rules.css` — placeholders. Phase 3 and 5.
- `rules/rules.json` — copied to `assets/rules.default.json` at build time.
- `tools/route-fixtures.json` `tools/test-router.mjs` `RouteTest.kt` — the same 81 cases.

## 6. How to run

```
./gradlew installDebug
adb shell am start -n app.dmgram.debug/app.dmgram.MainActivity
adb logcat -s DMGram:V
node tools/cdp.mjs targets
node tools/cdp.mjs eval --all 'window.__dmgram.tab+" "+location.pathname'
node tools/test-router.mjs
./gradlew test
```

Debug id is `app.dmgram.debug`. HyperOS can show "Install via USB"; Deny is the countdown.

## 7. Known issues

- Instagram's header, bottom nav, and "Use the app" stay on screen until Phase 3. `hide.css` only hides the Reels and Explore nav links and the explore grid.
- Both WebViews share one renderer. Crashing one reloads both. Home reloads at `/`.
- The reel viewer dismiss control is `svg[aria-label="Close"]`. A script `click()` does not dismiss it. Android back sends a real tap on that control. The thread's own control is `svg[aria-label="Back"]`.
- A feed reel (not a profile-grid reel) was not opened on device this pass.
- An incoming VIEW without a component opens the official Instagram app. With the component set, the post opens in Home. A Threads bio link opened a Firefox Custom Tab.
- No live inbound DM was watched. Ask the user to send one from another device.

## 8. What the UX layer must know

- Do not write `polish.css` or `polish.js`. Hooks: `__dmgram.tab`, `__dmgram.route`, `__dmgram.on`, `scrollToTop`, `pauseMedia`, `setConfig`, `navigate`, `openSearch`.
- `data-dmgram-route` and `data-dmgram-path` are on `<html>`. Reel lock sets `data-dmgram-reel-lock` and `data-dmgram-reel-extra`.
- Native colors, type, shape, and spacing come from `ui/theme/`.
- The blocked notice string is "Not available in DMGram".
- Stable Instagram hooks are still `href` and English `svg[aria-label]`. Do not style obfuscated classes.
