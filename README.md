# PepsLibrary

A personal Android app that wraps [Archive of Our Own](https://archiveofourown.org) so I can browse and sign in
in-app, download whole works as EPUBs, read them offline, and resume exactly where I left off.

> **Status:** version 1.0.3 (tag `v1.0.3`). The planned app is complete: browse and sign in to AO3, download works
> as EPUBs, read them offline and pick up exactly where you left off, with a managed library and a finished look.
> Next up is reading stats (Phase 4). See [HANDOFF.md](HANDOFF.md) for the goals, constraints and build plan.

## What works today

**Browsing AO3**
- AO3 in an in-app browser, with the site's own search, filters and bookmarks. Sign-in persists.
- Only AO3 opens in the app; other sites open in the phone's browser. AO3's pages are never modified.
- A footer with back, forward and refresh (stop while loading), the download queue and the library.
- Clear loading, error and offline states; an offline page reloads by itself once the connection is back.

**Downloading**
- On a work page, **Save** queues the whole work as one EPUB (one download per work, however many chapters).
- Downloads run one at a time with a pause between them, retry automatically (honoring AO3's `Retry-After`), wait
  out an offline spell, and show their progress. Any of them can be cancelled.
- For a work you already have, the bar offers **Read**, and **Update** to fetch new chapters.
- The **Download queue** screen lists everything queued or failed, by title, with Retry and Remove.

**Library**
- Pep's Library lists your works with their details, reading progress and size.
- Sort by last read, date downloaded, title or author (either direction), filter by status, fandom, author or
  pinned, and pin favorites to the top. The choices are remembered.
- Delete a work, with its file and reading position.

**Reading**
- An offline EPUB reader ([Readium](https://readium.org) 3.1.2) that resumes exactly where you left off.
- A bottom bar with a page slider and a chapter list, and Light, Sepia and Dark reading themes.
- The screen stays on while you read: always (the default), for a set number of minutes after your last page turn,
  or not at all, chosen in **Settings**.
- Updating a work in progress keeps your place, even if earlier chapters were added or removed.
- AO3 links in a book open in the app's browser, and Back returns to the book.

**Look and feel**
- A grey-tabby theme in light and dark (following the phone, or set in **Settings**), with Pep as the app icon.
- Short, quiet motion: screens slide in, and deleted or pinned works move smoothly.
- And a **Meow** button in Settings.

Built and tested on a physical Android phone, installed by sideloading. On an emulator, AO3's Cloudflare check can
block pages, so a real device on a normal connection is best for testing.

## Changes since 1.0.0

- **1.0.1:** pinning or unpinning a work in the library slides it smoothly to its new place, instead of the list
  snapping into the new order. 1.0.1 has no tag of its own; the fix is in `v1.0.2`.
- **1.0.2:** AO3 links inside a book (the preface's links to the work, its tags, series and author) open in the
  app's own browser, where you're signed in, instead of the phone's browser. Back on that page returns to the book
  at the same spot. Other links still open in the phone's browser.
- **1.0.3:** the screen stays on while you read. A new **Reader** section in Settings chooses Always (the default),
  Timed (for a number of whole minutes after your last page turn or tap) or Off.

## Roadmap

Progress against the phased plan in [HANDOFF.md](HANDOFF.md):

**Phase 1: foundation and the core loop**
- [x] 1. App skeleton and signed release build
- [x] 2. WebView browsing and sign-in
- [x] 3. Single-work EPUB download using the shared WebView cookies
- [x] 3a. Browser navigation footer (back, forward, refresh), added to work around intermittent Cloudflare errors
- [x] 4. Library (Room)
- [x] 5. Reader with progress tracking (Readium)
- [ ] 5a. Update dependencies and toolchain (Android Studio, AGP, Gradle, Kotlin, SDK, libraries)

**Phase 2: making it pleasant**
- [x] 6. Download button in the app's own chrome (no DOM injection into AO3's page)
- [x] 7. Download queue
- [x] 8. "Already downloaded" state: the Download bar says "In your library", with Read and Update (badges on AO3's
  own pages were ruled out, to leave AO3's pages untouched)

**Phase 3: polish**
- [x] 9. WIP updates (keeps your place across re-downloads)
- [x] 9a. Chapter navigation and position seeking in the reader
- [x] 10. Library management (delete, sort, filter, pinning, last read, storage usage)
- [x] 11. Hardening (private session data, cancelling downloads, offline states, saved sort and filter; installs
  stay on adb, with no published releases)
- [x] 12. UI polish and aesthetic tweaks (tabby theme with dark mode, icon and opening animation, reading themes,
  Settings, the shelf-style library, clearer loading and download progress, screen transitions)

**Phase 4: reading stats** (planned)
- [ ] 13. Reading stats: reading time and words read today, this week and this month; works finished; most-read
  authors and fandoms; trends such as a 30-day chart, streaks and time of day. Recorded on the device only.

## Disclaimer

This is an unofficial, independent project. It is **not affiliated with, endorsed by, or sponsored by** Archive of
Our Own (AO3) or the Organization for Transformative Works (OTW). "Archive of Our Own" and "AO3" belong to their
respective owners.

It is built for **personal use** on my own device. By design it uses AO3's normal web pages and official download
links, makes **one download per work** (the EPUB bundles every chapter, so it takes a single work-page load to find the
link plus one file request), runs downloads **sequentially with delays**,
and honors `Retry-After` on rate limiting. It does not bulk-crawl, scrape chapter by chapter, or download in
parallel, and contributions that add such behavior won't be accepted. If you use or fork this, you are responsible
for following [AO3's Terms of Service](https://archiveofourown.org/tos) and respecting the authors whose work you
download.

Downloaded works are not reviewed, scanned, or processed by the app, an AI agent, or any third party — they are
written to the app's private on-device storage exactly as fetched from AO3.

## How this was built

This project is built with an AI coding agent ([Claude Code](https://claude.com/claude-code)) working from a
written plan rather than ad-hoc prompting:

1. A planning discussion produced [HANDOFF.md](HANDOFF.md): goals, decisions, constraints, open questions, and a
   phased build order, with unverified assumptions explicitly marked **[verify]**.
2. The agent reviews the plan, flags gaps and risks, and resolves open questions before writing code.
3. Work proceeds in small, verifiable steps. Each step is built and run (release build on an emulator or device)
   before moving to the next. The highest-risk steps are done first.
4. Standing rules from the handoff apply throughout: no secrets in the repo, ask before adding dependencies, keep
   AO3-specific selectors in one file, and verify library and site behavior instead of assuming.

Commits made with the agent's help carry a `Co-Authored-By` trailer, so the history shows what it touched.

## Building

**There are no prebuilt APKs or GitHub Releases.** This is a personal project, so it's distributed as source only:
I build a signed release APK and install it on my own phone over adb. If you want to use it, build it yourself and
sign it with your own key (see [Release signing](#release-signing)). That way your install trusts only your key, not
mine.

Requires Android Studio (bundled JDK 21) and the Android SDK (platform 35). To build your own copy from scratch:

1. Install Android Studio, open this project once so it installs SDK platform 35 and writes `local.properties`, then
   close it.
2. Create a signing key and `keystore.properties` as described under [Release signing](#release-signing).
3. Build the release APK as below, and check the output is `app-release.apk`, not `app-release-unsigned.apk`.
4. On the phone, turn on Developer options and USB debugging, connect it, check it shows in `adb devices`, and
   install with `adb install -r`.

The system JDK on PATH may be too new for this Gradle/AGP combination, so point `JAVA_HOME` at Android Studio's JBR:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
```

`local.properties` (gitignored) must contain `sdk.dir`; Android Studio writes it automatically.

Output: `app/build/outputs/apk/release/app-release.apk`. Install with
`adb install -r app/build/outputs/apk/release/app-release.apk`.

The version is set in one place, `appVersion` in `app/build.gradle.kts`; `versionCode` is worked out from it. Android
won't install a lower `versionCode` over a higher one, so bump `appVersion` for each new build you put on a device.

## Testing

Unit tests are plain JUnit 4 tests under `app/src/test/` and run on the JVM, with no device or emulator needed.
Use the same `JAVA_HOME` as above:

```powershell
.\gradlew.bat testDebugUnitTest
```

`.\gradlew.bat test` runs the debug and release variants. HTML results are written to
`app/build/reports/tests/testDebugUnitTest/index.html`.

## Release signing

The keystore and its passwords are **never committed**, and are kept outside the repo (and outside OneDrive).
`app/build.gradle.kts` reads them from `~/.pepslibrary/keystore.properties`, or from the file named by the
`PEPSLIBRARY_KEYSTORE_PROPERTIES` environment variable:

```properties
storeFile=C:/Users/<you>/.pepslibrary/pepslibrary-release.jks
storePassword=...
keyAlias=pepslibrary
keyPassword=...
```

If that file is missing, `assembleRelease` still builds but produces an **unsigned** APK, which won't install.

To create your own keystore, make the folder, then use the `keytool` bundled with Android Studio's JBR, and fill in
the properties file above with the passwords you chose:

```powershell
New-Item -ItemType Directory -Force "$HOME\.pepslibrary"
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore "$HOME\.pepslibrary\pepslibrary-release.jks" -alias pepslibrary -keyalg RSA -keysize 4096 -validity 10000
```

**Back up `pepslibrary-release.jks` and `keystore.properties` somewhere safe.** Android only allows in-place updates
when the APK is signed with the same key; losing it means uninstalling the app and losing local data.

## License

[MIT](LICENSE)
