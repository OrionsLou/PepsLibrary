package app.pepslibrary.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import app.pepslibrary.AppScope
import app.pepslibrary.data.AppDatabase
import app.pepslibrary.data.PositionNotice
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
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
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
    private var notice by mutableStateOf<PositionNotice?>(null)

    /** Every position in the work (roughly a page each at the default font size); the slider steps through these. */
    private var positions by mutableStateOf<List<Locator>>(emptyList())
    private var chapters by mutableStateOf<List<ChapterEntry>>(emptyList())
    private var chapterLinks: List<Link> = emptyList()
    private var chapterOfFile by mutableStateOf<Map<String, String?>>(emptyMap())
    private var current by mutableStateOf<Locator?>(null)
    private var showControls by mutableStateOf(true)
    private var showChapters by mutableStateOf(false)

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
                        notice?.let { NoticeBanner(noticeMessage(it), onClose = { notice = null }) }
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            when (val s = state) {
                                State.Loading -> CircularProgressIndicator()
                                is State.Failed -> Text(s.message, modifier = Modifier.padding(24.dp))
                                State.Ready -> {
                                    NavigatorHost()
                                    // Drawn over the page rather than beside it, so showing or hiding the controls
                                    // never makes Readium re-paginate.
                                    // Fully qualified: inside this Column, the plain name resolves to the
                                    // ColumnScope overload, which can't be called from within the Box. Shown when a
                                    // work opens so it's discoverable; a tap in the middle of the page toggles it.
                                    if (positions.size > 1) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = showControls,
                                            enter = slideInVertically { it },
                                            exit = slideOutVertically { it },
                                            modifier = Modifier.align(Alignment.BottomCenter),
                                        ) {
                                            ReaderControls(
                                                positions = positions,
                                                currentIndex = currentPositionIndex(),
                                                chapterOf = ::chapterTitleOf,
                                                onSeek = { navigator?.go(it, animated = false) },
                                                onOpenChapters = { showChapters = true },
                                                onClose = { showControls = false },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (showChapters) {
                        ChapterSheet(
                            chapters = chapters,
                            currentTitle = current?.let(::chapterTitleOf),
                            onPick = { index ->
                                chapterLinks.getOrNull(index)?.let { navigator?.go(it, animated = false) }
                                showChapters = false
                            },
                            onDismiss = { showChapters = false },
                        )
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
        percentRead = percentOf(saved?.totalProgression)
        notice = saved?.notice

        when (val opened = withContext(Dispatchers.IO) { EpubOpener.get(this@ReaderActivity).open(file) }) {
            is OpenResult.Failed -> {
                Log.w(TAG, "Could not open $file: ${opened.message}")
                state = State.Failed(opened.message)
            }
            is OpenResult.Opened -> {
                val pub = opened.publication
                publication = pub
                chapterLinks = flatten(pub.tableOfContents)
                chapters = chapterLinks.map { ChapterEntry(it.title?.trim().orEmpty().ifEmpty { "Untitled" }, it.path()) }
                chapterOfFile = chapterTitlesByFile(pub.readingOrder.map { it.path() }, chapters)
                positions = withContext(Dispatchers.IO) { pub.positions() }
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
        // Tapping the left or right edge of the page turns it (swiping already works). Listeners are asked in order
        // and the adapter consumes edge taps, so the second one only ever sees taps in the middle of the page.
        nav.addInputListener(DirectionalNavigationAdapter(nav))
        nav.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                showControls = !showControls
                return true
            }
        })

        coroutineScope {
            launch {
                nav.currentLocator.collect {
                    current = it
                    percentRead = percentOf(it.locations.totalProgression)
                }
            }
            // Also save a moment after the reader stops turning pages, in case the process is killed without
            // onStop running.
            launch { nav.currentLocator.debounce(SAVE_DELAY_MS).collect { save(it) } }
        }
    }

    private fun currentPositionIndex(): Int =
        (current?.locations?.position?.minus(1) ?: 0).coerceIn(0, positions.lastIndex)

    private fun chapterTitleOf(locator: Locator): String? = chapterOfFile[locator.href.path.orEmpty()]

    private fun flatten(links: List<Link>): List<Link> = links.flatMap { listOf(it) + flatten(it.children) }

    private fun Link.path(): String = url().removeFragment().path.orEmpty()

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

internal fun noticeMessage(notice: PositionNotice): String = when (notice) {
    PositionNotice.CHAPTER_CHANGED ->
        "This chapter was changed in the latest update, so you're at its start rather than your exact spot."
    PositionNotice.CHAPTER_REMOVED ->
        "The chapter you were reading isn't in the latest update any more, so you're back at the start of the work."
}

@Composable
private fun NoticeBanner(message: String, onClose: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }
}

/**
 * The slider steps through Readium's positions (about a page each at the default font size) and shows the target's
 * chapter and percent while dragging; the jump happens on release. Single pages are still turned by tap or swipe.
 */
@Composable
private fun ReaderControls(
    positions: List<Locator>,
    currentIndex: Int,
    chapterOf: (Locator) -> String?,
    onSeek: (Locator) -> Unit,
    onOpenChapters: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    // Hold the released value until the navigator reports the new position, so the thumb doesn't snap back first.
    LaunchedEffect(currentIndex) { dragIndex = null }
    val shownIndex = dragIndex ?: currentIndex
    val shown = positions[shownIndex]

    Surface(
        modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                // Dragging the bar down closes it, like a bottom sheet. The slider's own drags are sideways, so
                // they never get this far.
                var travelled = 0f
                detectVerticalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = { if (travelled > 48.dp.toPx()) onClose() },
                ) { _, dy -> travelled += dy }
            },
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 4.dp)) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    positionLabel(chapterOf(shown), percentOf(shown.locations.totalProgression)),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Hide reading controls")
                }
            }
            Slider(
                value = shownIndex.toFloat(),
                onValueChange = { dragIndex = it.roundToInt().coerceIn(0, positions.lastIndex) },
                onValueChangeFinished = { dragIndex?.let { onSeek(positions[it]) } },
                valueRange = 0f..positions.lastIndex.toFloat(),
                modifier = Modifier.padding(end = 12.dp),
            )
            TextButton(onClick = onOpenChapters) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Chapters")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterSheet(chapters: List<ChapterEntry>, currentTitle: String?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val currentIndex = chapters.indexOfFirst { it.title == currentTitle }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Chapters",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(chapters) { index, chapter ->
                val isCurrent = index == currentIndex
                Text(
                    chapter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(index) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
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
