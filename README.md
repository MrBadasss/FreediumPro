# Freedium Pro

An Android Medium reader that falls back to [Freedium](https://freedium-mirror.cfd) for stories you
can't read, plus reading tools. No analytics, one permission (INTERNET).
Install id `com.mrbadasss.freediumpro`, code namespace `app.freediumpro`.

## What it does

- **Opens to your Medium feed** — the real `medium.com` in a full-screen WebView (your logged-in
  session), with no duplicate app bar. Pull down to refresh.
- **Paid articles → Freedium** — a member-only story you can't read auto-opens through Freedium
  (detection ported from the MrBadasss userscript: `al:android:url` meta + "Member-only").
- **Draggable unlock button** — shows on article pages; one tap reads it free. Elsewhere it's a
  menu dot (tap = menu, long-press = menu).
- **Share / open-with target** — share a Medium (or NYT, WaPo, Bloomberg, Reuters, Economist, FT,
  The Athletic) link from anywhere, or from the Medium app, and it opens via Freedium.
- **Blocks the "Open in app → Play Store" trap** and hides Freedium's donation drawer and
  announcement toasts.

## Reader tools

Text bigger/smaller, reading theme (default / sepia / dark), a reading-progress bar, **read aloud**
(system TTS), **save to list**, **save for offline** (web archive), share/copy the Freedium link,
open in browser, open the original. **Automatic mirror failover** — if one Freedium mirror is down
it tries the next.

## Library

Reading list (**Saved**) and **History** of recently-read articles, reachable from the menu.
Offline-saved items open without network.

## Convenience

- **Clipboard prompt** — on launch, offers to open a copied Medium link.
- **App shortcuts** (long-press icon): "Paste & read" and "Saved articles".
- **Quick Settings tile** — paste & read from the notification shade.

## Settings

Auto-route toggle · **App logo** picker (Unlocked / Key / Reader / Monogram) that also sets the
home-screen icon · open-by-default shortcut · Medium sign-out · Freedium mirror editor + Test +
**Find a working mirror** · Maintenance: clear cache, back up / restore settings (JSON).

## Branding

A brief launch splash (logo + "CONTRIBUTED BY / Mr_Badasss") and a different reader loading
animation (spinning ring + "unlocking…"). Both are built in code, not editable resources — though
no distributed APK is truly tamper-proof.

## Build

JDK 17+ and the Android SDK (platform 36). `local.properties` points at the SDK.

```bash
./gradlew testDebugUnitTest
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Release signing reads `keystore.properties` + `freedium-release.jks` (git-ignored). Updates must be
signed with the same key.

## Source layout (`app/src/main/java/app/freediumpro/`)

| File | Purpose |
| --- | --- |
| `FreediumLinks.java` | URL/host/article detection, tracking cleanup, Freedium URL building (unit tested) |
| `LinkResolver.java` | Follows short-link redirects off the main thread |
| `HomeActivity.java` | medium.com browser: paywall auto-route, draggable unlock button, pull-to-refresh, clipboard prompt |
| `ReaderActivity.java` | Freedium reader: font/theme, progress, TTS, save/offline, mirror failover, cleanups |
| `LibraryActivity.java` | Saved + history lists |
| `SettingsActivity.java` | Auto-route, logo/app-icon, mirror tools, maintenance, backup/restore |
| `Store.java` | Reading list + history persistence |
| `Prefs.java` | Preferences (mirror, auto-route, font, theme, logo) + backup JSON |
| `Branding.java` | Launch + reader loading animations |
| `LauncherIcons.java` | Switches the home-screen icon via activity-aliases |
| `QsTileService.java` | Quick Settings tile |
