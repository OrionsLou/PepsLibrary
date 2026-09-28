package app.pepslibrary.ui

import android.webkit.WebSettings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.pepslibrary.data.AppDatabase
import app.pepslibrary.data.DownloadQueueRepository
import app.pepslibrary.data.LibraryRepository
import app.pepslibrary.data.QueueStatus
import app.pepslibrary.data.ReadingProgressRepository
import app.pepslibrary.download.DownloadQueueProcessor
import app.pepslibrary.download.EpubDownloader
import app.pepslibrary.network.Ao3Http
import app.pepslibrary.network.NetworkMonitor
import app.pepslibrary.settings.AppSettings
import app.pepslibrary.reader.ReaderActivity
import kotlinx.coroutines.launch
import java.io.File

/** The browser is always composed; the other screens slide over it, so the WebView keeps its page and history. */
@Composable
fun PepsLibraryApp(
    /** An AO3 page to open in the browser (a link tapped in the reader), or null. */
    openLink: LinkFromBook? = null,
    onLinkOpened: () -> Unit = {},
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.get(context) }
    val worksDir = remember { File(context.filesDir, "works") }
    val repository = remember { LibraryRepository(database.workDao(), worksDir) }
    val progress = remember { ReadingProgressRepository(database.readingProgressDao()) }
    val queue = remember { DownloadQueueRepository(database.downloadQueueDao()) }
    val scope = rememberCoroutineScope()
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Kept here rather than in the library screen, so closing and reopening the library keeps the chosen order.
    // Starts from the last saved choice, so it also survives an app restart; rememberSaveable still covers the
    // activity being recreated in between.
    val viewStore = remember { LibraryViewStore(context) }
    val savedView = remember { viewStore.load() }
    var librarySort by rememberSaveable { mutableStateOf(savedView.toOrder().sort) }
    var librarySortReversed by rememberSaveable { mutableStateOf(savedView.toOrder().reversed) }
    var libraryFilter by rememberSaveable(stateSaver = LibraryFilterSaver) { mutableStateOf(savedView.toFilter()) }
    LaunchedEffect(librarySort, librarySortReversed, libraryFilter) {
        viewStore.save(SavedLibraryView.of(LibraryOrder(librarySort, librarySortReversed), libraryFilter))
    }

    // Started once per process. WebSettings.getDefaultUserAgent gives the same string a WebView would report,
    // without needing a live WebView instance: the queue outlives any one browser page, so it can't borrow the
    // browse screen's WebView the way the single-tap download used to.
    val processor = remember {
        val downloader = EpubDownloader(
            Ao3Http.createClient(WebSettings.getDefaultUserAgent(context)),
            worksDir,
        )
        val network = NetworkMonitor.get(context)
        DownloadQueueProcessor(
            queue,
            repository,
            download = { workId -> downloader.download(workId, this) },
            isOnline = { network.isOnline.value },
        ) { workId, result ->
            val before = result.previousChapters
            val after = result.chapters
            if (before != null && after != null) progress.reconcileAfterUpdate(workId, before, after)
        }.also { it.start() }
    }
    // Cancel and Remove are one action: the processor stops the work if it's running, then takes it off the queue.
    val cancelDownload: (Long) -> Unit = { workId -> scope.launch { processor.cancel(workId) } }

    // A link from the reader shows the browser, so close anything drawn over it.
    LaunchedEffect(openLink) {
        if (openLink != null) {
            showSettings = false
            showLibrary = false
            showQueue = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        BrowseScreen(
            queue = queue,
            onCancelDownload = cancelDownload,
            running = processor.running,
            isDownloaded = repository::isDownloaded,
            onReadWork = { workId -> context.startActivity(ReaderActivity.intent(context, workId)) },
            onOpenQueue = { showQueue = true },
            onOpenLibrary = { showLibrary = true },
            openLink = openLink,
            onLinkOpened = onLinkOpened,
        )

        OverlayScreen(visible = showQueue, onBack = { showQueue = false }) {
            val entries by queue.entries.collectAsState(initial = emptyList())
            val online by NetworkMonitor.get(context).isOnline.collectAsState()
            val running by processor.running.collectAsState()
            QueueScreen(
                entries = entries,
                online = online,
                running = running,
                onRetry = { workId -> scope.launch { queue.enqueue(workId) } },
                onRemove = cancelDownload,
                onBack = { showQueue = false },
            )
        }

        OverlayScreen(visible = showLibrary, onBack = { showLibrary = false }) {
            val works by repository.works.collectAsState(initial = emptyList())
            val fractions by progress.fractions.collectAsState(initial = emptyMap())
            val queued by queue.entries.collectAsState(initial = emptyList())
            LibraryScreen(
                works = works,
                progress = fractions,
                downloading = queued.filter { it.status == QueueStatus.IN_PROGRESS }.map { it.workId }.toSet(),
                order = LibraryOrder(librarySort, librarySortReversed),
                onOrderChange = { librarySort = it.sort; librarySortReversed = it.reversed },
                filter = libraryFilter,
                onFilterChange = { libraryFilter = it },
                onOpenWork = { workId -> context.startActivity(ReaderActivity.intent(context, workId)) },
                onSetPinned = { workId, pinned -> scope.launch { repository.setPinned(workId, pinned) } },
                onDeleteWork = { workId ->
                    scope.launch {
                        // Drop any queued re-download first, or it would bring the work straight back.
                        queue.remove(workId)
                        repository.delete(workId)
                    }
                },
                onOpenSettings = { showSettings = true },
                onBack = { showLibrary = false },
            )
        }

        OverlayScreen(visible = showSettings, onBack = { showSettings = false }) {
            val settings = remember { AppSettings.get(context) }
            val themeMode by settings.themeMode.collectAsState()
            val readingTheme by settings.readingTheme.collectAsState()
            val keepScreenOn by settings.keepScreenOn.collectAsState()
            SettingsScreen(
                themeMode = themeMode,
                onThemeMode = settings::setThemeMode,
                readingTheme = readingTheme,
                onReadingTheme = settings::setReadingTheme,
                keepScreenOn = keepScreenOn,
                onKeepScreenOn = settings::setKeepScreenOn,
                libraryOrder = LibraryOrder(librarySort, librarySortReversed),
                libraryFilter = libraryFilter,
                onResetLibraryView = {
                    val default = LibraryOrder()
                    librarySort = default.sort
                    librarySortReversed = default.reversed
                    libraryFilter = LibraryFilter()
                },
                onBack = { showSettings = false },
            )
        }
    }
}

/** How long a screen takes to slide in; leaving is a little quicker, so going back feels responsive. */
private const val SCREEN_ENTER_MS = 250
private const val SCREEN_EXIT_MS = 200

/**
 * A screen drawn over the browser (Download queue, Library, Settings): it slides in a short way from the right while
 * fading in, and going back reverses that. With Android's animations off it appears and goes at once. The system back
 * button closes it, but only while it's showing: once it's on its way out, a second quick Back goes to whatever is
 * underneath rather than being swallowed by the screen that's leaving.
 */
@Composable
private fun OverlayScreen(visible: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(SCREEN_ENTER_MS, easing = FastOutSlowInEasing)) { it / 8 } +
            fadeIn(tween(SCREEN_ENTER_MS)),
        exit = slideOutHorizontally(tween(SCREEN_EXIT_MS, easing = FastOutSlowInEasing)) { it / 8 } +
            fadeOut(tween(SCREEN_EXIT_MS)),
    ) {
        BackHandler(enabled = transition.targetState == EnterExitState.Visible, onBack = onBack)
        content()
    }
}
