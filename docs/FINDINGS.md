# DMGram findings — Instagram mobile web

Observed 30 Sep 2026 on a POCO X6 Neo 5G (Android 15), WebView Chrome 153, UI language English (`document.documentElement.lang` is `en`). CSS viewport about 375×833, device pixel ratio about 2.88.

DMGram's reduced user agent was in effect the whole time:

`Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36`

Client hints brands: `Not.A/Brand` 99, `Chromium` 153, `Google Chrome` 153. Not `Android WebView`.

The logged-in session survived `am force-stop` and a cold start. Login was done by the user.

**Language.** Selectors that use `aria-label` or visible text are English-only. Selectors that use `href` are not. Obfuscated class names (`x1lliihq`, and the `_9dls` / `_aa4c` classes on `<html>`) are ignored on purpose. `__fb-light-mode` is the exception: it is a stable theme token, not a layout hash.

## Q1 Following feed

`/?variant=following` loads and stays on that URL. It is a chronological feed. The header text is **Following**, with a back control (`svg[aria-label="Back"]`). **The stories bar is not on this page.** `aria-label` values starting with `Story by` are absent. Scrolling about 60 distinct `article` texts (24, then 43 more from that scroll position) found **no** `span` whose own text was `Ad`, `Sponsored`, or `Suggested for you`. Many of those posts are reels (`a[href*="/reel/"]` inside the article) from accounts the user follows. That is allowed by the product rule; it is not the reels feed.

The default home `/` is a different page. It **does** show the stories bar, and the first screen contained both an ad and a suggestion. See Q3 and Q19.

Confidence: high for "following has no stories and no ad/suggestion labels in this sample". The sample is one account, English UI.

## Q2 Navigation

Instagram navigates in-page with `history.pushState` and `history.replaceState`. `popstate` fires on `history.back()`. A hashtag click, a profile click, a reel click, and the Reels tab were all `pushState`. Opening `/reels/` then `replaceState`s to `/reels/<code>/`.

`Page.getNavigationHistory` had `currentIndex` 24 after that browsing, so `WebView.canGoBack()` would be true.

Android back does **not** call `goBack()`. `KEYCODE_BACK` left the app (the launcher resumed). The WebView URL did not change. This build does not install an `OnBackPressedCallback`, so that is the current activity behavior, not Instagram's.

Confidence: high.

## Q3 Home URL

| Action | URL |
|---|---|
| First load, logged out | `/?variant=following` (marketing page) |
| Just after login | `/?deoia=1` once. `deoia` did not stick. |
| Home tab, or the header wordmark's home | `https://www.instagram.com/` with **no** query |
| Header chevron → **Following** | `/?variant=following` |
| Header chevron → menu | dialog text `Following` and `Favorites`. Not create. |

Instagram drops `?variant=following` whenever it navigates "home". The Following item in the logo menu is what puts it back.

Confidence: high.

## Q4 Username

The bottom bar's last tab is `a[href="/<username>/"]`. There is no `svg` label on it; the other four tabs have icons. The same href is the profile page. Language-independent.

On this account the href was `/example_user/`.

Confidence: high.

## Q5 Unread DMs

On the feed, `document.title` is `Instagram`. It does **not** contain `(N)`.

On `/direct/inbox/`, the title is `(3) Instagram • Messages`. `^\((\d+)\)` matches. The count here was the same 3 as the badge.

The badge is in the messages tab, not the title:

- `a[href="/direct/inbox/"]`
- inside it, `div[aria-label="Direct messaging - 3 new notifications link"]`
- inside that, a `span` whose text is the count

The `aria-label` is English-dependent. The `span` of digits inside `a[href="/direct/inbox/"]` is the language-independent fallback. Inbox rows also say `4+ new messages` in their text; that is a preview line, not the badge.

Confidence: high for this English session. The aria-label wording may change with language.

## Q6 Reels from DMs

Tapping a shared reel in `/direct/t/<id>/` does **not** change the URL. It opens a full-screen viewer on top of the thread. The viewer header says **Suggested**. `svg[aria-label="Back"]` is the close control. A speaker control sits at the top right. The tab bar is hidden. About eight `video` elements are mounted (one viewport tall, stacked). A real swipe up advances to a different reel. The URL stays `/direct/t/<id>/` and history does not grow.

This is the algorithmic reel viewer, not a single locked reel. A URL lock cannot see the swipe.

Not a `[role=dialog]`. Confidence: high. One thread, one shared reel.

## Q7 Reels elsewhere

**From the following feed or a profile grid.** The link is `/<username>/reel/<code>/` (`pushState`). It is a single **Post** page (`h1` text `Post`), not a dialog. One `video`, autoplaying, `muted`. Scrolling does not change the URL. No sibling reel links in that viewer. The tab bar stays.

**Profile grid route:** `/<username>/reels/`, tiles link to `/<username>/reel/<code>/`. Same post page as above.

**Reels tab (the feed we must never open).** `a[href="/reels/"]` `pushState`s to `/reels/`, then `replaceState` to `/reels/<code>/`. Many videos are mounted. A swipe up changes the video **without** a new history entry; the URL stayed on the first code. The tab bar stays visible. This is the algorithmic feed.

Confidence: high.

## Q8 Search

The bottom search icon is `a[href="/explore/"]` (`svg[aria-label="Explore"]`).

`/explore/` is a search field (`input[type="search"]`, placeholder `Search`) **plus** the Explore grid (`a[href*="/p/"]` and `a[href*="/reel/"]` tiles). Focusing the field `pushState`s to `/explore/search/`. That page shows **Recent** and **Clear all**. The grid is gone.

To leave only the field and recents/results, hide the grid tiles on `/explore/` (links to `/p/` and `/reel/` in the main grid). Do not hide the search input. `/explore/search/` is the results/recents route and should stay.

Confidence: high.

## Q9 Activity

Route: `/notifications/`. Opened from `a[href="/notifications/"]` (`svg[aria-label="Notifications"]`) in the home header.

The page has **Follow requests**, then **Yesterday** / **Earlier**. Further down, a `span` whose text is `Suggested for you`. There is also a Meta notice ("Meta Accounts are coming to Instagram"), not a suggested-accounts carousel.

Confidence: high that the route and the suggested section exist. The suggested block was below the first screen.

## Q10 Profiles

**Suggested / similar.** `svg[aria-label="Similar accounts"]` (English) sits by the Following / Message buttons. Activating it reveals a `section` (about 260px tall) whose text includes `Suggested for you` and `See all`. That `span` is the language-dependent marker. Story highlights (the circles above that section) are not suggestions.

**Tabs**

| Tab | Path |
|---|---|
| Posts | `/<username>/` |
| Reels | `/<username>/reels/` |
| Reposts | `/<username>/reposts/` |
| Tagged | `/<username>/tagged/` |
| Followers | `/<username>/followers/` |
| Following | `/<username>/following/` |

`reposts` is not in the original route table. Tab icons include `svg[aria-label="Posts"]` and `svg[aria-label="Tagged"]`.

Confidence: high. One other profile plus the logged-in profile.

## Q11 Hashtags and locations

**Hashtags.** The anchor `href` is `/explore/tags/<tag>/`. The click does not stay there. It `pushState`s to `/explore/search/keyword/?q=%23<tag>` and shows a media grid (`h1` is the tag). Both the `href` and the real URL have to be blocked.

**Locations.** On the default home, a post place link was `/explore/locations/<numeric id>/<slug>/`. That page was not opened.

Confidence: high for hashtags. Medium for locations (href only).

## Q12 Create

No create route turned up.

- The header chevron opens Following / Favorites, not create.
- `svg[aria-label="Plus icon"]` is the badge on **Your story**. Taps did not open a composer.
- `/create/select/` is a **username** (`@create`), not a composer. A full navigation loaded that profile.

Story posting was not shown. Instagram's own settings (edit profile, privacy) are under `/accounts/…`, reached from the profile **Options** control (`svg[aria-label="Options"]` → `/accounts/settings/?entrypoint=profile`).

Confidence: medium. A create sheet may exist behind a control this pass did not hit. It is not in the tab bar.

## Q13 Nags

Logged out, on `/?variant=following`: a top **Open app** button, a large **Open Instagram** button, and **Log in**. Selectors were not captured before login.

Logged in, a bar sits just above the tab bar on `/`, profiles, post/reel pages, `/explore/`, and `/notifications/`:

- `button` whose own text is `Use the app`
- `svg[aria-label="Close"]` on the same bar

On one post page the bar was `position: fixed`, about 35px tall, just above the 66px tab bar. It was **not** present on `/?variant=following` the last time that page was checked.

`/notifications/` also has the Meta accounts notice (text, not a button).

No "Turn on notifications" banner was seen. These strings are English. They were not compared against the stock WebView user agent (see Q14).

Confidence: high for `Use the app` + `Close`. Medium for the logged-out labels (seen, not given a DOM path).

## Q14 WebView detection

Our UA and client hints are above. Login, the feed, DMs, reels, explore, and settings all worked. Nothing looked blocked.

Not done:

- No side-by-side load with the stock WebView UA (`Version/4.0`, often `wv`). Changing it would reload the session.
- **Log in with Facebook** was not tried. The user was already logged in; starting Facebook login could replace that session.

Confidence: high that this UA can stay logged in. Low for "identical to Chrome" and for Facebook login.

## Q15 DM features

Inbox routes: `/direct/inbox/`, `/direct/requests/`, `/direct/new/`, thread `/direct/t/<id>/`. Threads are `[role=button]` rows, not `<a href>`. Opening one `pushState`s to the thread.

Composer controls seen (English `aria-label`):

| Control | Seen |
|---|---|
| Add Photo or Video | yes |
| Choose a GIF or sticker | yes |
| Voice Clip | yes |
| React (`React to message from …`) | yes |
| Reply (`Reply to message from …`) | yes |
| Message actions | yes |
| Vanish mode | not seen |
| Unsend | not opened (it may live under Message actions) |

A shared reel plays (Q6). **Your note** is on the inbox. Nothing was sent: no photo, no voice recording, no reaction, no unsend. This build has no file-chooser callback and no `RECORD_AUDIO` permission, so a real send was not attempted.

Confidence: high that those buttons exist. Low for "the send actually works" until Phase 4.

## Q16 Dark mode

Right now the page follows light:

- `<html>` class contains `__fb-light-mode`
- `body` background `rgb(255, 255, 255)`
- `color-scheme: light`
- `matchMedia('(prefers-color-scheme: dark)').matches` is false

Instagram also has its own switch: `/accounts/switch_appearance/`, body text `Switch appearance` / `Dark mode`, an `input[type=checkbox][role=switch]` with `aria-checked="false"`. It was not toggled. The dark class was not observed; the light token is `__fb-light-mode`.

Confidence: high for the light signal and the settings route. The dark class name is an inference, not observed.

## Q17 Video

Feed and post videos start playing with `muted === true` and `paused === false`. The mute control is `svg[aria-label="Audio is muted"]`. No separate fullscreen button was found on the post page. The `/reels/<code>/` viewer and the DM suggested viewer are already full-bleed (the DM one hides the tab bar). Sound was not unmuted.

Confidence: high for muted autoplay. Medium for "there is no fullscreen API surface".

## Q18 Instagram's chrome

**Bottom bar.** A `position: fixed` div at the bottom of the layout viewport. On this phone: top 767, height 66, width 375, padding `0 0 16px`. Links, in order:

| href | svg aria-label |
|---|---|
| `/` | Home |
| `/explore/` | Explore |
| `/reels/` | Reels |
| `/direct/inbox/` | Messages |
| `/<username>/` | none (profile image) |

**Home header.** `header`, `position: fixed`, top 0, height 44. Contains the wordmark (`svg[aria-label="Instagram"]`), `svg[aria-label="Down chevron icon"]`, and `a[href="/notifications/"]` (`svg[aria-label="Notifications"]`). On the following feed the header text is `Following` and the back icon is `svg[aria-label="Back"]`.

**Padding.** `main` has padding and margin 0. The fixed bars cover the top 44px and the bottom 66px. The Use the app bar, when present, is another fixed strip just above the tab bar.

Icon labels are English. The hrefs are not.

Confidence: high.

## Q19 Feed transport

The following feed did not show ads, so network filtering is not required for that page. Pagination was still visible: **XHR** `POST /graphql/query`, form field `fb_api_req_friendly_name=PolarisFeedRootPaginationCachedQuery_subscribe` (doc id observed, will change). Video bytes came from `*.fbcdn.net`. Logging went to `graph.instagram.com/logging_client_events`.

The **default** home `/` does contain `Ad` and `Suggested for you` as `span` text inside `article`. Those are catchable in the DOM (Q1, Q3). Same idea if a later Following response grows an ad: the label is on a short `span`, not the whole caption.

Confidence: high for the endpoint name and the DOM labels. The doc id should not be hardcoded.

## Q20 Auth routes

| What | Observed |
|---|---|
| Logged-out marketing page | `/?variant=following` before login. Labels: Log in, Open Instagram, Log in or sign up, Open app |
| `/accounts/login/` while logged in | redirects to `/` |
| Settings, non-auth | `/accounts/settings/`, `/accounts/edit/`, `/accounts/switch_appearance/`, and `https://accountscenter.instagram.com/…` |
| Signup, challenge, two-factor, onetap | not shown this session |

Confidence: high for the redirect and the settings host. The pre-login screen was seen but its form `href`s were not recorded. 2FA was not on screen.

## Q21 External links

A profile website used the shim:

`https://l.instagram.com/?u=<encodeURIComponent(target)>&e=<token>`

The visible text was the bare host and path. A Threads row was a direct `https://www.threads.com/@<username>…` link, **not** the shim. Both are external.

Confidence: high.

## Q22 Multiple tabs

After Android back and a relaunch, DevTools listed two live page targets in this process: one on `/?variant=following`, one still on the profile. Pointing the second at `/direct/inbox/` loaded the inbox (`title` `(3) Instagram • Messages`, thread list present). The feed target stayed on Following. Neither document contained "another window" / "open in another".

A brand-new message arriving live was not watched. `Target.createTarget` is not supported by this WebView, so the second target is the leftover page from the relaunch, not a second tab we created on purpose. Phase 1's real second WebView should confirm this.

Confidence: medium.

## What this changes in the plan

1. **Home is not `/?variant=following` unless we force it.** Logo, home tab, and post-login landing use `/`, which has stories **and** ads and suggestions. The Following menu item and a direct load use `/?variant=following`, which has **no stories bar** and, in this sample, no ads. The product wants both the following feed and the stories bar. Those are different Instagram screens today.
2. **Reel lock cannot be URL-only.** DM reels and swipes inside `/reels/<code>/` advance with no URL change. `/<user>/reel/<code>/` is already a single post. Blocking `/reels/` and locking the suggested viewer in the DOM matters more than reverting reel URLs.
3. **Hashtags land on** `/explore/search/keyword/?q=%23…`, while the anchor still says `/explore/tags/…`.
4. **Profile reposts** (`/<user>/reposts/`) need a route class.
5. **Unread title works on the inbox only.** The feed badge is DOM.

## Phase 1–2 notes (30 Sep 2026, same phone)

These were checked in the debug app after the frame and the rules existed. Chrome 153 WebView, account `example_user`, CSS viewport about 375×698 inside the padded WebView (the full screen is 1080×2400).

**navigate().** Click works for `/`, `/explore/`, `/notifications/` (when the heart link is in the DOM), and `/<username>/`. `pushState` + a synthetic `popstate` renders `/notifications/` when the link is missing, and renders `/explore/search/` (Recent, Clear all). Focusing `input[type=search]` from script does not leave `/explore/`. A real tap does. Header search therefore clicks `/explore/` and then pushStates to `/explore/search/`.

**`/direct/inbox/` from another tab** must not be an in-page navigation. `navigate()` asks native to switch. An earlier version fell through to `pushState` and put the inbox URL on the Home WebView.

**History.** `history.length` and `navigation.currentEntry.index` move with `pushState`. `WebView.canGoBack()` stays false, and `goBack()` / `goBackOrForward()` do nothing, while `history.back()` and `history.go(n)` work. Q2's note that `canGoBack()` would be true does not hold for these same-document entries. Android back uses the JS history. At index 0 it moves the task to the back.

**`/explore/`.** Allowed, same as `/explore/search/`. The plan's later sentence that `pushState('/explore/')` is blocked is stale. `/explore/search/keyword/` and `/reels/` are blocked. The notice is "Not available in DMGram".

**Reels.** `/friend_c/reel/REELCODE1/` from a profile grid is one video, route `REEL_SINGLE`. A reel shared in a DM opened a viewer on the thread URL: one viewport-tall video, two 0×0 videos, no "Suggested" text. Swipes did not change the account (`lolwith_vibe`). The bubble `img` had no reel `href`. The lock arms on that single viewport video. The thread header and the viewer both expose `svg[aria-label="Back"]`; the viewer close must use the one that contains the viewport video, or the thread back lands on the inbox and the Home tab switches to DMs.

**Two WebViews.** Home and the inbox stay loaded together. No "open in another window" string was on the inbox. A new message arriving while both were alive was not watched.

**Renderer.** `Page.crash` on one target killed the shared renderer. The process survived and both tabs were created again. Home came back at `/`.

**DevTools.** Both WebViews report `visible: true` when one is `View.INVISIBLE`.

**Keyboard.** Gesture and 3-button: the thread composer sits on the keyboard. Navigation mode was restored to `2`. Night mode was restored to auto. Toggling night did not reload the documents.

**Background.** With DevTools `Network.enable` on both WebViews, pressing Home and waiting two minutes produced **no** HTTP requests. Two `POST /ajax/bz` calls in an earlier window were from the second before Home, not from the background. An idle websocket was not counted.

**Incoming view.** `am start -a android.intent.action.VIEW -d https://www.instagram.com/p/Dd0vEquDquw/` opened the official Instagram app. The same intent with `-n app.dmgram.debug/app.dmgram.MainActivity` loaded `/p/Dd0vEquDquw/` in the Home tab (`POST`). Supported links are not verified (`autoVerify` is off), so the user has to open Instagram links with DMGram.

**External link.** A Threads profile link (`https://www.threads.com/@…`) opened a Firefox Custom Tab.

**Reel dismiss.** The viewer chevron is `svg[aria-label="Close"]`, not Back. `element.click()` does not dismiss it. A real tap does, and the thread stays on `/direct/t/…`. Android back sends that tap. The thread's own control remains `svg[aria-label="Back"]`.
