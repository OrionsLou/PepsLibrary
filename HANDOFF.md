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
- **WIP updates:** *(Checked 2026-09-24 against a real 22-chapter WIP download.)* AO3 builds EPUBs with Calibre, which names content files by their place in the book (`<Title>_split_000.xhtml` preface, `_001` title page, `_002` onward one per chapter, then the afterword), and every id in the package is a fresh sequential number. Appending chapters leaves earlier file names alone, but removing or inserting an earlier chapter silently renumbers every later file, so a Readium locator's href can resolve "successfully" to the wrong chapter. Step 9 handles this by comparing content hashes, not names. Still **[verify]**: that Calibre regenerates an *unchanged* chapter byte-for-byte on a later download. If it doesn't, the only effect is landing at the chapter's start instead of the exact spot; confirm by re-downloading a real WIP after its next update.

## 5. Feature plan and build order

The order gets a working read-offline loop early, then adds convenience on top. Steps 2 and 3 carry the highest technical risk (cookie handoff and EPUB fetching), so they come early.

### Phase 1: Foundation and the core loop

1. **App skeleton.** Compose project, signing keystore, release build that installs on my phone via adb or APK.
2. **WebView browsing and sign-in.** AO3 loads in a WebView and I can log in. Session cookies persist.
3. **Single-work EPUB download.** Given a work ID (hardcode one to start), fetch the EPUB with the shared WebView cookies and save it to app storage. This proves the cookie handoff.
3a. **Browser navigation footer.** *(Inserted after step 3.)* AO3 and Cloudflare intermittently serve error or bot-check pages. A sticky footer under the WebView with back, forward and refresh buttons lets me retry or navigate without leaving the app. Back and forward follow the WebView history and are disabled at either end.
4. **Library (Room).** Store per-work metadata (ID, title, author, file path, download date) and show a list of downloaded works.
5. **Reader with progress tracking.** Open the EPUB in Readium, save the locator on pause/close, and restore it on open. **This is the MVP:** download, read offline, resume.
5a. **Update dependencies and toolchain.** *(Inserted after step 5. Deferred by decision on 2026-09-22 — see note below.)* Bring the build toolchain and libraries up to date as one deliberate chore, on its own branch, separate from feature work. Motivation: the toolchain is about a year behind the libraries it now pulls in. Readium's newer AndroidX dependencies ship lint checks that crash this AGP's lint (worked around by disabling `NullSafeMutableLiveData` in `app/build.gradle.kts`), and every Readium release after 3.1.2 needs a newer Kotlin and compileSdk.

   > **Deferred, not abandoned.** This is a personal project in a public repo, not something with users or a CI pipeline depending on the newest toolchain. The lint workaround costs nothing real: it silences one check (`LiveData` null-safety) for a feature the app doesn't use, doesn't touch the APK, and is a two-line, well-commented exception. Revisit this step only if a concrete need arises (wanting a Readium release past 3.1.2, or hitting another incompatibility this workaround doesn't cover) rather than for currency's own sake.

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

6. **Download button, own chrome.** *(Revised 2026-09-22 — originally planned as JS-injected buttons on AO3's own pages; changed on reflection to keep AO3's rendered page completely untouched.)* A Download/Save bar appears above the footer only when the current page is a work page (same `workId` detection as before), with the existing Download EPUB button and status text. No DOM injection, no JS bridge, no `addJavascriptInterface`. Only covers the single work page you're viewing, same as before this step; a list-page picker (downloading from search/tag/bookmark pages without opening each work) was considered and deferred, not built. Progress display beyond the status text is left to Phase 3, alongside cancel/abort (see the Phase 3 notes above).
7. **Download queue.** *(Done 2026-09-23, in four chunks: the Room table, the repository, the processor loop, and the UI.)* Sequential downloads with a delay, `Retry-After` handling on 429s, and queue state persisted in Room so it survives app close (queue state, not active execution: the processor is a process-lifetime coroutine, not WorkManager or a foreground service — see the decision recorded under "Suggested architecture" — so downloads pause if the app is fully closed and resume on reopen, rather than continuing unattended). Automatic retry up to 3 attempts for a retryable failure kind (bot check, rate limit, server error, network), with AO3's own `Retry-After` honored exactly and our own backoff (30s, doubling, capped at 5 minutes) otherwise; a non-retryable failure, or a retryable one past the cap, lands on FAILED for manual retry. Progress beyond status text (queued / downloading / retrying with attempt count / failed) stays deferred to Phase 3, per the existing notes. A Downloads screen (footer button) shows everything queued or failed at once, with Retry and Remove; a work's own page shows just its own entry. Queue rows show "Work `<id>`" rather than a title, since a title isn't known until the work page is actually fetched — deferred as a Phase 3 polish candidate rather than adding a schema column for a cosmetic field.
8. **"Already downloaded" badges.** *(Split by decision on 2026-09-24.)* Inject a marker on works already in the
   library.

   > **Split, not deferred.** The data-layer half landed now: `WorkDao.observe(workId)` and
   > `LibraryRepository.isDownloaded(workId): Flow<Boolean>`, the same keyed-by-workId pattern
   > `DownloadQueueRepository.entries` already uses, so a screen can react live to a work entering or leaving the
   > library. The visible half — a badge in AO3 search/browse results, or changing the Download bar's own
   > appearance/text on a work's page — is deferred to step 12 (UI polish) rather than built now, since it's a
   > presentation decision better made alongside the rest of that pass, not a one-off ahead of it.

### Phase 3: Polish

9. **WIP updates.** *(Done 2026-09-26.)* Re-download the EPUB to pick up new chapters while keeping the reading position. When a download replaces an existing copy, the downloader reads the old and new reading order (each file's zip path, SHA-256 and first heading) before and after the swap, and `reconcilePosition` decides what the saved position becomes:
   - the chapter is byte-identical at the same path → kept exactly (new chapters appended, or edits to *other* chapters);
   - identical but under a different file name (an earlier chapter was added or removed) → same spot, new href;
   - the chapter's content changed → start of that chapter, found by its heading, with a one-time reader banner;
   - the chapter can't be found → start of the work, with a banner.

   Chosen over text-anchored bookmarks (store the sentence at the top of the page and search for it after an update), which were considered and dropped as a much larger lift: Readium selection plumbing, sentence segmentation, text normalization and duplicate-match handling, for little gain once chapter navigation (step 9a) covers the remaining gaps by hand. The banner is a `notice` column on `reading_progress` (migration 3→4); any normal save replaces the row without it, so it shows once. Verified on the emulator with a real re-download of a real WIP against planted "older" copies: an edited current chapter (→ chapter start, banner) and an inserted earlier chapter (→ exact spot in the renumbered file).
9a. **Chapter navigation and position seeking.** *(Added and done 2026-09-26.)* Built as a bottom bar over the page (so it never re-paginates): a slider stepping through Readium positions (about a page each) with a live "chapter · percent" preview that jumps on release, and a Chapters button opening a bottom sheet scrolled to the current chapter. Shown when a work opens; a tap in the middle of the page toggles it (edge taps still turn pages), and its down arrow or dragging it down closes it. Swipe-up-to-open and a floating corner button were tried and dropped: the swipe was unreliable in practice and the button covered text. Original plan: in the reader, a chapter list from Readium's `publication.tableOfContents` (it will include Preface and Afterword) that jumps with `navigator.go(link)`, and a way to seek to a point in the work. Seek by Readium's `publication.positions()` (fixed ~1,024-character slices) or a percentage rather than rendered page numbers, which shift with font size and rotation. Position numbers are recomputed after an update, which is fine for manual navigation.
10. **Library management.** Delete works, sort/filter, resume-reading shortcut, storage usage. *(Planned 2026-09-27 as one PR per feature, all from data already stored per work; no schema change or extra AO3 requests.)*
    1. **Delete** *(done)*: trash icon on each library card, with a confirmation dialog. Removes the row, its reading position (foreign key cascade) and the EPUB file, and first drops any queued or failed re-download so it can't bring the work back. Disabled while that work is actively downloading, as on the Downloads screen.
    2. **Sort by title** *(done)*: a "Sort" menu under the library's title bar, with Date downloaded (newest first, the default and the previous fixed order) and Title. Titles compare case- and accent-insensitively, word by word (a space sorts before letters), and a leading "The"/"A" is not skipped, matching AO3. Uses an explicit sort key rather than `java.text.Collator`, whose treatment of spaces differs between the JVM unit tests and Android. The choice survives closing and reopening the library but not an app restart. Since date downloaded is now an option already, PR 4 is reduced to adding oldest-first.
    3. **Sort by author** *(done)*: an Author option in the Sort menu. By the first listed author (AO3's byline order), compared like titles (case- and accent-insensitive); one author's works by title; works with no known author last. "Anonymous" works group together.
    4. **Sort by date downloaded, and reversible sorts** *(done)*: every sort can go either way. Each option starts in its natural direction (Date downloaded newest first, Title and Author A–Z); choosing the already-selected option again reverses it, and the menu shows which way that will go. The button reads e.g. "Sort: Title, Z–A". Reversing flips only the chosen key: ties still fall back to newest download first, and works with no known author stay last in an author sort either way. Date downloaded is the *latest* download, since a WIP re-download updates it; the first-download date isn't stored.
    5. **Filter by author** *(done)*: a "Filter" button beside Sort opens a bottom sheet with one checkbox per author (alphabetical, with a work count), plus "No author listed" when any work lacks one. Several authors selected show works by any of them. The button reads "Filter (n)", the header "2 of 4 works", and an empty result offers "Clear filters"; the sheet has "Clear all". A co-authored work appears under each author; one person's different pseuds show as separate authors. A selected author whose works were all deleted stays listed (count 0) so it can be unchecked. The shared filter state (`LibraryFilter`: one set per kind, any-of within a kind, all kinds combined) and the sheet's section layout are what PRs 6 and 7 extend; the filter persists like the sort (across closing the library, not an app restart).
    6. **Filter by completion status** *(done)*: a Status section at the top of the filter sheet with Completed (all chapters out), Work in progress (chapters still to come, or an unknown total like "3/?"), and **Other (chapter count unknown)** for works whose chapter counts couldn't be read at download time, so they can be filtered explicitly. All three are always listed, with counts, even at 0. The rule lives in one place (`completionStatus`), shared with the "Complete"/"In progress" label on each library card. Combines with the author filter: e.g. Completed plus one author shows only that author's completed works.
    7. **Filter by fandom** *(done)*: a Fandom section between Status and Author, one checkbox per fandom (alphabetical, with counts), plus "No fandom listed" when any work lacks one. A crossover appears under each of its fandoms; fandom names are AO3's exact wording, so e.g. book and movie fandoms are separate (select both to see either). Combines with status and author like the other kinds. Author and fandom options share one helper (`namedOptions`).

    8. **Sort by last read, the new default** *(added and done 2026-09-27)*: "Last read" is the first Sort option and the default, so works being read sit at the top. Reversible like the others (most / least recent first); works never opened stay at the end either way, ordered newest download first. Backed by a new `works.lastOpenedAt` column (migration 4→5), set when the reader successfully opens a book (not on a failed open) and kept across re-downloads. The migration backfills it from each work's last saved reading position, so existing reads rank sensibly straight away. The library now also scrolls to the top when the first work changes (not just when the sort or filter does); otherwise the work just read moved above the visible list and was hidden on return.

    9. **"Last read" label on library cards** *(done)*: a small "Last read Sep 27, 2026" line above "Downloaded", from `lastOpenedAt`; left off for works never opened. A work opened but not read past the first page shows both "Not started" and a "Last read" date; left as is for now (candidate for step 12 polish). The sort option was renamed from "Last opened" to "Last read" to match the label; code names (`LibrarySort.OPENED`, `lastOpenedAt`) are unchanged, to avoid a migration just for a rename.
    10. **Pinned works** *(done)*: a pin button beside the trash icon on each card (outlined and upright when unpinned; filled in the accent colour and tilted 45°, with a short animation, when pinned). Pinned works stay at the top under every sort and direction, ordered among themselves by the chosen sort. A "Pinned only" choice at the top of the filter sheet combines with the other filters. Stored as `works.pinned` (migration 5→6, everything starts unpinned) and kept across re-downloads. The pin icons are Material's "push pin" path data drawn in `PinIcons.kt`, since the icon is only in `material-icons-extended`, a large dependency for two icons.

    11. **Storage usage** *(done)*: the library header shows the total size of the works listed ("4 works · 463 KB", or "2 of 4 works · 37 KB" while filtered, so it always matches the list), and each card's "Downloaded" line shows that work's size. From the stored `fileSizeBytes`, no disk scan. Decimal units (1 KB = 1,000 bytes), as Android's storage settings use.

    **Step 10 is done.** The resume-reading shortcut from the original line wasn't built as its own feature: with "Last read" as the default sort, the work being read is the first card, one tap from the library. Revisit (e.g. a "Continue reading" footer button) only if that proves too slow in practice.

    Filters of different kinds combine (e.g. a fandom plus WIP only); design the filter state for that from PR 5 onward.
11. **Hardening.** Cookies in encrypted storage, sensible error and offline states, GitHub Releases plus Obtainium for updates.
12. **UI polish and aesthetic tweaks.** *(Added after hardening.)* A visual pass over the whole app once the features are in place: consistent theming (including dark mode), spacing and typography, app icon and splash, empty and loading states, and replacing the temporary scaffolding UI (such as the Download EPUB bar) with a finished design. Also carries the visible half of step 8: a badge on already-downloaded works in AO3 search/browse results and/or a changed Download bar appearance on a work's own page, using `LibraryRepository.isDownloaded` (already built).

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
4. **Make deleting a work feel less abrupt** *(fits step 12, UI polish)*. Today the card vanishes instantly and the list jumps. Wanted: visible feedback on removal, e.g. the card fading or sliding out while the rest of the list closes the gap smoothly (`LazyColumn` item animations), or a brief "Deleted" message.

## 6. Suggested architecture

- **Layers:** UI (Compose screens), data (Room, download storage), network (OkHttp with WebView cookie jar), and an isolated AO3 "adapter" layer holding all selectors, injected JS, and parsing.
- **Screens:** Browse (WebView), Library, Reader, and a Downloads/queue view (Phase 2).
- **Cookie jar:** a small OkHttp `CookieJar` that reads from Android's `CookieManager` for the AO3 domain.
- **Downloads:** a persisted queue (Room) processed by a single worker scoped to the app process — not WorkManager, not a foreground service. See the decision under step 7's open questions below.

## 7. Working agreements for the Claude Code session

- Start with **Phase 1, step 1**, and work in small, verifiable steps. Confirm each step builds and runs on a device or emulator before moving on.
- Before writing code that depends on a library or on AO3 behavior marked **[verify]**, check current documentation or test against the real site, rather than assuming.
- Keep AO3-specific selectors and injected JS in one file.
- Do not add chapter-by-chapter scraping, parallel downloads, or any bulk-crawling behavior.
- Do not commit the keystore or any credentials. Add them to `.gitignore` and document how they are supplied to the build.
- Ask before adding dependencies beyond the stack above.
- **Testing on my phone.** Once a change works on the emulator, build a signed release APK and hand it over for a phone test (a release build, not debug: it's signed with the real key, so it installs over my existing copy and keeps my library and reading positions). From the repo root in PowerShell:

  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  .\gradlew.bat assembleRelease
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "C:\Repos\PepsLibrary\app\build\outputs\apk\release\app-release.apk"
  ```

  The output must be `app-release.apk`; `app-release-unsigned.apk` means the keystore properties weren't found (see the README's release-signing section) and it won't install. The phone needs USB debugging on and must show up in `adb devices`. If the emulator is running too, adb needs a target: add `-s <serial>` from `adb devices`, or close the emulator first.

## 8. Open questions

- Minimum Android version to target?
- Where should downloaded EPUBs live (app-private storage versus a user-visible folder)?
- ~~WorkManager versus a simpler foreground service for the download queue?~~ **Decided 2026-09-23:** neither. A
  process-lifetime coroutine (the same pattern as `AppScope`, already used for saving reading position) processes
  a Room-backed queue. Queue *state* survives the app closing and resumes on reopen; downloads do not continue
  unattended once the process is fully killed (that tradeoff — option (a), "resumable," over option (b),
  "unattended" — was explicitly chosen as good enough for a personal app). WorkManager's deferred/constrained
  scheduling doesn't suit the queue's own tight retry/delay timing (`Retry-After`, sequential-with-a-pause), and a
  foreground service's persistent notification is an unwarranted UX cost for work that stays in-app. Revisit only
  if "keeps downloading while fully closed" becomes an actual want; the Room-backed state doesn't need to change
  either way.
- Should the library also store AO3 metadata (tags, summary, word count) at download time for sorting and filtering? This is deferred to Phase 3 unless wanted earlier.
