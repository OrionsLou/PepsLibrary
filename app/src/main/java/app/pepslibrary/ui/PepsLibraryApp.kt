package app.pepslibrary.ui

import android.webkit.WebSettings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import app.pepslibrary.data.ReadingProgressRepository
import app.pepslibrary.download.DownloadQueueProcessor
import app.pepslibrary.download.EpubDownloader
import app.pepslibrary.network.Ao3Http
import app.pepslibrary.reader.ReaderActivity
import kotlinx.coroutines.launch
import java.io.File

/** The browser is always composed; the library slides over it, so the WebView keeps its page and history. */
@Composable
fun PepsLibraryApp() {
    val context = LocalContext.current
    val database = remember { AppDatabase.get(context) }
    val repository = remember { LibraryRepository(database.workDao()) }
    val progress = remember { ReadingProgressRepository(database.readingProgressDao()) }
    val queue = remember { DownloadQueueRepository(database.downloadQueueDao()) }
    val scope = rememberCoroutineScope()
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }

    // Started once per process. WebSettings.getDefaultUserAgent gives the same string a WebView would report,
    // without needing a live WebView instance: the queue outlives any one browser page, so it can't borrow the
    // browse screen's WebView the way the single-tap download used to.
    remember {
        val downloader = EpubDownloader(
            Ao3Http.createClient(WebSettings.getDefaultUserAgent(context)),
            File(context.filesDir, "works"),
        )
        DownloadQueueProcessor(queue, repository, downloader::download).start()
    }

    Box(Modifier.fillMaxSize()) {
        BrowseScreen(
            queue = queue,
            onOpenQueue = { showQueue = true },
            onOpenLibrary = { showLibrary = true },
        )

        if (showQueue) {
            val entries by queue.entries.collectAsState(initial = emptyList())
            QueueScreen(
                entries = entries,
                onRetry = { workId -> scope.launch { queue.enqueue(workId) } },
                onRemove = { workId -> scope.launch { queue.remove(workId) } },
                onBack = { showQueue = false },
            )
        }

        if (showLibrary) {
            val works by repository.works.collectAsState(initial = emptyList())
            val fractions by progress.fractions.collectAsState(initial = emptyMap())
            LibraryScreen(
                works = works,
                progress = fractions,
                onOpenWork = { workId -> context.startActivity(ReaderActivity.intent(context, workId)) },
                onBack = { showLibrary = false },
            )
        }
    }
}
