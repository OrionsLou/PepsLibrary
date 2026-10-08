# Pep's Library

A personal Android app (Kotlin, Jetpack Compose) that browses Archive of Our Own in a WebView, downloads whole works
as EPUBs, and reads them offline in Readium with saved positions. Sideloaded onto one phone; never published.

**Start every session by reading `HANDOFF.md`**, beginning with "Status at a glance". It holds the plan, what's done,
what's next, and the reasoning behind past decisions. This file only covers how to work in the repo.

## Commands

All from the repo root in PowerShell. The system JDK is too new for this Gradle/AGP, so always set:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

- Unit tests (JVM, no device): `.\gradlew.bat testDebugUnitTest`
- Lint: `.\gradlew.bat lintDebug`. CI (`.github/workflows/ci.yml`) runs lint and the unit tests on every PR and on
  pushes to `develop`; run both locally before pushing.
- Debug build: `.\gradlew.bat assembleDebug` → `app\build\outputs\apk\debug\app-debug.apk`
- Signed release build: `.\gradlew.bat assembleRelease` → `app\build\outputs\apk\release\app-release.apk`
  (`app-release-unsigned.apk` means the keystore properties weren't found; it won't install)
- adb: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"`
- Emulator (the only AVD): `& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone_API_35`, then
  wait for `adb shell getprop sys.boot_completed` to return 1

## Hard rules

- **Be gentle with AO3.** One download per work (the EPUB holds every chapter); never scrape chapter by chapter,
  download in parallel, or crawl. Downloads stay sequential with a pause and honor `Retry-After`.
- **All AO3-specific selectors, URLs and parsing live in `ao3/Ao3Adapter.kt`**, so a markup change is a one-file fix.
- **Never commit the keystore or credentials.** Signing reads `~/.pepslibrary/keystore.properties` (or the file named
  by `PEPSLIBRARY_KEYSTORE_PROPERTIES`), outside the repo.
- **Ask before adding a dependency.** For a single icon, draw it as an `ImageVector` (see `ui/PinIcons.kt`) rather
  than pulling in `material-icons-extended`.
- **Room: never destructive migration.** Bump `version` in `AppDatabase`, add a `Migration` in `data/Migrations.kt`,
  register it, and commit the schema Room exports to `app/schemas/`. A column with a SQL default needs a matching
  `@ColumnInfo(defaultValue = ...)`. Test the migration by installing over the emulator's existing database.
- Check AO3 or library behavior marked **[verify]** in `HANDOFF.md` against the real site or current docs before
  relying on it.

## Code conventions

- Put logic worth testing in pure Kotlin functions (as in `ui/LibrarySort.kt`, `ui/LibraryFilter.kt`,
  `ui/WorkDisplay.kt`, `epub/PositionUpdate.kt`) so it runs in JVM unit tests. `android.util.Log` and `org.json` don't
  work there; keep them out of tested code.
- Repositories depend on DAO interfaces and are tested with in-memory fakes. **Adding a DAO method means updating
  every fake** (the `WorkDao` fakes live in `LibraryRepositoryTest` and `DownloadQueueProcessorTest`).
- Only comment the non-obvious: a constraint, a workaround, a decision. Don't narrate what the code does.
- User-facing wording is plain and consistent across screens (e.g. "Last read" on both the sort and the card label).

## Workflow

- One change per branch (`feature/...` or `chore/...`), off an up-to-date `develop`. I push, open the PR and merge
  myself; don't push unless asked. If the branch is missing something that isn't merged yet, stop and say so.
- Keep commits small and focused: one logical change each, easy to review on its own. When a task is larger than
  that, split it into several PRs (each on its own branch, each leaving the build green) rather than one big one, and
  propose the split before starting.
- Each change: unit tests → debug build on the emulator (golden path and edge cases, screenshots, light and dark) →
  signed release APK for my phone test → commit when I say so.
- A change carrying a new feature bumps `appVersion` in `app/build.gradle.kts` (patch by default); `versionCode`
  follows from it.
- Commit messages: a short subject plus a line or two of why, ending with the `Co-Authored-By` trailer.
- Keep `HANDOFF.md` current as work lands: mark the step done with the date, record decisions and anything tried and
  dropped, update "Status at a glance". Keep the README's "What works today" and roadmap in step.

## Gotchas

- Screenshots from `adb shell screencap` are 1080×2400 but display scaled; multiply displayed coordinates by 1.2
  before tapping. Better: `adb shell uiautomator dump` and tap the centre of an element's `bounds` (works for Compose
  UI, not for the WebView's page content).
- In Git Bash, write device paths as `//sdcard/...` (one leading slash becomes a Windows path) and escape `>` in
  `adb shell input text`.
- "The process cannot access the file" on a `.dex` during a release build is a transient Windows file lock: rerun.
- If the emulator is running, adb needs `-s <serial>` to reach the phone.
- The emulator sometimes hits AO3's Cloudflare check; the phone is the reliable place for network-heavy tests.
- More emulator recipes (inspecting and planting the database, opening an AO3 page via DevTools, which test works are
  on the emulator, the GPU-driver crash) are under "Testing" in `HANDOFF.md` section 7.
