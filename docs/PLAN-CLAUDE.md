# DMGram — Review + UX plan (for Claude)

## Context (read first, and only these)

- `docs/PRODUCT.md`: the spec. The DMGram UI list and the Reels rule are binding.
- `docs/HANDOFF.md`: what Grok built, deviations, how to run.
- `docs/FINDINGS.md`: facts about Instagram's live mobile site (selectors, routes, quirks).

**The user's Claude budget is limited.** Don't read the codebase wholesale; open a file only when a step needs it. Prefer `git diff phase-N..phase-M --stat`, then targeted reads. Grok (Cursor) is cheap: write bigger fixes up as numbered tasks for Grok instead of doing them yourself. Spend Claude tokens on judgment (architecture, UX) and on the UX layer.

## Tooling

- Device: `adb devices` must show the user's phone. Build and install: `./gradlew installDebug`.
- Screenshots: `adb exec-out screencap -p > <scratchpad>/s.png && magick <scratchpad>/s.png -resize 40% <scratchpad>/s.png`, then Read it. Only take one to verify something.
- Input: `adb shell input tap X Y`, `input swipe X1 Y1 X2 Y2 200`, `input keyevent 4` (back), `input text '...'`.
- Live page access (see HANDOFF): `node tools/cdp.mjs targets | eval '<js>' | css <file>`.
  - `eval` returns only what you ask for. Query specific selectors and truncate `outerHTML`; never dump the DOM.
  - `css app/src/main/assets/inject/polish.css` restyles the live pages instantly. Iterate there, then rebuild once at the end of a batch.
- Native UI changes (Compose) need `./gradlew installDebug`. Batch them.

## Step 0: Checkpoint review (optional, after Grok tags `phase-2`)

Cheap insurance before Grok builds on the foundation. Check only:
- **WebView ownership:** created once in `TabController`, never in a recomposable factory; hidden tabs set `INVISIBLE`, not destroyed; `pauseTimers` on stop; `onRenderProcessGone` returns true.
- **Insets and IME:** the DM input sits on the keyboard with gesture and 3-button nav (screenshot).
- **Bridge:** origin-restricted listener; messages validated; URLs always pass through `NavPolicy`.
- **Navigation:** the four enforcement layers exist, and the fixtures cover the edge cases in PLAN-GROK §2.4.
- **Reel lock:** works from a DM, the feed and a profile grid (device).

Output: `docs/REVIEW-1.md`, numbered tasks for Grok. Fix only one-liners yourself.

## Step 1: Final review (after Grok tags `phase-6`)

1. Device walkthrough, using the acceptance lists from every phase in PLAN-GROK as the checklist: cold start, tab switching, back, reselect, keyboard, reels, blocked links, search, profile, login/logout, update banner, rules override, renderer crash.
2. Security pass (targeted files only):
   - `RulesSanitizer` and the rules pipeline: no path lets remote data become JS
   - `Bridge` and `NavPolicy` URL handling
   - Manifest: exported components, permissions, intent filters
   - Release build: not debuggable, no dev tooling
   - No Kotlin → instagram.com requests (`grep -rn "instagram.com" app/src/main/java`)
3. Output: fix small issues directly, and put bigger ones in `docs/REVIEW-2.md` for Grok. Let Grok finish them before Step 2 if they touch the foundation.

## Step 2: UX layer

### Direction

- **It should feel like the Instagram app the user already knows, minus the noise.** Match the Instagram app's conventions (tab bar height, icon weight, spacing, motion) so muscle memory works. DMGram's own identity appears only in the wordmark, the About sheet and the icon.
- **Instant:** no white frames, no blank screens, no layout jumps.
- **Native gestures** everywhere.
- **Quiet:** notices are brief; nothing nags.
- **Consistent** in light and dark.

### Work items (in order of impact)

1. **Remove web-isms** (`polish.css`, iterated live via `cdp.mjs css`):
   - tap highlight, hover states on touch, text selection on UI chrome
   - `overscroll-behavior`, focus rings, scrollbars
   - gaps left by hidden Instagram chrome, safe areas
   - inputs that zoom on focus, `touch-action` delays
2. **Theme sync:** native chrome and the WebView background follow the bridge `theme` event. No flash on launch, tab switch or dark-mode change. Status and navigation bar icon colors match.
3. **Tab bar:** Instagram-style outline/filled icons, unread badge, reselect behavior, light haptic on tap, a smooth hide/show for full-screen routes and the keyboard.
4. **Home header:** DMGram wordmark (typeset, no image needed), Search and Activity icons. Decide whether to hide it on scroll down and show it on scroll up like the Instagram app. Only if it stays jank-free with a WebView.
5. **Loading:** splash → first paint. Per-tab first load uses a skeleton or thin progress bar in theme colors, never white. Preloaded tabs show no loader at all.
6. **Full-screen surfaces:** story viewer and reel lock go immersive (system bars hidden), with a clear close/back affordance.
7. **DMGram UI pieces:** update banner, About sheet, blocked notice, offline/error state. Small, native and calm.
8. **DM thread:** input bar vs keyboard, photo picker round trip. Restyle Instagram's thread only where the web version looks clearly off compared with the app.
9. **Create entry point** on the Profile tab (per FINDINGS Q12). Reachable, never prominent.
10. **App icon:** adaptive and monochrome (themed icons), no Instagram marks.
11. **Tune `ChromePolicy`** and pull-to-refresh per route.

### Verify each item

- Light and dark.
- Gesture navigation (and 3-button if the phone allows switching).
- Compare with the Instagram app where the user has it available. Otherwise use memory of its layout.

## Definition of done

- Home, inbox, DM thread, own profile, other profile, story, reel, search, activity, About sheet and error state all look intentional in light and dark. Verified by screenshot.
- No web-isms visible, no white flashes, no layout jumps on tab switch.
- Every PLAN-GROK acceptance check still passes.
- `polish.css` / `polish.js` are commented by section. `HANDOFF.md` has a short "UX layer" section describing what was changed and where.
