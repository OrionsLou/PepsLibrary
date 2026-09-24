# PepsLibrary

A personal Android app that wraps [Archive of Our Own](https://archiveofourown.org) so I can browse and sign in
in-app, download whole works as EPUBs, read them offline, and resume exactly where I left off.

> **Status:** early development, but the core loop works: browse and sign in to AO3, download a work as an EPUB,
> read it offline, and pick up exactly where you left off. See [HANDOFF.md](HANDOFF.md) for the goals, constraints
> and build plan.

## What works today

- Signed release build that installs on a device by sideloading.
- AO3 loads in a `WebView` with the site's full search, filters and bookmarks. Signing in works and the session
  cookies persist.
- Navigation stays on AO3: links to other sites open in the system browser.
- A retry screen is shown when a page fails to load (for example, when offline).
- A sticky footer under the browser has back, forward and refresh buttons. Back and forward follow the browsing
  history and are disabled at either end; refresh is the way out of an intermittent Cloudflare error or bot-check
  page, and stays available on the error screen.
- On a work page, a **Download EPUB** bar appears above the footer and adds the work to a download queue rather
  than downloading it inline. The queue processes one work at a time, with a pause between downloads, and
  automatically retries a bot check, rate limit, server error or network failure (up to 3 attempts, honoring
  AO3's own `Retry-After` on a 429, backing off on its own otherwise) before giving up. The bar reflects the
  work's queue status live: queued, downloading, retrying with the attempt count, or failed. The queue survives
  the app closing and resumes on reopen, but doesn't keep running once the app is fully closed.
- The **Downloads** button in the footer opens everything currently queued or failed, for a view across all
  works rather than just the one you're on. A failed item can be retried or removed from there.
- Each download is recorded in a local Room database, using metadata read from the work page that was already
  fetched (no extra request): title, authors, summary, rating, warnings, categories, fandoms, relationships,
  characters, tags, language, word and chapter counts, dates, and AO3's `updated_at` for later update checks.
- The **Library** button in the footer opens a list of downloaded works (title, authors, fandoms, word and chapter
  counts with a Complete/In progress status, summary and download date). It slides over the browser, so the page
  and history you were on are kept. Downloading a work again replaces its entry.
- Tapping a work in the library opens it in a **reader** built on the [Readium](https://readium.org) toolkit
  (version 3.1.2): swipe or tap the page edges to turn pages, with the title and percent read in a top bar. The
  reading position is saved (as a Readium locator, in Room) when you leave, when the app is stopped, and a second
  after you stop turning pages, so even a killed app resumes at the same spot. Each library entry shows "Not
  started", "42% read" or "Finished". Links inside a book open in the browser, never inside the reader. A missing
  or damaged file gets a clear message instead of a crash.
- Tested on a physical Android device. On an emulator, AO3's Cloudflare bot check may block the page, so a real
  device on a normal connection is recommended for testing.

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
- [ ] 8. "Already downloaded" badges

**Phase 3: polish**
- [ ] 9. WIP updates
- [ ] 10. Library management
- [ ] 11. Hardening
- [ ] 12. UI polish and aesthetic tweaks

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

Requires Android Studio (bundled JDK 21) and the Android SDK (platform 35). The system JDK on PATH may be too new for
this Gradle/AGP combination, so point `JAVA_HOME` at Android Studio's JBR:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
```

`local.properties` (gitignored) must contain `sdk.dir`; Android Studio writes it automatically.

Output: `app/build/outputs/apk/release/app-release.apk`. Install with
`adb install -r app/build/outputs/apk/release/app-release.apk`.

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

If that file is missing, `assembleRelease` still builds but produces an **unsigned** APK.

**Back up `pepslibrary-release.jks` and `keystore.properties` somewhere safe.** Android only allows in-place updates
when the APK is signed with the same key; losing it means uninstalling the app and losing local data.

## License

[MIT](LICENSE)
