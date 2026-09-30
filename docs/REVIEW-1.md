# Review 1: checkpoint after phase-2 (Claude)

Overall: the foundation is sound.
- WebViews are created once in `TabController`, hidden with `INVISIBLE`, paused with `pauseTimers`, and recovered after a renderer crash.
- The bridge is origin-restricted and size-capped.
- All four navigation layers exist, and 81 fixtures cover the edge cases.
- The `history.back()` workaround for `canGoBack()` is reasonable. That's most likely Chromium's history intervention, which skips pushState entries made without a user gesture.

Fix the tasks below **before starting Phase 3**, then continue with Phases 3–5 as planned.

## Already fixed by Claude

- **Incoming links could load any website inside DMGram.** `MainActivity` is exported, so any app can send an explicit `VIEW` intent with any URL. `openIncoming` sent `External`/`System` decisions to `HOME.loadUrl(...)`, which let a fake Instagram login page appear inside DMGram's frame. Now `External` → Custom Tab, `System` → system intent, `Ignore` → nothing; only Instagram routes load. (`TabController.openIncoming`)
- The Phase 2 acceptance line that used `pushState('/explore/')` now uses `/explore/tags/test/`.

## Tasks for Grok

### T1. Android back must never do nothing in the reel viewer (must fix)

**Problem:** while `reelLocked`, `onBack()` only calls `tapReelBack()`. If `reelBackPoint()` returns `""`, back is dead. That happens with:
- a non-English UI (`aria-label="Close"` / `"Back"` are English)
- an Instagram markup change
- a lock armed on some other full-screen video

**Fix:**
1. Make `reelBackPoint()` **structural first**:
   - within the viewer host (the full-viewport ancestor of the visible video), take the top-most, then left-most `svg` inside a `button` / `[role=button]` / `a` in the top 160 px
   - keep the English `aria-label` match only as a tiebreaker
2. If still nothing is found: dispatch a `keydown` `Escape` on `document`, then re-check the lock after about 300 ms.
3. If the lock is **still** active after that: log it, unlock, and fall through to normal back (`stepBack` → tab switch → `moveTaskToBack`). Back may never be a no-op.

**Accept:** with `aria-label` matching disabled (temporarily rename the attribute check), back still closes the viewer and returns to the thread.

### T2. The lock can stick after the viewer closes (should fix)

**Problem:** `viewportVideos()` and `reelBackPoint()` only check a video's **size**, not whether it's on screen. If Instagram hides a closed viewer by transform, offscreen position or opacity instead of unmounting it, the lock stays armed. Then document-wide `touchmove` is prevented (**the DM thread can't scroll**) and back keeps routing to `tapReelBack`.

**Fix:**
- Count a video only if its rect intersects the viewport (`bottom > 0 && top < innerHeight`), and it and its ancestors aren't `visibility:hidden` or `opacity:0`.
- Also unlock when the thread composer textbox is visible and focusable again.

**Accept:** open → close a DM reel five times in a row. After each close the thread scrolls normally, and back leaves the thread as usual.

### T3. Prove the stacked "Suggested" viewer is locked (must verify)

**Problem:** Phase 2 only met the single-video variant. Phase 0 saw the stacked variant (about 8 videos, header "Suggested", swipes advanced), and that path (`lockReel` with ≥ 2 videos + scroller) is unexercised.

**Fix:**
- Add `touch-action: none` to `[data-dmgram-reel-scroller]`, and to the viewer host when there's no scroller. That stops browser panning even if Instagram drives the swipe with pointer events; taps still work.
- Then, with a reel that opens the stacked viewer (ask the user to send one from another account if needed), check:
  - the kept item is **the reel that was sent** (compare the poster or author with the DM bubble), not just whichever item is nearest the top
  - swipe up and swipe down show no other reel
  - mute, pause and close still work
  - back returns to the thread

Record the results in FINDINGS.

### T4. Remove dead code (minor)

`dmgram.closeReelViewer` in `router.js` is never called (HANDOFF says a script `click()` doesn't dismiss the viewer). Delete it.

## Human checks (the user does these on the phone)

- With DMGram open on the DMs tab, send the account a DM from another device: it should appear live without a refresh.
- Open a reel someone sent you in DMs: swiping should do nothing, and Android back should return to the chat.
