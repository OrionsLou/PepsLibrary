# Project Handoff: Personal AO3 Offline Reader (Android)

> Context file for starting a Claude Code session. Everything here comes from a planning discussion. Items marked **[verify]** are assumptions that should be confirmed against current docs or real behavior before relying on them.

## 1. Goal

A personal Android app that wraps https://archiveofourown.org (AO3) so I can:

1. Browse AO3 in-app while online, with the site's full search and filter features.
2. Sign in to my AO3 account.
3. Select works and download the entire work to my phone.
4. Read downloaded works offline.
5. Track where I last left off and resume exactly there when reopening.

**Scope:** strictly personal use, installed on my own device by sideloading. Not for the Play Store and not for distribution.

## 2. Key facts and decisions

- **No official AO3 API.** Everything works through the site's normal web pages.
- **No backend.** All logic runs on-device.
- **One request per work.** AO3 offers official download links (EPUB, MOBI, PDF, HTML). The EPUB bundles all chapters, so downloading a 60-chapter work costs one request. **Do not scrape chapter by chapter.**
- **Browsing uses a WebView, not a native search UI.** AO3 itself provides search, filters, tag autocomplete, and bookmarks. Do not reimplement these.
- **Sign-in happens in the WebView.** Its `CookieManager` cookies are reused by the OkHttp client for downloads, and this avoids hand-rolling CSRF login. It also handles bot checks better than raw HTTP.
- **Reading uses an existing EPUB toolkit (Readium).** Do not build an EPUB renderer. Save Readium's position locator in Room on pause/close and restore it on open.
- **Distribution is sideloading only.** Adb or APK install, optionally GitHub Releases plus Obtainium for updates.

## 3. Tech stack

| Concern | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (WebView hosted via `AndroidView`) |
| HTTP | OkHttp (with cookies from the WebView `CookieManager`) |
| HTML parsing | Jsoup (used sparingly, e.g. to find the EPUB link on a work page) |
| Local DB | Room |
| EPUB reader | Readium Kotlin toolkit **3.1.2**, pinned. *Verified 2026-09-21:* the newest release this project's toolchain accepts (compileSdk 35, AGP 8.7.3, Kotlin 2.0.21; Kotlin reads metadata one minor version ahead). 3.2.0 and later need newer Kotlin and compileSdk, i.e. an AGP, Android Studio and SDK upgrade. Setup: `readium-shared`, `readium-streamer` and `readium-navigator` from Maven Central, core library desugaring, and `androidx.fragment` declared explicitly (Readium lists it runtime-only). No PDF adapter. |
| Secure storage | Android Keystore / EncryptedSharedPreferences equivalent (Phase 3) |
| Build / release | Gradle, signed release APK |

Fallback if Kotlin is not wanted: Capacitor or React Native, but EPUB rendering options are less mature there. Kotlin is the default.

## 4. Constraints and gotchas

- **Rate limiting:** AO3 returns HTTP 429 with a `Retry-After` header when throttled. Downloads must be sequential with a few seconds' delay between them, and must honor `Retry-After` before retrying. Human-speed browsing in the WebView is not a concern.
- **Adult content interstitial:** add `view_adult=true` when fetching work pages/downloads **[verify behavior]**.
- **Finding the download URL:** parse the EPUB link from the work page rather than constructing it by hand **[verify the current markup]**.
- **Scraping fragility:** AO3 markup can change. Keep all injected JS and CSS selectors (e.g. `li.work` blurbs, where the work ID is in the element id) in **one dedicated file** so breakages are easy to fix.
- **Signing key:** the release keystore must be kept safe and reused. Android only allows updates over an existing install when signed with the same key, and losing it means uninstalling and losing local data.
- **Use a release build for daily use.** Debug builds are slower and signed with a throwaway key.
- **Terms of Service:** review AO3's ToS, keep this personal-use, and do not bulk-crawl.
- **Android sideloading policy:** Google has been rolling out developer-verification requirements. Installing my own builds on my own device is expected to remain supported **[verify current rules]**.
- **WIP updates:** re-downloading the EPUB to pick up new chapters should keep the saved reading position, on the assumption that Readium locators are chapter-based and remain valid when chapters are appended **[verify this with a real work-in-progress fic]**.

## 5. Feature plan and build order

The order gets a working read-offline loop early, then adds convenience on top. Steps 2 and 3 carry the highest technical risk (cookie handoff and EPUB fetching), so they come early.

### Phase 1: Foundation and the core loop

1. **App skeleton.** Compose project, signing keystore, release build that installs on my phone via adb or APK.
2. **WebView browsing and sign-in.** AO3 loads in a WebView and I can log in. Session cookies persist.
3. **Single-work EPUB download.** Given a work ID (hardcode one to start), fetch the EPUB with the shared WebView cookies and save it to app storage. This proves the cookie handoff.
3a. **Browser navigation footer.** *(Inserted after step 3.)* AO3 and Cloudflare intermittently serve error or bot-check pages. A sticky footer under the WebView with back, forward and refresh buttons lets me retry or navigate without leaving the app. Back and forward follow the WebView history and are disabled at either end.
4. **Library (Room).** Store per-work metadata (ID, title, author, file path, download date) and show a list of downloaded works.
5. **Reader with progress tracking.** Open the EPUB in Readium, save the locator on pause/close, and restore it on open. **This is the MVP:** download, read offline, resume.
5a. **Update dependencies and toolchain.** *(Inserted after step 5.)* Bring the build toolchain and libraries up to date as one deliberate chore, on its own branch, separate from feature work. Motivation: the toolchain is about a year behind the libraries it now pulls in. Readium's newer AndroidX dependencies ship lint checks that crash this AGP's lint (worked around by disabling `NullSafeMutableLiveData` in `app/build.gradle.kts`), and every Readium release after 3.1.2 needs a newer Kotlin and compileSdk.
   - **Scope, roughly in this order** (verify each version pairing against its compatibility table instead of assuming; do one layer at a time, and build, run the unit tests and try the app on the emulator before the next):
     1. **Android Studio** (currently Ladybug 2024.2, matched to AGP 8.7). Updating it is a manual step for me; a newer AGP likely needs a newer Studio.
     2. **Gradle wrapper and Android Gradle Plugin** (currently Gradle 8.9, AGP 8.7.3). Find the AGP release that fixes the lint crash and confirm its required Gradle version. Generate the wrapper in an empty directory, because `gradle wrapper` evaluates the build files and fails on a version mismatch.
     3. **Kotlin, KSP and the Compose compiler plugin** (currently 2.0.21 and KSP 2.0.21-1.0.28). These move together, so pick the KSP release that matches the Kotlin version.
     4. **compileSdk and targetSdk** (currently 35), including installing the matching SDK platform.
     5. **Libraries:** the Compose BOM (2024.10.01), core-ktx, activity-compose, Room (2.6.1; the schema and migration must keep working), OkHttp (4.12.0; 5.x is a bigger change), Jsoup, `desugar_jdk_libs`, AndroidX Fragment and JUnit.
     6. **Readium** (pinned at 3.1.2). Decide how far to go. The 3.2 to 3.4 releases need progressively newer Kotlin, compileSdk and Gradle, and 3.4.0 raises minSdk to 24 and changes PDFium defaults (irrelevant here, as there is no PDF adapter).
   - **Also in this step:** remove the lint workaround once the crash is gone, and check whether R8 shrinking can go in (this is a good moment to look at keep rules for Readium, which currently make the release APK about 13 MB).
   - **Done when:** the workaround is removed, all unit tests pass, the release build is signed and installs, the Room migration path still works on an existing database, and the reader and downloads pass a phone test. Record the versions chosen and why in this file, as done for Readium above.

### Phase 2: Making it pleasant

6. **Injected download buttons.** In `onPageFinished`, inject JS that adds a download button to each work blurb on list pages and to the work page. Buttons call Kotlin through `addJavascriptInterface` with the work ID. Selectors live in the single selectors file.
7. **Download queue.** Sequential downloads with a delay, `Retry-After` handling on 429s, and queue state persisted in Room so it survives app close. Show progress and failures.
8. **"Already downloaded" badges.** Inject a marker on works already in the library.

### Phase 3: Polish

9. **WIP updates.** Re-download the EPUB to pick up new chapters while keeping the reading position.
10. **Library management.** Delete works, sort/filter, resume-reading shortcut, storage usage.
11. **Hardening.** Cookies in encrypted storage, sensible error and offline states, GitHub Releases plus Obtainium for updates.
12. **UI polish and aesthetic tweaks.** *(Added after hardening.)* A visual pass over the whole app once the features are in place: consistent theming (including dark mode), spacing and typography, app icon and splash, empty and loading states, and replacing the temporary scaffolding UI (such as the Download EPUB bar) with a finished design.

### Notes to revisit in Phase 3

Observations from hands-on testing of the Phase 1 build, to fold into steps 11 and 12 (or into step 7 if the download queue gets there first, since it already promises progress and failure display).

1. **Cancel or abort a download in progress** *(fits step 11, hardening; overlaps step 7)*. Right after tapping Download EPUB the button stays grey until the request times out, and there is no way out. Wanted: a Cancel control while a download runs, and a shorter, more honest failure path when the network stalls.
   - Today's client allows a 20 s connect and 60 s read timeout per request, and a download makes two requests, so a stall can take well over a minute to surface.
   - `EpubDownloader.download` is blocking. To cancel it, keep a handle on the in-flight OkHttp `Call` and call `cancel()` (or wrap the call so coroutine cancellation does it), and make sure a cancelled download leaves no `.part` file behind. It already cleans up on failure; add a test for cancellation.
   - Decide what timeouts feel right. AO3 can take a while to build an EPUB for a long work, so a short read timeout would cause false failures.
2. **Make page loading obvious** *(fits step 12, UI polish)*. The thin progress bar at the top of the browser is easy to miss. Wanted: a clearer indicator that a page is loading, for example a more prominent bar or spinner, and the refresh button reflecting the loading state (a stop control while loading).
3. **Show download progress** *(fits step 12, and step 7 for the queue)*. Wanted: visible progress while a work downloads instead of only a status line at the end.
   - Read `Content-Length` from the EPUB response for a determinate bar, and fall back to an indeterminate one when it is absent.
   - There may be a quiet gap before the first byte while AO3 builds the file, so show a "waiting for AO3" state distinct from "downloading", and distinguish the work-page load from the file transfer.
   - Report progress from the downloader through a callback so the queue (step 7) and the UI can share it.

## 6. Suggested architecture

- **Layers:** UI (Compose screens), data (Room, download storage), network (OkHttp with WebView cookie jar), and an isolated AO3 "adapter" layer holding all selectors, injected JS, and parsing.
- **Screens:** Browse (WebView), Library, Reader, and a Downloads/queue view (Phase 2).
- **Cookie jar:** a small OkHttp `CookieJar` that reads from Android's `CookieManager` for the AO3 domain.
- **Downloads:** run as a persisted queue (Room) processed by a single worker, so they can survive process death. WorkManager is a candidate for this **[decide]**.

## 7. Working agreements for the Claude Code session

- Start with **Phase 1, step 1**, and work in small, verifiable steps. Confirm each step builds and runs on a device or emulator before moving on.
- Before writing code that depends on a library or on AO3 behavior marked **[verify]**, check current documentation or test against the real site, rather than assuming.
- Keep AO3-specific selectors and injected JS in one file.
- Do not add chapter-by-chapter scraping, parallel downloads, or any bulk-crawling behavior.
- Do not commit the keystore or any credentials. Add them to `.gitignore` and document how they are supplied to the build.
- Ask before adding dependencies beyond the stack above.

## 8. Open questions

- Minimum Android version to target?
- Where should downloaded EPUBs live (app-private storage versus a user-visible folder)?
- WorkManager versus a simpler foreground service for the download queue?
- Should the library also store AO3 metadata (tags, summary, word count) at download time for sorting and filtering? This is deferred to Phase 3 unless wanted earlier.
