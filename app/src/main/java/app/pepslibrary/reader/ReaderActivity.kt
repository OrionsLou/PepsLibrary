package app.pepslibrary.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import app.pepslibrary.AppScope
import app.pepslibrary.data.AppDatabase
import app.pepslibrary.data.ReadingProgressRepository
import app.pepslibrary.ui.isOpenableExternally
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import java.io.File
import kotlin.math.roundToInt

/**
 * Reads one downloaded work in Readium's EPUB navigator, restoring the saved position on open and saving it as
 * the reader pages, pauses or closes.
 *
 * This is its own activity (as in Readium's own sample app) because the navigator is a Fragment whose factory must
 * be installed before the fragment is created, which suits a plain FragmentActivity better than the Compose browser.
 */
class ReaderActivity : FragmentActivity() {
    private sealed interface State {
        data object Loading : State
        data class Failed(val message: String) : State
        data object Ready : State
    }

    private var state by mutableStateOf<State>(State.Loading)
    private var workTitle by mutableStateOf("")
    private var percentRead by mutableStateOf<Int?>(null)

    private var workId = -1L
    private var publication: Publication? = null
    private var navigator: EpubNavigatorFragment? = null
    private lateinit var progress: ReadingProgressRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Nothing here needs restoring: the book and the position are reloaded from disk and the database. Passing
        // null also stops the FragmentManager from trying to restore a navigator fragment before its factory exists.
        super.onCreate(null)
        enableEdgeToEdge()

        workId = intent.getLongExtra(EXTRA_WORK_ID, -1L)
        if (workId < 0) {
            finish()
            return
        }
        val db = AppDatabase.get(this)
        progress = ReadingProgressRepository(db.readingProgressDao())

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        ReaderBar(title = workTitle, percent = percentRead, onClose = ::finish)
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            when (val s = state) {
                                State.Loading -> CircularProgressIndicator()
                                is State.Failed -> Text(s.message, modifier = Modifier.padding(24.dp))
                                State.Ready -> NavigatorHost()
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch { load(db) }
    }

    private suspend fun load(db: AppDatabase) {
        val work = db.workDao().get(workId)
        if (work == null) {
            state = State.Failed("This work isn't in your library any more.")
            return
        }
        workTitle = work.title

        val file = File(File(filesDir, "works"), work.epubFileName)
        if (!file.isFile) {
            state = State.Failed("The EPUB file is missing. Download the work again.")
            return
        }

        val saved = progress.get(workId)
        percentRead = saved?.totalProgression?.let { (it * 100).roundToInt() }

        when (val opened = withContext(Dispatchers.IO) { EpubOpener.get(this@ReaderActivity).open(file) }) {
            is OpenResult.Failed -> {
                Log.w(TAG, "Could not open $file: ${opened.message}")
                state = State.Failed(opened.message)
            }
            is OpenResult.Opened -> {
                publication = opened.publication
                val initial = saved?.let { locatorFromJson(it.locatorJson) }
                supportFragmentManager.fragmentFactory = EpubNavigatorFactory(opened.publication)
                    .createFragmentFactory(initialLocator = initial, listener = navigatorListener)
                state = State.Ready
            }
        }
    }

    /** Hosts the navigator fragment. It can only be added once its container is on screen, hence the frame wait. */
    @Composable
    private fun NavigatorHost() {
        val containerId = remember { View.generateViewId() }
        AndroidView(
            factory = { FragmentContainerView(it).apply { id = containerId } },
            modifier = Modifier.fillMaxSize(),
        )
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runNavigator(containerId)
        }
    }

    // Readium marks tap-to-turn as experimental. The version is pinned, so an API change can't slip in unnoticed.
    @OptIn(FlowPreview::class, ExperimentalReadiumApi::class)
    private suspend fun runNavigator(containerId: Int) {
        val fragments = supportFragmentManager
        if (fragments.findFragmentByTag(NAVIGATOR_TAG) == null) {
            fragments.commitNow { replace(containerId, EpubNavigatorFragment::class.java, null, NAVIGATOR_TAG) }
        }
        val nav = fragments.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment
        navigator = nav
        // Tapping the left or right edge of the page turns it (swiping already works).
        nav.addInputListener(DirectionalNavigationAdapter(nav))

        coroutineScope {
            launch { nav.currentLocator.collect { percentRead = it.locations.totalProgression?.let { p -> (p * 100).roundToInt() } } }
            // Also save a moment after the reader stops turning pages, in case the process is killed without
            // onStop running.
            launch { nav.currentLocator.debounce(SAVE_DELAY_MS).collect { save(it) } }
        }
    }

    private fun save(locator: Locator) {
        val json = locator.toJSON().toString()
        val total = locator.locations.totalProgression
        AppScope.launch { progress.save(workId, json, total) }
    }

    override fun onStop() {
        super.onStop()
        navigator?.currentLocator?.value?.let(::save)
    }

    override fun onDestroy() {
        super.onDestroy()
        publication?.close()
    }

    /** Links inside a book (for example back to the work on AO3) open in the browser, never inside the reader. */
    @OptIn(ExperimentalReadiumApi::class)
    private val navigatorListener = object : EpubNavigatorFragment.Listener {
        override fun onExternalLinkActivated(url: AbsoluteUrl) {
            val uri = android.net.Uri.parse(url.toString())
            if (!isOpenableExternally(uri.scheme)) return
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: android.content.ActivityNotFoundException) {
                Log.w(TAG, "No app to open $uri")
            }
        }
    }

    companion object {
        private const val TAG = "PepsLibrary"
        private const val EXTRA_WORK_ID = "workId"
        private const val NAVIGATOR_TAG = "epub-navigator"
        private const val SAVE_DELAY_MS = 1_000L

        fun intent(context: Context, workId: Long): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_WORK_ID, workId)

        private fun locatorFromJson(json: String): Locator? =
            try {
                Locator.fromJSON(JSONObject(json))
            } catch (e: Exception) {
                Log.w(TAG, "Ignoring an unreadable saved position", e)
                null // start from the beginning rather than refuse to open the book
            }
    }
}

@Composable
private fun ReaderBar(title: String, percent: Int?, onClose: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close reader")
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            percent?.let {
                Text("$it%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 16.dp))
            }
        }
    }
}
