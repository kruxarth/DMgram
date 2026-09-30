# DMGram — Product spec

**Instagram for staying in touch: your friends' posts and stories, your DMs, and nothing the algorithm picked.**

This file is the source of truth for *what* DMGram is. `PLAN-GROK.md` (build) and `PLAN-CLAUDE.md` (review + UX) describe *how*.

## Who it's for

People who keep Instagram because their friends are there. They mostly watch and message, and rarely post. They want Instagram without Reels, Explore and suggested content. They do not want a focus or productivity tool.

## Principles

1. **Instagram minus the algorithm.** Not a wellbeing app: no timers, limits, grayscale, streaks, usage stats or nudges. Ever.
2. **Minimal in exchange for smooth.** If a feature costs smoothness, cut the feature.
3. **Stand-alone.** Everything works without the official Instagram app installed.
4. **Account-safe.** DMGram only shows Instagram's own website in a web view, exactly like a logged-in phone browser. No private API calls, no background requests, no automation.
5. **Feels native.** It should feel like an app, not a website.

## How it works

A native Android app (Kotlin + Jetpack Compose) that shows instagram.com inside WebViews.

- **We own the frame:** tab bar, Home header, back gesture, keyboard handling, media picker, loading states, transitions.
- **Inside the pages** we inject CSS/JS to hide algorithmic surfaces and remove web-isms.
- **Instagram's own screens** (feed, DM thread, story viewer, profile) stay Instagram's. We adjust them; we don't redesign them.

## Layout

Bottom tabs (3): **Home · DMs · Profile**

| Tab | Shows | Notes |
|---|---|---|
| Home | `instagram.com/?variant=following` (people you follow, newest first) with the stories bar on top | Native header: DMGram name (tap → About sheet), Search icon, Activity icon |
| DMs | `/direct/inbox/` | Tab shows the unread count while the app is open |
| Profile | Your own profile | Instagram's own settings (account, privacy, edit profile/photo) open from here. Instagram's "create" stays reachable from here, never in the tab bar |

The tab bar hides on full-screen surfaces: DM thread, story viewer, reel viewer, login, and whenever the keyboard is open.

## Scope

**In**
- Following feed + stories bar (view, reply, react)
- DMs: text, photos from gallery, shared posts and reels, reactions, voice notes *if Instagram mobile web supports them*
- Any profile, search, activity (follow requests, likes)
- Posts, stories, highlights
- Instagram's own login, 2FA, logout and account settings

**Out (decided)**
- Notifications of any kind. DMGram does no work while closed.
- Reels tab / reels feed, Explore grid, hashtag and location pages
- Sponsored posts, suggested posts, suggested accounts
- Calls
- A DMGram settings page
- Any productivity / wellbeing feature

**Inherited limit:** anything Instagram's mobile website can't do, DMGram can't do.

## The Reels rule

**No algorithmic reels.** A reel from someone you chose to look at (a friend's post in your feed, a reel in a profile you opened, a reel sent in a DM) plays **one at a time** and never advances to a reel you didn't pick. The reels feed never opens.

## Blocked links

Tapping something blocked (hashtag, Explore link) does not navigate. A brief, neutral notice appears ("Not available in DMGram"). No lectures, no explanations.

## DMGram's own UI (complete list)

1. Bottom tab bar
2. Home header
3. Update banner (top of Home, only when an update exists)
4. About sheet: version, update status, GitHub link, Report a problem
5. Single-reel lock / viewer
6. Blocked-link notice
7. Loading, offline and error states
8. Splash + app icon

Anything new needs a strong reason.

## Updates and distribution

- Distributed via GitHub Releases and APK hosting sites; Obtainium-compatible. APK asset named `DMGram-vX.Y.Z.apk`.
- On launch (max once per 24 h) the app checks the latest GitHub release. If newer: banner on Home. Tap → the APK downloads in the browser.
- Hide/filter rules ship as `rules/rules.json` in the repo. The app fetches it (max every 6 h, foreground only) so selector fixes don't need a new APK. **Rules are data only** (selectors, URL patterns, text markers, sanitized CSS), **never JavaScript**.

## Constraints

- Android 8.0+ (minSdk 26), portrait only.
- Package id `app.dmgram`. Final once the first public APK ships; changing it later breaks updates.
- No Instagram logo or wordmark in the icon or UI. "for Instagram" in descriptions is fine. README states it's not affiliated with Instagram or Meta.
- No analytics, no crash reporting, no DMGram servers. The only network traffic outside Instagram is the GitHub update check and rules fetch.
- Permissions: `INTERNET`. `RECORD_AUDIO` only if voice notes work on mobile web.

## Decision log

| Decision | Why |
|---|---|
| Web view, not private API | Lowest ban risk; works without the Instagram app |
| No notifications | WebViews can't receive web push; background polling costs battery and adds account risk; people open DMGram on purpose |
| Following feed as Home | Only accounts you follow, newest first, reportedly no ads or suggestions |
| Native frame + injected CSS, not native screens | Native screens built from scraped data break on every Instagram change and look like a bot |
| 3 tabs, search in header | Search is occasional |
| No settings page | Nothing left to configure once notifications were dropped |
| Posting not promoted, not blocked | Occasional posters would otherwise reinstall Instagram |
| Kotlin + Compose, not Flutter / React Native | Full WebView control, fewer layers |
| Calls not supported | Mobile web likely lacks them; acceptable |

## Build workflow

1. **Grok (Cursor)** builds everything functional per `docs/PLAN-GROK.md`, phase by phase. It writes `docs/FINDINGS.md` (facts about Instagram's live site) and keeps `docs/HANDOFF.md` current.
2. **Claude** reviews and builds the UX layer per `docs/PLAN-CLAUDE.md`, reading `HANDOFF.md` instead of the whole codebase.
