# DMGram handoff

Claude reads this instead of the codebase. Phases 0–3 are in. Do not start Phase 4 until this note is updated.

## 1. Status

| Phase | State | Why |
|---|---|---|
| 0 Scaffold and reality check | done | Tag `phase-0`. |
| 1 App frame | done | Two warm WebViews, header, tab bar, back, keyboard, theme, crash recovery. |
| 2 Navigation rules | done | Same rules in Kotlin and `router.js`. 81 fixtures. Reel lock is approach A. |
| 3 Content cleanup | done | Home ends at "You're all caught up". Instagram chrome and nags are hidden, not removed. |
| 4 Media and input | not started | No file chooser, microphone, or download listener yet. |
| 5 Rules, updates, About | not started | About is name + version only. Remote rules are not fetched. |
| 6 Release | not started | Needs the user (keystore, repo). |

`./gradlew test` and `node tools/test-router.mjs` pass (81 fixtures).

## 2. Deviations

- Home is `https://www.instagram.com/`. Stories stay. Ads, suggestions, and everything after "You're all caught up" are hidden with `data-dmgram-hidden`. Nodes are never removed.
- Network-level ad filtering was skipped. After the cutoff, 60s at the bottom produced zero `/graphql/query` requests. DOM hiding was enough.
- A Follow button in an article header (exact label, not "Follow back" / "Following") matched suggested posts and never matched followed posts in this sample. Text markers are the backup. No "Follow back" post appeared.
- `WebView.canGoBack()` / `goBack()` / `goBackOrForward()` ignore same-document history. Back uses `__dmgram.back()` / `go()`.
- Header search clicks `/explore/`, then pushStates to `/explore/search/`. `/explore/` and `/explore/search/` are allowed. Other `/explore/*` paths are blocked.
- Username is in SharedPreferences (`dmgram` / `username`).
- `onTrimMemory` keeps the visible tab.
- DevTools reports both WebViews `visible: true`. Use `cdp.mjs eval --all` and branch on `__dmgram.tab`.
- `Page.crash` kills the shared renderer. The process stays up and both tabs reload.
- DM reel lock counts an on-screen, non-transparent viewport video. One video arms the lock. Two or more plus a scroller hide the extras. `touch-action: none` is on the scroller and the host.
- Android back in the viewer taps the top-most, then left-most, hit-testable icon in the viewer (English `aria-label` is only a tiebreaker). If that point is missing, it sends Escape, then unlocks and continues normal back. Back is never a no-op.
- The stacked "Suggested" viewer (about eight videos) was not on screen this session. The user confirmed swipe does nothing and back returns to the chat. This pass saw one viewport video, one hidden extra, no "Suggested" header.
- `polish.css` and `polish.js` are empty. Do not write them.
- `dmgram.closeReelViewer` was removed. A script `click()` does not dismiss the viewer.

## 3. navigate() steps

| Path | Step |
|---|---|
| `/` | click |
| `/explore/` | click |
| `/explore/search/` | pushState after the explore click |
| `/notifications/` | click when the heart link is in the DOM; pushState still renders it |
| `/<username>/` | click |
| `/direct/inbox/` | native tab switch, not an in-page navigation |

## 4. Device checks (phase 3)

- Home shows the stories bar, a followed post, no Instagram header, no Instagram tab bar, no "Use the app". The DMs tab badge showed 3.
- Scrolling Home reaches "You're all caught up". Suggested posts and ads stay in the DOM with `display: none`. Visible text has no "Suggested for you" or "Ad".
- Search is the field plus recents. No explore grid.
- Profile opens `/example_user/`. On `/friend_a/` the similar-accounts control is `display: none`.
- System dark mode (`cmd uimode night yes`) switched `<html>` to `__fb-dark-mode` and `rgb(12, 16, 20)` without a reload and without touching Instagram's appearance switch. `night no` restored `__fb-light-mode`. The phone was left on light.
- Cleanup observer callbacks stayed under 2ms. The rAF scan averaged about 2.2ms and peaked at 7ms. That is not a >50ms long task.
- The user confirmed a live DM appears without refresh, a sent reel does not scroll, and back returns to the chat.

## 5. Architecture map

- `MainActivity.kt` — splash, edge-to-edge, insets, owns `TabController`.
- `tabs/TabController.kt` — WebView lifetime, back, reel dismiss, bridge.
- `nav/Route.kt` `NavPolicy.kt` `ChromePolicy.kt` — classify, allow/block, chrome.
- `web/InjectBundle.kt` — document-start script. Config is a JSON object.
- `web/DMGramChromeClient.kt` — fullscreen video, progress, title. No file chooser yet.
- `assets/inject/bridge.js` — `__dmgram`. `ready` depends on the route class.
- `assets/inject/router.js` — history patch, click policy, reel lock. `reelBackPoint`, `reelEscape`, `forceUnlock`.
- `assets/inject/hide.css` — chrome, nags attribute, explore grid, reel lock. `cleanup.js` marks the bottom nav `data-dmgram-chrome="nav"` because a `:has()` rule would also match ancestors.
- `assets/inject/cleanup.js` — one MutationObserver, added nodes only, `data-dmgram-checked` / `data-dmgram-hidden`.
- `rules/rules.json` — routes plus `markers` (en, es, pt, fr, de, id, hi where the string is reliable). `hide` and `css` are empty until Phase 5.
- `assets/inject/rules.css` — still a placeholder.

## 6. How to run

```
./gradlew installDebug
adb shell am start -n app.dmgram.debug/app.dmgram.MainActivity
adb logcat -s DMGram:V
node tools/cdp.mjs eval --all 'window.__dmgram.tab+" "+location.pathname'
node tools/test-router.mjs
./gradlew test
```

Debug id is `app.dmgram.debug`.

## 7. Known issues

- Logged-out "Open app" / "Open Instagram" were not reopened this pass. The nag markers include those strings.
- The eight-video Suggested reel viewer was not opened. Do not treat the single-video lock as proof of that layout.
- Both WebViews share one renderer.
- An incoming VIEW without a component opens the official Instagram app. With the component, the post opens in Home.
- A feed reel (not a profile-grid reel and not a DM reel) was not opened.

## 8. What the UX layer must know

- Do not write `polish.css` or `polish.js`.
- `data-dmgram-hidden` means `display: none`. Do not remove those nodes.
- Reel lock: `data-dmgram-reel-lock`, `data-dmgram-reel-host`, `data-dmgram-reel-scroller`, `data-dmgram-reel-extra`.
- Native colors, type, shape, and spacing come from `ui/theme/`.
- The blocked notice string is "Not available in DMGram".
