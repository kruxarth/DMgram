# DMGram

Instagram for staying in touch: your friends' posts and stories, your DMs, and nothing the algorithm picked.

DMGram is not affiliated with Instagram or Meta.

## Install

Download `DMGram-vX.Y.Z.apk` from the [GitHub releases](https://github.com/kruxarth/DMgram/releases). Android will ask you to allow installs from your browser. Later downloads from those releases update the app you already have.

Android 8.0 or newer. Portrait only.

## Updates

On launch, at most once a day, DMGram checks this repo's latest GitHub release. If it is newer, Home shows "Update available · vX.Y.Z". Install opens the APK in the browser. DMGram does not install it itself.

Selector fixes ship in [`rules/rules.json`](rules/rules.json). The app fetches that file, at most every 6 hours, while it is in the foreground. Rules are selectors, URL patterns, text markers, and sanitized CSS. They never contain JavaScript.

## What it leaves out

DMGram shows Instagram's own site and hides the algorithmic surfaces:

- Reels tab, Explore grid, hashtags, keyword and location results
- Ads, suggested posts, suggested reels, suggested accounts, and everything after "You're all caught up"
- Notifications, calls, and a settings page

A reel you actually opened plays one at a time. Tapping a blocked link does nothing except the notice "Not available in DMGram".

## Privacy

Nothing leaves the phone except to Instagram, and to GitHub for the update check and the rules file. There is no DMGram server, no analytics, and no crash reporting. The app does no work while it is closed.
