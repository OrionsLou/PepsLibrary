# PepsLibrary

A personal Android app that wraps [Archive of Our Own](https://archiveofourown.org) so I can browse and sign in
in-app, download whole works as EPUBs, read them offline, and resume exactly where I left off.

> **Status:** early development. The app currently opens AO3 in an in-app browser where you can sign in and
> browse; downloading and reading aren't built yet. See [HANDOFF.md](HANDOFF.md) for the goals, constraints and
> build plan.

## What works today

- Signed release build that installs on a device by sideloading.
- AO3 loads in a `WebView` with the site's full search, filters and bookmarks. Signing in works and the session
  cookies persist.
- Navigation stays on AO3: links to other sites open in the system browser.
- A retry screen is shown when a page fails to load (for example, when offline).
- Tested on a physical Android device. On an emulator, AO3's Cloudflare bot check may block the page, so a real
  device on a normal connection is recommended for testing.

## Roadmap

Progress against the phased plan in [HANDOFF.md](HANDOFF.md):

**Phase 1: foundation and the core loop**
- [x] 1. App skeleton and signed release build
- [x] 2. WebView browsing and sign-in
- [ ] 3. Single-work EPUB download using the shared WebView cookies
- [ ] 4. Library (Room)
- [ ] 5. Reader with progress tracking (Readium)

**Phase 2: making it pleasant**
- [ ] 6. Injected download buttons
- [ ] 7. Download queue
- [ ] 8. "Already downloaded" badges

**Phase 3: polish**
- [ ] 9. WIP updates
- [ ] 10. Library management
- [ ] 11. Hardening

## Disclaimer

This is an unofficial, independent project. It is **not affiliated with, endorsed by, or sponsored by** Archive of
Our Own (AO3) or the Organization for Transformative Works (OTW). "Archive of Our Own" and "AO3" belong to their
respective owners.

It is built for **personal use** on my own device. By design it uses AO3's normal web pages and official download
links, makes **one request per work** (the EPUB bundles every chapter), runs downloads **sequentially with delays**,
and honors `Retry-After` on rate limiting. It does not bulk-crawl, scrape chapter by chapter, or download in
parallel, and contributions that add such behavior won't be accepted. If you use or fork this, you are responsible
for following [AO3's Terms of Service](https://archiveofourown.org/tos) and respecting the authors whose work you
download.

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
