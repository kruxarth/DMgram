# DMGram — Build plan (for Grok in Cursor)

Read `docs/PRODUCT.md` first. It is the spec; this file is the how.

## Changes after Phase 0 (read this first)

These come from `FINDINGS.md`. The sections below are already updated; this list says what moved.

1. **Home is `/`, not `/?variant=following`.** The following feed has no stories bar, and the user wants stories on top. So Home is Instagram's normal home, with ads and suggestions filtered and the feed cut off at "You're all caught up". Filtering is now core Phase 3 work, not a safety net. There is no REWRITE rule anymore.
2. **Native chrome navigates by clicking Instagram's own hidden links** (`__dmgram.navigate(path)`, §1.8). Header search and activity, tab reselect and inbox switching become in-page navigations instead of full page loads. Instagram's bottom nav and header are hidden with CSS but **must stay in the DOM**.
3. **Logged-in state comes from the `ds_user_id` cookie**, not the route. When logged out, `/` is a marketing page (§1.7).
4. **DM threads open in the current tab**, like the app. Only `/direct/inbox/` switches to the DMs tab (§2.2).
5. **Search:** `/explore/` (grid hidden) and `/explore/search/` are allowed. Every other `/explore/*` path is blocked, including `/explore/search/keyword/`, where hashtag taps actually land (§2.1).
6. **The reel lock is a DOM lock on the DM "Suggested" viewer**, which never changes the URL. `/<user>/reel/<code>/` is already a single post page and needs no lock. `/reels/*` is always blocked (§2.5).
7. **Unread count:** the primary source is the digits `span` inside the hidden `a[href="/direct/inbox/"]` in the HOME WebView (language-independent). The inbox title is the fallback (§3.4).
8. The PROFILE route class gains `reposts`. ACTIVITY is `/notifications/`.
9. Once the file chooser exists, re-test "Your story" → story posting (Phase 4 acceptance).
10. **Stop points changed.** Run Phases 1 and 2 back to back, then **stop after `phase-2`** for Claude's review. Then run Phases 3–5 back to back and **stop after `phase-5`**. Phase 6 needs the user (keystore, repo).

## Your role and boundaries

- You build **all functionality**: the Android app frame, WebView setup, navigation rules, content filtering, media handling, rules/update system, release pipeline, dev tooling.
- A second engineer (Claude) does the **visual/UX layer after you**. Therefore:
  - **Do not polish visuals.** Native UI you build uses plain Material 3 defaults and placeholder icons, and reads every color, size, shape and font from `ui/theme/` tokens. Functional over pretty.
  - **Do not write to** `assets/inject/polish.css` or `assets/inject/polish.js`. Create them as empty placeholders.
  - Keep the names in **"Hooks for the UX layer"** exactly as specified.
- Work **one phase at a time**. After each phase: run its acceptance checks, commit, `git tag phase-N`, update `docs/HANDOFF.md`. **Stop and report** only at the stop points in "Changes after Phase 0", or when you're blocked.
- You can run most device checks yourself: `adb shell input …`, `adb exec-out screencap -p`, and `tools/cdp.mjs`. Ask the user only for things that need a human (logging in, sending a message from another device).
- If this plan turns out wrong against the real site, don't hack around it silently. Record it in `FINDINGS.md`/`HANDOFF.md` and pick the simplest option that satisfies `PRODUCT.md`.
- **Never add:** notifications, background services, WorkManager, analytics, Kotlin-side requests to instagram.com, permissions not listed here, or new DMGram screens.

## Environment

- Linux, JDK 21, Android SDK at `/opt/android-sdk` (`ANDROID_HOME` is set), `adb` on PATH, Node 26 (has global `WebSocket`), ImageMagick (`magick`).
- No global Gradle: generate and use the Gradle wrapper.
- Testing happens on the user's **real Android phone** over adb (USB or wireless debugging), logged into their real Instagram account. The user does the login; you never handle their password.

## Stack

- Kotlin, Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`), latest stable AGP and Kotlin.
- compileSdk / targetSdk: latest stable platform installed (≥ 35). minSdk 26.
- Jetpack Compose (BOM) + Material 3 + `activity-compose` + `core-splashscreen`.
- `androidx.webkit` (WebViewCompat / WebSettingsCompat / WebViewFeature).
- `androidx.browser` (Custom Tabs) for external links.
- `kotlinx.serialization` (JSON), `kotlinx.coroutines`.
- OkHttp for the only two network calls (rules fetch, update check).
- **Not allowed:** Hilt/Koin, Room, Navigation-Compose (tabs are state, not routes), any JS library in injected scripts.
- Tests: JUnit for pure-Kotlin logic; a Node script for the JS route matcher.

## Repository layout

```
DMgram/
  docs/            PRODUCT.md PLAN-GROK.md PLAN-CLAUDE.md FINDINGS.md HANDOFF.md
  rules/rules.json                 # remote rules, served raw from GitHub
  tools/cdp.mjs                    # DevTools CLI for live WebViews (Phase 0)
  tools/route-fixtures.json        # shared (tab, url) → expected class/action table
  tools/test-router.mjs            # runs router.js matcher against fixtures
  app/src/main/
    AndroidManifest.xml
    assets/rules.default.json      # generated at build time from rules/rules.json
    assets/inject/
      bridge.js      router.js      cleanup.js      hide.css      # yours
      polish.css     polish.js                                      # Claude's (empty placeholders)
    java/app/dmgram/
      MainActivity.kt   DMGramApp.kt
      tabs/     Tab.kt  TabController.kt
      web/      WebViewFactory.kt  InjectBundle.kt  Bridge.kt  UserAgent.kt
                DMGramWebViewClient.kt  DMGramChromeClient.kt
      nav/      Route.kt  NavPolicy.kt  ChromePolicy.kt
      rules/    Rules.kt  RulesRepository.kt  RulesSanitizer.kt
      update/   UpdateChecker.kt  Version.kt
      ui/theme/ Color.kt  Type.kt  Shape.kt  Dimens.kt  Theme.kt
      ui/       TabBar.kt  HomeHeader.kt  UpdateBanner.kt  AboutSheet.kt
                BlockedNotice.kt  LoadingOverlay.kt  ErrorState.kt
  app/src/test/...                 # unit tests
  .github/workflows/release.yml
  .github/ISSUE_TEMPLATE/bug.md
```

---

## Phase 0: Scaffold and reality check

**Goal:** a bare app you can log into, a DevTools CLI, and written answers about how Instagram's mobile site actually behaves. Every later phase depends on these answers.

### Steps

1. `git init`. `.gitignore` for Android plus `local.properties`, `keystore.properties`, `*.jks`, `*.keystore`.
2. Create the Gradle project per **Stack**. Package `app.dmgram`, app name `DMGram`. Debug build type uses `applicationIdSuffix ".debug"`.
3. `MainActivity`: one full-screen WebView loading `https://www.instagram.com/?variant=following`, configured per **WebView configuration** below. Debug builds: `WebView.setWebContentsDebuggingEnabled(true)`.
4. Build `tools/cdp.mjs`, a small Chrome DevTools Protocol CLI for the app's live WebViews. No npm dependencies (Node 26 has `WebSocket` and `fetch`).
   - It sets up port forwarding itself: find the pid with `adb shell pidof app.dmgram.debug`, then `adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>`.
   - Targets come from `http://localhost:9222/json`. Each WebView target's `description` JSON includes `visible`.
   - Commands:
     - `node tools/cdp.mjs targets`: list targets (id, url, visible).
     - `node tools/cdp.mjs eval '<js>' [--all]`: run `Runtime.evaluate` (returnByValue, awaitPromise) in the visible target, or all targets. Print the result as compact JSON.
     - `node tools/cdp.mjs css <file> [--id polish]`: put the file's contents into `<style id="dmgram-<id>">` in every target, creating it if missing. This is live styling without a rebuild.
   - Clear error messages when no device, app not running, or no targets.
5. `./gradlew installDebug`. Ask the user to connect their phone, open the app and log in.
6. Investigate the live site with `tools/cdp.mjs` (and `adb exec-out screencap -p` screenshots when needed). Answer every question below in `docs/FINDINGS.md`.
   - For every element we will hide or read, record a **stable selector**: prefer `href`, `aria-label`, `role`, SVG `aria-label`/`title`, or structure. **Never** obfuscated class names (like `x1lliihq`). Note your confidence.
   - Note whether each selector depends on the UI language.

### Questions (copy into FINDINGS.md and answer each)

- **Q1 Following feed:** Does `/?variant=following` load reliably? Is the stories bar shown on it? Scroll about 60 posts: any sponsored, suggested or "Suggested for you" units? If yes, how are they marked in the DOM, and in network JSON?
- **Q2 Navigation:** Does Instagram navigate in-page with `history.pushState`? Does `WebView.canGoBack()` reflect it? Does Android back work naturally?
- **Q3 Home URL:** When Instagram navigates "home" (logo, after login, after posting), what URL does it use? Does it drop `?variant=following`?
- **Q4 Username:** Where can the logged-in username be read reliably (for example the profile link in Instagram's nav bar)?
- **Q5 Unread DMs:** Does `document.title` contain `(N)`? If not, where is the unread badge in the DOM?
- **Q6 Reels from DMs:** Tap a reel shared in a DM. What route? Page or modal? Can you swipe to other reels? How does the URL change when you do?
- **Q7 Reels elsewhere:** Same questions for a reel posted by a friend (in the feed) and a reel opened from a profile's Reels grid.
- **Q8 Search:** Which route(s)? Is the Explore grid visible on the search screen? What must be hidden so only the search field and results/recent searches remain?
- **Q9 Activity:** Route? Is there a suggested-accounts section?
- **Q10 Profiles:** Selectors for "Suggested for you" / similar-accounts UI. Routes for profile tabs (posts, reels, tagged).
- **Q11 Hashtags and locations:** Routes when tapped from a caption or post.
- **Q12 Create:** How is "create post/story" opened on mobile web: route or modal? What triggers it? Is story posting supported at all?
- **Q13 Nags:** Every "Open in app", "Get the app", "Use the app" or "Turn on notifications" banner and interstitial: selector, and whether it differs between the default WebView user agent and ours.
- **Q14 WebView detection:** Compare the default WebView UA with our UA (see **User agent**). Any blocked features or login problems? Try "Log in with Facebook" once and record what happens.
- **Q15 DM features:** Which of these work on mobile web? Send one photo from gallery, send several, voice note (record and play), react, reply, reply to a story, share a post to a DM, GIFs/stickers, vanish mode, unsend.
- **Q16 Dark mode:** Does Instagram web follow `prefers-color-scheme`? Does it have its own appearance toggle? How can JS read the current theme (for example `body` background color, or a class on `html`)?
- **Q17 Video:** Feed autoplay, sound toggle, fullscreen behavior.
- **Q18 Instagram's chrome:** Selectors for Instagram's own bottom nav bar and top header on the home feed, and for the page padding they leave behind.
- **Q19 Feed transport:** fetch or XHR, and which endpoint loads feed pages. Only needed if Q1 found ads or suggestions.
- **Q20 Auth routes:** login, signup, challenge, two-factor, "save login info" (onetap).
- **Q21 External links:** Format of outbound links (for example `l.instagram.com/?u=...`).
- **Q22 Multiple tabs:** With two WebViews open (one on the feed, one on `/direct/inbox/`), does Instagram complain ("open in another window" or similar)? Do new messages still arrive live in the inbox WebView?

### Acceptance

- The app builds and installs; the user logs in and stays logged in after force-stopping and reopening the app.
- `tools/cdp.mjs targets | eval | css` all work against the phone.
- `FINDINGS.md` answers Q1–Q22 with selectors, confidence and language dependence.
- Commit, tag `phase-0`. **Stop.** The user will review `FINDINGS.md` before Phase 1.

---

## WebView configuration (every WebView)

**Settings**
- `javaScriptEnabled = true`, `domStorageEnabled = true`.
- `mediaPlaybackRequiresUserGesture = false` (feed videos autoplay muted, like the app).
- `setSupportZoom(false)`, `builtInZoomControls = false`.
- `allowFileAccess = false`, `allowContentAccess = false`, `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`.
- Leave `textZoom` at its default so the system font-size setting still works.
- Safe Browsing: default (on).
- `WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)` if `ALGORITHMIC_DARKENING` is supported. Instagram has its own dark mode.
- If `REQUESTED_WITH_HEADER_ALLOW_LIST` is supported: set it to an empty set, so requests don't carry our package name.
- If `USER_AGENT_METADATA` is supported: set brands so client hints look like Chrome, not "Android WebView". Verify the effect in Q14.
- `setBackgroundColor(...)` from the theme **before** the first load. Never show a white frame in dark mode.
- `WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)`.

**Cookies:** `CookieManager` accepts cookies, and third-party cookies are enabled per WebView (needed for accountscenter). Call `CookieManager.flush()` in `onStop`.

**User agent (`UserAgent.kt`):** build the exact string real Chrome for Android sends (reduced UA), using the device's real Chrome major version parsed from `WebSettings.getDefaultUserAgent(context)`:
```
Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/<major>.0.0.0 Mobile Safari/537.36
```
Don't hard-code a version, and don't invent anything else.

**Renderer crashes:** `onRenderProcessGone` must return `true`, then destroy and recreate that tab's WebView and reload its root. Returning `false` kills the app.

**Required WebView features:** `DOCUMENT_START_SCRIPT` and `WEB_MESSAGE_LISTENER`. If either is unsupported, show the error state ("Update Android System WebView", with a Play Store link) instead of the tabs.

---

## Phase 1: App frame

**Goal:** three persistent tabs that feel instant, correct back/keyboard/insets behavior, login gating, the JS↔native bridge and the inject pipeline.

### 1.1 Activity

- Single activity. `enableEdgeToEdge()`. `android:screenOrientation="portrait"`.
- `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden|navigation|uiMode"` so WebViews are **never** destroyed by a config change. On `uiMode` change: update the Compose theme and tell pages (bridge).
- `android:windowSoftInputMode="adjustResize"`.
- App theme: Material3 DayNight, NoActionBar (so WebView's `prefers-color-scheme` follows the system; confirm with Q16).
- `core-splashscreen`: keep the splash until Home reports `ready` or 3 s pass, whichever is first.

### 1.2 Tabs and WebView ownership

- `enum class Tab { HOME, DMS, PROFILE }` with roots:
  - HOME: `https://www.instagram.com/`
  - DMS: `https://www.instagram.com/direct/inbox/`
  - PROFILE: `https://www.instagram.com/<username>/` (username from Q4). Until it's known, show the loading state.
- `TabController` is a plain class owned by the Activity. Not a ViewModel, because WebViews hold the Activity context. It holds the current tab, one WebView per tab (created lazily), and per-tab state: url, route class, canGoBack, progress, `ready`, `atTop`.
- **All WebViews are children of one `FrameLayout`** inside a single `AndroidView`. Create WebViews in `TabController`, **never** in an `AndroidView` factory lambda that recomposition can re-run.
- Switching tabs:
  - hidden tabs are set `INVISIBLE`, the target tab `VISIBLE`
  - call `__dmgram.pauseMedia()` on the tab being hidden
  - no reloads, ever
- Preloading:
  - create HOME at start
  - once HOME has loaded and the user is logged in, create DMS in the background so the first DMs tap is instant
  - create PROFILE on its first tap
- Backgrounding:
  - `onStop`: `onPause()` every WebView, then `WebView.pauseTimers()` once
  - `onStart`: resume them
  - DMGram must do nothing while in the background
- `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL or BACKGROUND+)`: destroy the PROFILE WebView (and DMS if not current), then recreate lazily.

### 1.3 Back handling (OnBackPressedCallback, `enableOnBackInvokedCallback="true"`)

In order:
1. Fullscreen video showing → exit it.
2. A DMGram sheet is open → close it.
3. The current tab's WebView `canGoBack()` → `goBack()`.
4. The current tab ≠ HOME → switch to HOME.
5. Otherwise → `moveTaskToBack(true)`. Don't finish; keep WebViews warm.

### 1.4 Tab reselect (tapping the active tab)

- Not at the tab root: in `copyBackForwardList()`, find the nearest earlier entry whose URL is the tab root and `goBackOrForward(-steps)`. If there is none, `__dmgram.navigate(rootPath)`.
- At the root and not at top: `__dmgram.scrollToTop()` (smooth).
- At the root and at top: reload (like the Instagram app refreshing the feed).

### 1.5 Pull-to-refresh

Native (`SwipeRefreshLayout` or Compose `PullToRefreshBox`) around each WebView. Enabled only when `ChromePolicy` allows it for the current route **and** the page reports `atTop == true`. It must never trigger inside the DM thread or story viewer. Refresh = `reload()`.

### 1.6 Insets and keyboard

- Top: the web container gets status-bar padding, except on full-screen routes (story, reel), which go edge-to-edge.
- Bottom: when the tab bar is visible it handles the navigation-bar inset. When it is hidden, the web container gets the navigation-bar inset.
- Keyboard: the web container gets `ime` padding (union with nav bar). The tab bar hides while the keyboard is open.
- Status-bar icon color follows the page theme (bridge `theme` event).
- **Must pass:** in a DM thread the message box sits directly on top of the keyboard, with no gap and no overlap, on both gesture and 3-button navigation.

### 1.7 Login gating

- **Logged in = the `ds_user_id` cookie is present** (`CookieManager.getCookie("https://www.instagram.com")`, checked on every `route` event and on resume). Don't infer it from the route: when logged out, `/` itself is a marketing page with "Open app" / "Log in" buttons.
- Logged out: hide the tab bar and Home header, and show Instagram's pages full-screen in the HOME WebView. Hide the "Open app" / "Open Instagram" nags on those pages too (Phase 3).
- Don't create DMS or PROFILE until the user is logged in.
- On the transition to logged in (post-login URL may be `/?deoia=1`): stay on the HOME root, detect the username, preload DMS.
- On the transition to logged out: destroy DMS and PROFILE, clear the username, switch to HOME.

### 1.8 Bridge (`Bridge.kt` + `bridge.js`)

- `WebViewCompat.addWebMessageListener(webView, "DMGramNative", setOf("https://www.instagram.com"), listener)`.
- JS → native (JSON strings, each with `type`):

  | type | payload | when |
  |---|---|---|
  | `route` | `{url}` | initial load, every pushState/replaceState/popstate |
  | `navigate` | `{url}` | router.js wants native to handle a link (switch tab / external) |
  | `blocked` | `{url, reason}` | a blocked link was stopped |
  | `unread` | `{count}` | the DM unread count changed |
  | `username` | `{value}` | detected logged-in username |
  | `theme` | `{dark, background}` | on load and whenever Instagram's theme changes |
  | `scroll` | `{atTop}` | only when the value changes |
  | `ready` | `{}` | first meaningful paint of the current route (feed has a post or empty state, inbox list rendered, …) |
  | `log` | `{level, msg}` | debug builds only |

- Native → JS: `evaluateJavascript("window.__dmgram && window.__dmgram.<fn>(…)")` for `scrollToTop()`, `pauseMedia()`, `setConfig(json)`, `navigate(path)`.
- **`navigate(path)`** moves Instagram to another route without a full page load. Try in order, stopping at the first that works (confirm the URL changed via the `route` event):
  1. `.click()` an existing `a[href="<path>"]` in the DOM. Instagram's hidden nav and header links count, because `display:none` elements still dispatch clicks to React.
  2. `history.pushState({}, '', path)` + `dispatchEvent(new PopStateEvent('popstate'))`. Only use this if testing shows Instagram's router renders the new route. Otherwise drop this step.
  3. `location.assign(path)` (full load, last resort).

  Record in `HANDOFF.md` which step works for `/`, `/explore/`, `/notifications/`, `/direct/inbox/` and `/<username>/`.
- **Plain `TabBar` and `HomeHeader`** (see Hooks) are built in this phase:
  - Header search → `navigate('/explore/')`, then focus `input[type="search"]` so Instagram moves to `/explore/search/`.
  - Header activity → `navigate('/notifications/')`.
- **All incoming messages are untrusted.** Parse with kotlinx.serialization, ignore unknown types, cap string lengths, and send every URL through `NavPolicy` before acting on it.

### 1.9 Inject bundle (`InjectBundle.kt`)

- Build **one** script per WebView:
  ```
  (() => { if (window.__dmgram) return;
    const CONFIG = { tab, rules, debug };    // JSON from native
    <bridge.js> <router.js> <cleanup.js>
    <styles: hide.css, rules.css, polish.css as <style id="dmgram-hide|rules|polish"> appended to document.documentElement>
    <polish.js>
  })();
  ```
- Register with `WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("https://www.instagram.com"))`. Keep the `ScriptHandler`. When rules update: `remove()`, re-add, then `setConfig` on the current page.
- Keep the style elements alive across Instagram's re-renders: re-append them if they are ever removed (a cheap MutationObserver on `document.documentElement` child list).
- Wrap every injected file in `try/catch`. A bug in our JS must never break Instagram's page. Errors go to bridge `log` in debug builds.
- Injected JS must be small and fast: no libraries, no polling loops, events and observers only.
- `window.__dmgram` exposes: `tab`, `route` (current class), `on(event, fn)` for `route` / `theme` / `scroll`, and the native-callable functions above. `polish.js` builds on this.

### Phase 1 acceptance

- Cold start → Home (`/`) with the stories bar. Tap DMs → the inbox is already there (no loading). Back to Home → same scroll position.
- With HOME and DMS both alive, ask the user to send the account a message from another device: it appears live in the DMs tab, and Instagram shows no "open in another window" warning (Q22).
- Header search and activity open without a full page load. Record which `navigate()` step worked.
- A video playing in Home stops when switching to DMs.
- Back and reselect behave exactly per 1.3 and 1.4.
- Keyboard and insets per 1.6.
- Background the app for 2 minutes with DevTools open: no new HTTP requests (an already-open websocket idling is acceptable).
- Toggling system dark mode doesn't reload any page.
- `node tools/cdp.mjs css <file>` with `body{outline:4px solid red}` shows instantly in all tabs.
- Kill the renderer with `adb shell am crash`, or via DevTools with `chrome://crash` in one tab: the app survives and that tab reloads.

---

## Phase 2: Navigation rules

### 2.1 Route classes (`Route.kt`: pure Kotlin, no Android imports)

Initial table (adjust using FINDINGS):

| Class | Paths |
|---|---|
| `HOME_FEED` | `/` (any query: `?variant=following`, `?deoia=1`, …) |
| `DIRECT_INBOX` | `/direct/inbox/`, `/direct/requests/`, `/direct/new/` |
| `DIRECT_THREAD` | `/direct/t/<id>/` |
| `STORY` | `/stories/<user>/…`, `/stories/highlights/<id>/` |
| `POST` | `/p/<code>/`, `/<user>/p/<code>/` |
| `REEL_SINGLE` | `/reel/<code>/`, `/<user>/reel/<code>/` (a single post page; needs no lock) |
| `REELS_FEED` | `/reels/`, `/reels/<code>/` (the algorithmic feed; Q7) |
| `SEARCH` | exactly `/explore/` (grid hidden by CSS) and exactly `/explore/search/` |
| `EXPLORE_BLOCKED` | every other `/explore/*`: `/explore/tags/*`, `/explore/locations/*`, `/explore/search/keyword/*` (where hashtag taps land; Q11), and anything unknown |
| `ACTIVITY` | `/notifications/` |
| `PROFILE` | `/<username>/` and `/<username>/(reels|reposts|tagged|followers|following|saved)/` |
| `ACCOUNT` | `/accounts/*` (non-auth, e.g. `settings`, `edit`, `switch_appearance`), `accountscenter.instagram.com` |
| `AUTH` | per Q20 |
| `EXTERNAL` | any other host; unwrap `l.instagram.com/?u=` first |
| `UNKNOWN` | any other instagram.com path → allow; log in debug |

Usernames: `[A-Za-z0-9._]{1,30}`. Exclude reserved first segments (`p`, `reel`, `reels`, `explore`, `stories`, `direct`, `accounts`, `notifications`, `about`, `legal`, `developer`, `challenge`, …). Note that `create` is **not** reserved; `/create/` is a real account (Q12).

### 2.2 Policy (`NavPolicy.kt`): (currentTab, url) → action

| Condition | Action |
|---|---|
| `EXPLORE_BLOCKED`, `REELS_FEED` | `BLOCK(reason)` |
| `HOME_FEED` in HOME | `ALLOW` (any query) |
| `HOME_FEED` in DMS/PROFILE | `SWITCH(HOME)`, showing Home as it was (no reload) |
| `DIRECT_INBOX` in HOME/PROFILE | `SWITCH(DMS)`, showing the inbox as it was (no reload); the original tab stays where it was |
| `DIRECT_THREAD` in any tab | `ALLOW` in the current tab (like the app: back returns to where you were) |
| `EXTERNAL` | open a Custom Tab (link shim unwrapped) |
| `mailto:`, `tel:` | system `ACTION_VIEW` in try/catch |
| `intent:` and Instagram app deep links | ignore silently |
| everything else | `ALLOW` in the current tab |

### 2.3 Enforcement layers (all four)

1. **`router.js` click capture:** `click`/`auxclick` listeners in the capture phase on `document`. Take the closest `a[href]`, classify it with `CONFIG.tab`, then:
   - `BLOCK`: `preventDefault()` + `stopImmediatePropagation()`, post `blocked`
   - `SWITCH` / `EXTERNAL`: prevent, post `navigate`
   - Hashtag anchors carry `href="/explore/tags/…"` but Instagram navigates to `/explore/search/keyword/?q=%23…`. Layer 1 blocks the href; layer 2 catches the real URL if the click came from somewhere else.
   - `ALLOW`: do nothing
2. **History patch:** wrap `pushState`/`replaceState`, listen to `popstate`, and post `route` on every change. If native sees a route whose action is `BLOCK` (a programmatic navigation that got past layer 1): `goBack()` if possible, else load the tab root, and show the blocked notice.
3. **`shouldOverrideUrlLoading`** for full navigations: same policy.
4. **`hide.css`** hides entry points (Reels tab, Explore links), so users rarely meet a block at all.

### 2.4 One rule table, two matchers

- Route patterns live in `rules.json` → `routes` (ordered list of `{class, pattern, hosts?}`). The policy mapping stays in code, mirrored in Kotlin and JS. It's small.
- `tools/route-fixtures.json` holds at least 50 cases of `{tab, url, expectedClass, expectedAction}`, covering every row above plus edge cases: trailing slashes, query strings, `m.instagram.com`, reserved words, link shims, uppercase usernames.
- `RouteTest.kt` (JUnit) and `tools/test-router.mjs` (Node, loads `router.js` matcher) must both pass on the same fixtures.

### 2.5 Reel lock (implements the Reels rule)

Required behavior:
- A reel opened from a DM thread, the feed or a profile grid shows **that reel**, playing, with sound control.
- Swiping, scrolling or auto-advance **never** reaches another reel.
- Back returns to where the user came from.

What FINDINGS showed (Q6/Q7):
- Feed and profile-grid reels open `/<user>/reel/<code>/`, which is already a single post page. No lock is needed there.
- `/reels/*` is the algorithmic feed and is blocked by policy.
- **The only real work is the DM viewer.** Tapping a shared reel in `/direct/t/<id>/` opens a full-screen overlay (header "Suggested", about 8 stacked viewport-tall `video`s). Swiping advances, and **the URL never changes**, so this must be a DOM lock in `router.js` (or a small `reel-lock` part of it).

Implementation:
- **Detect the viewer structurally, not by the English "Suggested" text:**
  - on a `DIRECT_THREAD` route
  - a new full-viewport, fixed-position container appears with **≥ 2 `video` descendants**
  - its scrollable ancestor (`scrollHeight > 1.5 × clientHeight`) is the reel scroller
  - use a MutationObserver scoped to that route; no polling
- **Lock it:**
  - set `data-dmgram-reel-lock` on `<html>` and mark the scroller
  - `hide.css` gives the marked scroller `overflow: hidden !important; overscroll-behavior: none` and hides every reel item except the first (the one that was sent; **verify it is the first**)
  - also stop `touchmove`/`wheel` on the scroller in the capture phase, so scroll-snap can't advance
  - taps (play/pause, mute, Back) must keep working
- **Unlock** when the viewer closes: the container is removed or `svg[aria-label="Back"]` is used. Android back must close the viewer and return to the thread, not leave the thread.
- If a structural lock turns out unreliable, fall back to **B**: a 4th WebView created on demand over the current tab, loading `/reel/<code>/` (a single post page), with a plain native close button, destroyed on close. B needs the reel's code from the DM bubble. Record in FINDINGS whether the bubble exposes it (an `href`, or a code in an attribute or image URL).

Document which approach shipped, and why, in `HANDOFF.md`.

### 2.6 Chrome policy (`ChromePolicy.kt`)

Route class → `{ showTabBar, showHomeHeader, fullScreen, pullToRefresh }`. Initial values (Claude will tune):

| Route | Tab bar | Home header | Full-screen | Pull-to-refresh |
|---|---|---|---|---|
| `HOME_FEED` (HOME tab root) | ✓ | ✓ | | ✓ |
| `DIRECT_INBOX` | ✓ | | | ✓ |
| `DIRECT_THREAD` | | | | |
| `STORY`, `REEL_SINGLE` | | | ✓ | |
| `AUTH` | | | | |
| own profile (PROFILE root) | ✓ | | | ✓ |
| everything else | ✓ | | | |

Keyboard open → tab bar hidden, always.

### 2.7 Incoming links

Add an `ACTION_VIEW` intent filter (no `autoVerify`) for `https://www.instagram.com/*` and `https://instagram.com/*`. An incoming URL goes through `NavPolicy`: `DIRECT_*` opens in DMS, blocked routes show the notice on Home, and anything else opens in HOME.

### Phase 2 acceptance

- `./gradlew test` and `node tools/test-router.mjs` pass.
- DM → shared reel: the reel that was sent plays, swiping up shows no other reel, and Android back and the viewer's Back both return to the thread.
- A friend's reel in the feed and a reel from a profile grid open as a single post page.
- Hashtag in a caption: nothing navigates; the blocked notice shows.
- `node tools/cdp.mjs eval "location.href='/reels/'"` and `"history.pushState({},'','/explore/tags/test/')"` both end back where they were, with the notice.
- On a profile in the Home tab, tap "Message": the thread opens in the Home tab, and back returns to the profile.
- `node tools/cdp.mjs eval "history.pushState({},'','/explore/search/keyword/?q=%23test')"` is blocked. Header search still reaches `/explore/search/` with no grid visible.
- Bio link → Custom Tab. `adb shell am start -a android.intent.action.VIEW -d "https://www.instagram.com/p/<code>/"` opens the post in DMGram (after enabling the link in app settings).

---

## Phase 3: Content cleanup

1. **`hide.css`** (functional hiding only, stable selectors from FINDINGS; comment each rule with what it hides and the Q number):
   - Instagram's bottom nav and home top header, including the padding they leave behind. **Hide with CSS only; never remove them from the DOM.** `navigate()` clicks their links, and the unread badge is read from the nav.
   - app nags, banners and interstitials, including the fixed "Use the app" bar (Q13) and the logged-out "Open app" / "Open Instagram" buttons
   - Reels and Explore entry points
   - suggested-account carousels and "Suggested for you" in the feed, profiles, activity and search
   - the Explore grid on the search screen
   - the similar-accounts button on profiles
2. **`cleanup.js`**: Home is `/`, which **does** contain ads and suggestions (Q1/Q19), so this is core work.
   - **What to hide on `/`:**
     - ads: short `span` "Ad" / "Sponsored" in a post header
     - "Suggested for you" posts and account carousels
     - suggested reels units
     - **everything after the "You're all caught up" marker**, including "Suggested posts". The feed ends there.
   - **Markers:** text markers come from `rules.json` → `markers`, per language. Ship English plus the most common languages you can fill in reliably. Match only short label elements or `aria-label`, never a whole post's text.
   - **Test this language-independent signal:** a post from an account you don't follow has a **Follow button in its header**. If that holds (and never matches followed accounts' posts, including ones you follow that are shown with a "Follow back" state), use it as the primary ad/suggestion detector and text markers as backup. Record the result in FINDINGS.
   - **Pagination check (must do):** after the caught-up cutoff, stay at the bottom of the feed for 60 s and count `/graphql/query` XHRs. If Instagram keeps loading page after page of posts we hide, stop it (for example hide or neutralize the pagination sentinel after the cutoff) and document how.
   - Mechanics:
     - one `MutationObserver`; process added nodes only, batched per `requestAnimationFrame`
     - mark checked nodes `data-dmgram-checked`; hide matches with the `data-dmgram-hidden` attribute (`[data-dmgram-hidden]{display:none!important}`)
     - **never remove nodes** (it breaks React)
     - budget: under 2 ms per observer callback on average; log the timing in debug
3. **Network-level ad filtering** (patching the XHR response of `PolarisFeedRootPaginationCachedQuery…`): only if the DOM approach proves unreliable. Otherwise skip it and say so in `HANDOFF.md`. Never hard-code the doc id.
4. **Unread count:**
   - Primary: the digits `span` inside the hidden `a[href="/direct/inbox/"]` in the **HOME** WebView (language-independent; Q5), watched with a MutationObserver.
   - Fallback: in the DMS WebView, `WebChromeClient.onReceivedTitle` parsing `^\((\d+)\)` (the title only has the count on the inbox).
   - Expose it as Compose state for `TabBar`.
5. **Username** per Q4, stored in SharedPreferences, used for the PROFILE root.
6. **Theme** per Q16: `bridge.js` reports `{dark, background}` on load and on change. Native uses it for status-bar icons and the WebView background.
   - Test with the phone in system dark mode: does Instagram follow it without its own switch?
   - What's the dark class on `<html>` (expected `__fb-dark-mode`)?
   - What happens when Instagram's own appearance switch disagrees with the system?
   - Record the answers in FINDINGS. Base `dark` on what Instagram actually renders, not on the system setting.
7. **`ready`** per route type, used for the splash and `LoadingOverlay`.

### Phase 3 acceptance

- Scroll Home to the end: stories bar visible; no ads, suggested posts, suggested accounts or suggested reels; the feed ends at "You're all caught up"; no background fetch loop after the end (pagination check).
- None of Instagram's nav bars and no "open app" / "Use the app" nags in the main flows, logged in or out.
- Search shows only the field and results/recents.
- The DMs badge matches Instagram's count and updates within seconds of a new message while the app is open.
- The Profile tab opens your own profile.
- DevTools Performance while scrolling the feed: no long tasks (>50 ms) attributable to `cleanup.js`.

---

## Phase 4: Media and input

1. **File chooser:** `onShowFileChooser` → Android Photo Picker (`PickVisualMedia` / `PickMultipleVisualMedia` depending on `fileChooserParams.mode` and accept types), falling back to `GetContent` for non-media. **Call the callback exactly once**, with `null` on cancel. No storage permission.
2. **Microphone:** only if Q15 says voice notes work. `onPermissionRequest` with `RESOURCE_AUDIO_CAPTURE` → request `RECORD_AUDIO` at runtime → grant or deny. Add the manifest permission only in that case. **Deny every other permission request** (camera, geolocation, MIDI, protected media, notifications).
3. **Fullscreen video:** implement `onShowCustomView`/`onHideCustomView`: full-screen over everything, system bars hidden, back exits.
4. **Downloads:** `setDownloadListener` → open in a Custom Tab.

### Phase 4 acceptance

- Send one photo, then three, in a DM.
- Cancel the picker, and the next attempt still works.
- Voice note, if applicable. Q15 saw a "Voice Clip" control, so try it.
- Tap "Your story" (the plus badge on your avatar in the stories bar). Phase 0 saw nothing happen, probably because there was no file chooser yet. Record in FINDINGS whether a picker opens and a story can be posted. Build nothing extra either way.
- Fullscreen video enter/exit.
- No audio ever plays from a hidden tab.

---

## Phase 5: Rules, updates, About

### 5.1 `rules/rules.json` schema

```json
{
  "schema": 1,
  "version": 1,
  "minAppVersionCode": 1,
  "routes":  [ { "class": "EXPLORE", "pattern": "^/explore/?$" } ],
  "hide":    [ { "id": "ig-bottom-nav", "selector": "…", "note": "Q18" } ],
  "markers": { "sponsored": { "en": ["Sponsored"] }, "suggested": { "en": ["Suggested for you"] } },
  "css": "/* extra CSS, sanitized */"
}
```
`hide.css` holds the bundled rules; `rules.hide` + `rules.css` let fixes ship without an APK.

### 5.2 `RulesRepository`

- Bundled: a Gradle task copies `rules/rules.json` → `assets/rules.default.json` at build time, so the two never drift.
- Remote URL: `https://raw.githubusercontent.com/<repo>/main/rules/rules.json`, with `<repo>` from the Gradle property `dmgram.githubRepo` via BuildConfig. **If the property is empty, skip remote fetching.**
- Fetch on app start if the last fetch was more than 6 h ago. Coroutine, foreground only.
- Use the remote file only if it parses, `schema` is known, `version` > bundled version, `minAppVersionCode` ≤ app versionCode, **and** it passes `RulesSanitizer`. Cache it in `filesDir`.
- New rules apply by re-registering the inject script and calling `setConfig` on current pages.
- Debug builds: allow overriding the rules URL via a Gradle property, for example `http://localhost:8000/rules.json` with `adb reverse tcp:8000 tcp:8000` and a debug-only network security config permitting cleartext to localhost.

### 5.3 `RulesSanitizer`: reject the whole file if

- `css` contains any backslash, `url(`, `@import`, `image-set(`, `@font-face`, `expression(`, `-moz-binding` or `behavior:` (case-insensitive). These block data exfiltration via CSS.
- Any selector is longer than 500 characters or contains `{` or `}`.
- Any route pattern is not a valid regex or is longer than 300 characters.
- The file is larger than 256 KB.
- **Rules can never contain JavaScript.** There is no field for it; never add one. If the repo were compromised, remote JS would expose every user's Instagram session.

### 5.4 `UpdateChecker`

- `GET https://api.github.com/repos/<repo>/releases/latest` with `Accept: application/vnd.github+json`. On app start, at most every 24 h, foreground only. Skip if no repo is configured.
- Compare `tag_name` (strip a leading `v`) with `BuildConfig.VERSION_NAME` using semver (`Version.kt`, unit-tested).
- If newer and not dismissed for that version, expose `UpdateInfo(version, apkUrl, releaseUrl)`. `apkUrl` is the first asset ending in `.apk`.
- Install = `ACTION_VIEW` on `apkUrl` (the browser downloads it). **No** `REQUEST_INSTALL_PACKAGES`.

### 5.5 Plain UI (Claude restyles later; functional only)

- `UpdateBanner`: top of Home. "Update available · vX.Y.Z", Install, dismiss.
- `AboutSheet` (ModalBottomSheet, opened from the Home header title):
  - app name and version, rules version
  - update status: Up to date / Update available → Install / Checking…
  - "GitHub" row → Custom Tab to the repo
  - "Report a problem" → Custom Tab to `/issues/new` with the body prefilled with app version, rules version, Android version and WebView version, and nothing personal
- `BlockedNotice`: snackbar-style, about 2 s, "Not available in DMGram", at most once every 3 s.
- `ErrorState`: offline or main-frame load error (`onReceivedError` / `onReceivedHttpError` for the main frame only) with Retry. It replaces WebView's built-in error page. Also used for the "Update Android System WebView" case.

### Phase 5 acceptance

- Unit tests: version compare, rules parsing, sanitizer (include malicious samples that must be rejected).
- Change a hide rule in a locally served `rules.json` → after restarting the app it applies, with no reinstall.
- A debug build with an older `VERSION_NAME` shows the banner; Install opens the download.

---

## Phase 6: Release

- Release signing from `keystore.properties` (gitignored). README documents creating the keystore with `keytool` and warns: **losing it means users can never update**.
- R8 minify + `shrinkResources`, with keep rules for kotlinx.serialization. The release build must contain no debug tooling and must not be debuggable.
- `versionCode` / `versionName` in `gradle.properties`.
- `.github/workflows/release.yml`, on tag `v*`:
  - JDK 21, keystore decoded from a base64 secret, `./gradlew test assembleRelease`
  - rename the APK to `DMGram-vX.Y.Z.apk`, compute its SHA-256
  - create a GitHub Release with both attached
- `README.md`:
  - what it is (PRODUCT one-liner), install, how updates work, what's blocked and why
  - privacy: nothing leaves the phone except to Instagram, and to GitHub for update/rules checks
  - "Not affiliated with Instagram or Meta"
- `.github/ISSUE_TEMPLATE/bug.md`.

### Phase 6 acceptance

Tagging `v0.1.0` on a test repo produces a signed APK that installs over the previous release build.

---

## Hooks for the UX layer (keep these names and signatures)

- `ui/theme/*`: every native color, size, shape and font comes from here.
- Composables:
  - `TabBar(current, unread, onSelect, onReselect)`
  - `HomeHeader(onTitleClick, onSearch, onActivity)`
  - `UpdateBanner(info, onInstall, onDismiss)`
  - `AboutSheet(state, onDismiss, …)`
  - `BlockedNotice(state)`
  - `LoadingOverlay(progress, ready)`
  - `ErrorState(kind, onRetry)`
- `ChromePolicy` table.
- `assets/inject/polish.css` and `polish.js`, injected last, with access to `window.__dmgram`.
- Bridge `theme`, `ready`, `scroll`, `unread` exposed as Compose state.
- `tools/cdp.mjs`.

## `docs/HANDOFF.md` (update every phase; Claude reads this instead of the code)

Keep it under about 200 lines:
1. Status per phase: done / partial / skipped, and why.
2. Deviations from this plan, and why.
3. Architecture map: one line per file.
4. How to run: build and install, `cdp.mjs` usage, rules override, tests.
5. Known issues and flaky areas.
6. What the UX layer must know: Instagram DOM quirks that affect styling, which elements are hidden where, the reel lock approach.

## General rules

- Small commits, messages like `phase 2: route classifier + fixtures`. Tag each finished phase.
- No stubs pretending to be done. If you skip something, say so in `HANDOFF.md`.
- Don't swallow exceptions silently. Log with tag `DMGram`.
- **Never make requests from Kotlin to instagram.com.** Only the WebViews talk to Instagram.
