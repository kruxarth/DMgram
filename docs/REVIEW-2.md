# Review 2: after phase-5 (Claude)

Phases 3–5 match the plan:
- Home ends at "caught up", with no pagination loop.
- The Follow-button signal works well.
- Instagram's chrome is hidden, not removed.
- The photo picker fires its callback exactly once.
- The rules pipeline re-parses JSON strictly before injecting it.
- The update check is sound.
- T1–T3 from Review 1 are verified on device.

## Already fixed by Claude

- **Remote rules could make external sites load inside DMGram.** A route in the downloaded `rules.json` may list `hosts`, and `classify()` trusted them, so a tampered rules file could route a bio link to a look-alike login page inside the app frame.
  - The sanitizer now rejects any route host outside `instagram.com`.
  - `classify()` (Kotlin) and `router.js` ignore such hosts even if one gets through.
  - Test: `RulesSanitizerTest.rejectsRoutesForOtherHosts`.
- **Microphone was granted to any origin** that asked for audio. Now it's only granted to `https://www.instagram.com`. (`MediaRequests.onPermissionRequest`)

## Tasks for Grok (do alongside Phase 6)

### T5. Cleanup cost (should fix)

**Problem:** FINDINGS shows the rAF scan averaging 2.2 ms, with one 7.1 ms batch. On a 120 Hz screen the frame budget is 8.3 ms, so this can drop frames while scrolling Home.

**Target:** ≤ 3 ms for every batch.

**Fix:**
- Only scan `article`s that aren't already `data-dmgram-checked`.
- Stop scanning once the caught-up cutoff is applied.
- Avoid document-wide `querySelectorAll` per frame.

**Accept:** measure a fast fling through Home and record the max batch time in HANDOFF.

### T6. Reel lock false positive on Voice Clip (low; only if cheap)

**Problem:** the recorder arms the lock. Back still closes it, so the impact is small.

**Fix:** if a structural signal separates it from a reel viewer (for example, the recorder's video has no `currentSrc`, or isn't playing), exclude it. Otherwise leave it and note it in HANDOFF.
