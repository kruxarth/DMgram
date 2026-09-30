# DMGram

**Instagram for your friends, not for the algorithm.**

You keep Instagram because your friends are there: their stories, their posts, your group chats. But every time you open the app to reply to a message, you end up forty reels deep into strangers. DMGram keeps the part you came for and leaves the rest out.

- Your friends' **stories** and **posts**, newest first
- Your **DMs**
- Search, profiles and follow requests, so you can still find and add people

No Reels tab. No Explore page. No suggested posts, no ads, no "you might like".

It's also **built to keep your account safe**: DMGram is simply Instagram's own website, used the way your phone's browser uses it. It doesn't pose as the Instagram app or automate anything, which is what gets third-party Instagram apps' users banned. [More on that below.](#will-my-account-get-banned)

DMGram is free, open source, and not affiliated with Instagram or Meta.

## What it does

**Home** shows your friends' stories on top and the people you follow below, newest first. Keep scrolling and you just see older posts from the same people. Nothing gets mixed in.

**DMs** work as you'd expect: text, photos from your gallery, voice clips, reactions, replies, shared posts. When a friend sends you a reel, it opens and plays, just that one. Swiping doesn't pull you into the next one.

**Profile** is your own profile, with Instagram's usual account settings.

**Search** finds people, not a grid of viral posts. Hashtag and Explore links simply don't open. You'll see "Not available in DMGram" and stay where you were.

## What it isn't

DMGram is not a productivity app. There are no timers, daily limits, grayscale mode, streaks, usage stats, or "are you sure you want to keep scrolling?" nags. It doesn't try to change your habits or make you feel guilty. It just removes what you didn't come for.

It's also small: the whole app is about **1.5 MB**, it doesn't need the Instagram app installed, and it does nothing at all when it's closed.

## Privacy

**DMGram collects nothing.** There is no DMGram server, no account, no analytics, no crash reporting, no ads SDK and no tracking. The developer can't see who uses the app, let alone what they do in it.

### How it works

DMGram shows Instagram's own website (instagram.com) inside the app, the same way a phone browser would, with a native frame around it and the algorithmic parts hidden. Everything you see comes straight from Instagram to your phone. DMGram never sits in the middle.

### Your password

You log in on Instagram's own login page. DMGram's code never reads what you type there. It only checks whether you're logged in, the way a browser does.

### What stays on your phone

- **Your Instagram login**, in the app's private storage, like a browser keeps it. It's excluded from phone backups and from device-to-device transfer, so it never leaves the phone.
- **A few app preferences**: your username (to open the Profile tab), the size of the stories row, and whether you dismissed an update notice.
- **A cached copy of DMGram's hide rules** (see [Updates](#updates)).

That's all. Logging out removes your Instagram login; uninstalling removes everything.

### Who your phone talks to

| Where | Why |
|---|---|
| Instagram (instagram.com and its image/video servers) | Everything you see in the app, exactly what Instagram's website loads |
| GitHub (api.github.com, raw.githubusercontent.com) | At most once a day, "is there a newer version?", and at most every 6 hours, the latest hide rules. Nothing about you is sent; GitHub sees an ordinary request, like any website visit |

Nothing else. Nothing happens in the background, and the app doesn't wake up on its own.

What you do *on Instagram* is still Instagram's business, as it would be in any browser, and [Instagram's own privacy policy](https://privacycenter.instagram.com/policy) applies to that. DMGram adds nothing on top.

### Permissions

- **Internet**, to load Instagram.
- **Microphone**, only if you record a voice clip in a DM. Android asks you first, and only Instagram's site can use it.

No contacts, location, camera, storage or notification permissions. Photos you send go through Android's own photo picker, which only shares the pictures you choose.

### Don't trust us, check

Every line of DMGram is in this repository. The security-relevant parts:

- [`app/src/main/assets/inject/`](app/src/main/assets/inject/) holds all the code DMGram adds to Instagram's pages. It hides things and moves the page around. It doesn't read your messages, and nothing it does leaves your phone.
- [`rules/rules.json`](rules/rules.json) is the only thing the app downloads besides the update check. It can only contain CSS selectors, URL patterns and text, never code, and the app rejects anything else.

## Install

1. Download `DMGram-vX.Y.Z.apk` from the [latest release](https://github.com/kruxarth/DMgram/releases/latest).
2. Open it. Android will ask you to allow installs from your browser or file manager.
3. Log in with your Instagram account.

Requires Android 8.0 or newer.

### Checking the download (optional, recommended)

Android refuses an *update* signed by anyone else, but a *first* install has no such check. To make sure the APK is really this project's:

1. Compare it with the `.sha256` file on the same release:
   ```
   sha256sum DMGram-vX.Y.Z.apk
   ```
2. Check who signed it:
   ```
   apksigner verify --print-certs DMGram-vX.Y.Z.apk
   ```
   The certificate's SHA-256 digest must be:
   `ab50d0df3604a6b9dbead845dd9751f52495116a2ac4770033d69b1eb9f83827`

## Updates

When you open DMGram (at most once a day), it checks this repository for a newer release. If there is one, a small banner appears on Home. Tapping **Install** downloads the new APK in your browser. DMGram never installs anything by itself.

Instagram changes its website often. When something that should be hidden starts showing again, the fix usually ships as an update to [`rules/rules.json`](rules/rules.json), which the app picks up by itself, with no new APK needed.

## Limitations

DMGram can only do what Instagram's mobile website can do, and a few things are left out on purpose:

- **No notifications.** An app built on a web page can't receive Instagram's push notifications, and checking for messages in the background would drain your battery. You'll see your unread count when you open the app.
- **No calls.** Instagram's mobile website doesn't support them.
- **No feed posts.** Instagram's mobile website has no way to create them. "Your story" on Home opens Instagram's own story flow.
- **It depends on Instagram's website.** If Instagram changes something, a part of the app can misbehave until a fix ships.

## Will my account get banned?

It's very unlikely, and here's why. Instagram flags accounts that behave like bots: logging in through apps that pretend to be the official Instagram app, automated likes, follows or messages, scraping, logging in from servers. DMGram does none of that:

- **It's Instagram's own website.** Every page and every request comes from instagram.com's own code, exactly as in Chrome on your phone. DMGram doesn't use Instagram's private app interface and doesn't send Instagram anything the website wouldn't.
- **Nothing is automated.** Nothing happens on your account unless you tap it.
- **You log in on Instagram's own page, from your own phone.** Your login never passes through anyone's server.
- **Nothing runs in the background.** When DMGram is closed, it does nothing.
- **Hiding happens only on your screen**, like an ad blocker in a browser. Instagram isn't told anything different.

To Instagram, you look like one of the many people using instagram.com in their phone's browser.

The honest fine print: DMGram is unofficial, and Instagram enforces its own rules, so no one outside Meta can promise what Instagram will do. But DMGram is deliberately built to be indistinguishable from a normal browser, which is as safe as an unofficial app can be.

## Building it yourself

You need JDK 21 and the Android SDK.

```
./gradlew test assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Release builds are made and signed by the [release workflow](.github/workflows/release.yml) when a `v*` tag is pushed.

## Contributing and problems

Found something that shows up when it shouldn't, or broke after an Instagram change? Open an issue, or use **Report a problem** in the app's About screen (tap the DMGram title on Home). It fills in your app, Android and WebView versions, and nothing else.

---

DMGram is an independent project, not affiliated with, endorsed by or connected to Instagram or Meta. "Instagram" is a trademark of Meta Platforms, Inc.
