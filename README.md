# Freedium for Medium

A tiny Android app (≈49 KB, no third-party libraries): a Medium browser that falls back to
[Freedium](https://freedium-mirror.cfd) for stories you can't read.

## What it does

- **Opens to your Medium feed.** The home screen is the real `medium.com` in a WebView with a
  persistent session, so you land on your own logged-in dashboard.
- **Paid articles auto-open via Freedium.** When you open a member-only story you *can't* read,
  the app detects the paywall gate and reopens it through Freedium. Stories you *can* read as a
  member stay on Medium. Detection is best-effort — see the caveat below.
- **Floating "Read free" button** on any Medium article — one tap sends the current article
  through Freedium, whatever the auto-detection decided.
- **Also a share / open-with target.** Share a Medium link from anywhere, select a link and pick
  "Read on Freedium", or tap a medium.com link once the app is set as the default handler.

### Supported link formats

`medium.com/@user/slug`, `medium.com/p/<id>`, any `*.medium.com` subdomain, `link.medium.com`
short links (resolved by following the redirect first), and Medium-hosted custom-domain blogs.
Tracking params (`source`, `utm_*`, `ref`, Branch ids) are stripped; friend-link `?sk=` is kept.

### Settings

- **Auto-open paywalled articles via Freedium** toggle.
- **Open Medium links in this app** — jumps to Android's *Open by default* screen.
- **Sign out of Medium** — clears the in-app cookies/session.
- **Freedium mirror** — editable domain with a reachability **Test** (default
  `https://freedium-mirror.cfd`), in case Freedium moves.
- **Open a link via Freedium** — paste any Medium URL.

## Caveat on paywall detection

Medium does not cleanly mark "this reader can't see this story". The app looks for the
*member-only* badge together with an upgrade/sign-up gate and treats that as locked. It can miss
or misfire as Medium changes its markup, so the **Read free** button is always the reliable path.

## Not included (by design)

A floating button *over the official Medium app* is intentionally not built — it would require an
accessibility service reading another app's screen to scrape the article URL, which is invasive
and fragile. The in-app floating button covers the same need cleanly.

## Build

Requires JDK 17+ and the Android SDK (platform 36). `local.properties` points at the SDK.

```bash
./gradlew testDebugUnitTest   # unit tests for link handling
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Release signing reads `keystore.properties` + `freedium-release.jks` (both git-ignored).
Keep them: updates must be signed with the same key to install over an existing version.

## Layout

| File | Purpose |
| --- | --- |
| `FreediumLinks.java` | URL extraction, tracking cleanup, host/article recognition, Freedium URL building (pure Java, unit tested) |
| `LinkResolver.java` | Follows short-link redirects off the main thread |
| `HomeActivity.java` | medium.com browser: feed, paywall auto-route, floating "Read free" button |
| `ReaderActivity.java` | Share / view / process-text target with the Freedium WebView reader and retry page |
| `SettingsActivity.java` | Auto-route toggle, mirror editor + test, default-handler shortcut, Medium sign-out |
| `Prefs.java` | Stores the mirror and the auto-route preference |
