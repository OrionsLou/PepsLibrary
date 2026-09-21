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
| EPUB reader | Readium Kotlin toolkit **[verify current version and setup]** |
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

### Phase 2: Making it pleasant

6. **Injected download buttons.** In `onPageFinished`, inject JS that adds a download button to each work blurb on list pages and to the work page. Buttons call Kotlin through `addJavascriptInterface` with the work ID. Selectors live in the single selectors file.
7. **Download queue.** Sequential downloads with a delay, `Retry-After` handling on 429s, and queue state persisted in Room so it survives app close. Show progress and failures.
8. **"Already downloaded" badges.** Inject a marker on works already in the library.

### Phase 3: Polish

9. **WIP updates.** Re-download the EPUB to pick up new chapters while keeping the reading position.
10. **Library management.** Delete works, sort/filter, resume-reading shortcut, storage usage.
11. **Hardening.** Cookies in encrypted storage, sensible error and offline states, GitHub Releases plus Obtainium for updates.
12. **UI polish and aesthetic tweaks.** *(Added after hardening.)* A visual pass over the whole app once the features are in place: consistent theming (including dark mode), spacing and typography, app icon and splash, empty and loading states, and replacing the temporary scaffolding UI (such as the Download EPUB bar) with a finished design.

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
