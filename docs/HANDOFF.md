# DMGram handoff

Claude reads this instead of the codebase. Phases 0–4 are in. Do not start Phase 5 until this note is updated.

## 1. Status

| Phase | State | Why |
|---|---|---|
| 0 Scaffold and reality check | done | Tag `phase-0`. |
| 1 App frame | done | Two warm WebViews, header, tab bar, back, keyboard, theme, crash recovery. |
| 2 Navigation rules | done | Same rules in Kotlin and `router.js`. 81 fixtures. Reel lock is approach A. |
| 3 Content cleanup | done | Home ends at "You're all caught up". Instagram chrome and nags are hidden, not removed. |
| 4 Media and input | done | Photo picker, microphone permission, download listener, fullscreen hooks. |
| 5 Rules, updates, About | not started | About is name + version only. Remote rules are not fetched. |
| 6 Release | not started | Needs the user (keystore, repo). |

`./gradlew test` and `node tools/test-router.mjs` pass (81 fixtures).

## 2. Deviations

- Home is `https://www.instagram.com/`. Stories stay. Ads, suggestions, and everything after "You're all caught up" are hidden with `data-dmgram-hidden`. Nodes are never removed.
- Network-level ad filtering was skipped. After the cutoff, 60s at the bottom produced zero `/graphql/query` requests.
- A Follow button in an article header (exact "Follow") matched suggestions in this sample. No "Follow back" post appeared.
- `WebView.canGoBack()` ignores same-document history. Back uses `__dmgram.back()` / `go()`.
- Header search clicks `/explore/`, then pushStates to `/explore/search/`. Other `/explore/*` paths are blocked.
- Username is in SharedPreferences (`dmgram` / `username`).
- DM reel lock counts an on-screen, painted viewport video. One video arms the lock. Two or more plus a scroller hide the extras. `touch-action: none` is on the scroller and the host.
- Android back in the viewer taps the top-most, then left-most, hit-testable icon. If that point is missing, it sends Escape, then unlocks and continues. Back is never a no-op.
- The stacked "Suggested" viewer was not on screen. The user confirmed swipe does nothing and back returns to the chat.
- The photo button's file input stays in the DOM. Script `click()` does not open the picker. A tap on the overlaid input does. Accept extensions such as `.png` go to the Photo Picker, not `GetContent`.
- Voice Clip's recorder is a viewport video, so the reel lock arms on top of it. Back still closes it. No voice message was sent.
- No fullscreen control was found, so `onShowCustomView` was not entered. A download was not triggered.
- `polish.css` and `polish.js` are empty. Do not write them.
- `dmgram.closeReelViewer` was removed.

## 3. navigate() steps

| Path | Step |
|---|---|
| `/` | click |
| `/explore/` | click |
| `/explore/search/` | pushState after the explore click |
| `/notifications/` | click when the heart link is in the DOM; pushState still renders it |
| `/<username>/` | click |
| `/direct/inbox/` | native tab switch |

## 4. Device checks (phase 4)

- Cancel the picker, then the next photo tap opens it again.
- One red test photo, then three test photos (blue, green, red), were sent in the test thread. No personal photos were selected.
- Voice Clip showed the microphone dialog. Granting it opened a recorder. Back closed it without sending.
- "Your story" opened the photo picker. Nothing was posted.
- A playing Home video was paused after switching to DMs.
- The user asked for a trial text: `trial` was sent to a friend (`friend_d`), not to a friend.

## 5. Architecture map

- `MainActivity.kt` — splash, edge-to-edge, insets. System bars hide while `frame.fullScreen` (reel lock or video custom view).
- `tabs/TabController.kt` — WebView lifetime, back, reel dismiss, bridge. Download listener opens a Custom Tab.
- `web/MediaRequests.kt` — Photo Picker or `GetContent`. Only `RESOURCE_AUDIO_CAPTURE` is granted, after `RECORD_AUDIO`.
- `web/DMGramChromeClient.kt` — file chooser, permission, fullscreen custom view.
- `assets/inject/cleanup.js` — hiding, plus `placeFileInput()` over "Add Photo or Video".
- `assets/inject/router.js` — history patch, click policy, reel lock.
- `rules/rules.json` — routes plus markers. `hide` and `css` are empty until Phase 5.
- `ui/AboutSheet.kt` — name and version only.

## 6. How to run

```
./gradlew installDebug
adb shell am start -n app.dmgram.debug/app.dmgram.MainActivity
adb logcat -s DMGram:V
node tools/cdp.mjs eval --all 'window.__dmgram.tab+" "+location.pathname'
node tools/test-router.mjs
./gradlew test
```

Debug id is `app.dmgram.debug`. `RECORD_AUDIO` is the only extra permission.

## 7. Known issues

- Logged-out nags were not reopened. The eight-video Suggested viewer was not opened. A feed reel was not opened.
- Both WebViews share one renderer. DevTools reports both `visible: true`.
- An incoming VIEW without a component opens the official Instagram app.
- Voice Clip can trip the reel lock. Back leaves it. HTML5 fullscreen and a real download were not exercised.

## 8. What the UX layer must know

- Do not write `polish.css` or `polish.js`.
- `data-dmgram-hidden` means `display: none`. Do not remove those nodes.
- Reel lock: `data-dmgram-reel-lock`, `data-dmgram-reel-host`, `data-dmgram-reel-scroller`, `data-dmgram-reel-extra`.
- Native colors, type, shape, and spacing come from `ui/theme/`.
- The blocked notice string is "Not available in DMGram".
