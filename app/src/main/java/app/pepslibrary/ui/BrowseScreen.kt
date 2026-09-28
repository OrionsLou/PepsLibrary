package app.pepslibrary.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.pepslibrary.BuildConfig
import app.pepslibrary.ao3.Ao3
import app.pepslibrary.data.DownloadQueueEntity
import app.pepslibrary.data.DownloadQueueRepository
import app.pepslibrary.download.DownloadProgress
import app.pepslibrary.download.RunningDownload
import app.pepslibrary.network.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "PepsLibrary"

/** Hosts AO3 in a WebView. Sign-in happens on the site itself; the WebView's CookieManager owns the session. */
@Composable
fun BrowseScreen(
    queue: DownloadQueueRepository,
    onCancelDownload: (workId: Long) -> Unit,
    /** The download in progress, if any, for the Download bar's progress. */
    running: StateFlow<RunningDownload?>,
    isDownloaded: (workId: Long) -> Flow<Boolean>,
    onReadWork: (workId: Long) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val online by remember { NetworkMonitor.get(context).isOnline }.collectAsState()

    val webView = remember {
        createWebView(
            context = context,
            onProgress = { progress = it },
            onHistoryChanged = { back, forward, url -> canGoBack = back; canGoForward = forward; currentUrl = url },
            onPageStarted = { error = null },
            onError = { error = it },
        ).also { it.loadUrl(Ao3.HOME_URL) }
    }
    DisposableEffect(webView) { onDispose { webView.destroy() } }

    val workId = Ao3.workIdFromUrl(currentUrl)

    fun goBack() { error = null; webView.goBack() }
    fun goForward() { error = null; webView.goForward() }
    fun refresh() {
        error = null
        refreshTarget(webView.url)?.let { webView.loadUrl(it) } ?: webView.reload()
    }
    val loading = progress in 1..99
    fun stop() {
        webView.stopLoading()
        progress = 100 // a stopped load doesn't always report reaching the end
    }

    BackHandler(enabled = canGoBack) { goBack() }

    // A page that failed while offline reloads by itself once the connection is back; nothing else is retried.
    LaunchedEffect(online) {
        if (online && error != null) refresh()
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

            if (loading) PageLoadBar(progress, Modifier.align(Alignment.TopStart))

            error?.let { message ->
                if (online) {
                    ErrorOverlay(message = message, onRetry = ::refresh)
                } else {
                    OfflineOverlay(onOpenLibrary = onOpenLibrary, onRetry = ::refresh)
                }
            }
        }

        // Sits between the page and the footer rather than over the page, so AO3's own content is never covered.
        if (workId != null) {
            // Re-derived whenever workId changes, so navigating to a different work page swaps which entry we
            // watch. The queue's own Flow drives this: no local "just tapped" state to keep in sync by hand.
            val entry by remember(workId) {
                queue.entries.map { entries -> entries.find { it.workId == workId } }
            }.collectAsState(initial = null)

            val inLibrary by remember(workId) { isDownloaded(workId) }.collectAsState(initial = false)
            val runningNow by running.collectAsState()

            DownloadBar(
                entry = entry,
                progress = runningNow?.takeIf { it.workId == workId }?.progress,
                inLibrary = inLibrary,
                online = online,
                onDownload = {
                    val title = Ao3.workTitleFromPageTitle(webView.title)
                    scope.launch { queue.enqueue(workId, title) }
                },
                onCancel = { onCancelDownload(workId) },
                onRead = { onReadWork(workId) },
            )
        }

        BrowserToolbar(
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            onBack = ::goBack,
            onForward = ::goForward,
            loading = loading,
            onRefresh = ::refresh,
            onStop = ::stop,
            onOpenQueue = onOpenQueue,
            onOpenLibrary = onOpenLibrary,
        )
    }
}

/**
 * The bar for the work page you're on: download it, follow a download in progress, or, once it's in the library, read
 * it or fetch the latest version.
 */
@Composable
private fun DownloadBar(
    entry: DownloadQueueEntity?,
    /** Set only while this work is the one downloading. */
    progress: DownloadProgress?,
    inLibrary: Boolean,
    online: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // contentColor is explicit: in Pep's palette surfaceContainer and surfaceVariant are the same grey, and Material's
    // lookup by colour would otherwise pick the muted onSurfaceVariant for all of the bar's text.
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                queueShowsCancel(entry) -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            queueStatusLabel(entry, online, progress).orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onCancel) { Text("Cancel download") }
                    }
                    DownloadProgressIndicator(progress)
                }
                entry == null && inLibrary -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("In your library", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    // Update fetches AO3's current version: new chapters of a work in progress, or an author's edits.
                    TextButton(onClick = onDownload) { Text("Update") }
                    Button(onClick = onRead) { Text("Read") }
                }
                else -> {
                    Button(onClick = onDownload) { Text(queueButtonLabel(entry)) }
                    queueStatusLabel(entry, online)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Shows how far a page has loaded as a clear accent bar along the top; it starts at a tenth so it's seen at once. */
@Composable
private fun PageLoadBar(progress: Int, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState((progress.coerceIn(10, 100)) / 100f, label = "pageLoad")
    Box(modifier.fillMaxWidth().height(3.dp)) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun ErrorOverlay(message: String, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Couldn't load the page", style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

/** Shown instead of [ErrorOverlay] when the page failed because there's no connection at all. */
@Composable
private fun OfflineOverlay(onOpenLibrary: () -> Unit, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("You're offline", style = MaterialTheme.typography.titleLarge)
            Text(
                "AO3 needs a connection, but your downloaded works are still in your library. This page reloads by " +
                    "itself once you're back online.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpenLibrary) { Text("Open Library") }
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    onProgress: (Int) -> Unit,
    onHistoryChanged: (canGoBack: Boolean, canGoForward: Boolean, url: String?) -> Unit,
    onPageStarted: () -> Unit,
    onError: (String) -> Unit,
): WebView = WebView(context).apply {
    settings.apply {
        javaScriptEnabled = true // AO3 and its bot check need it
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
    }
    // Step 3 must send this exact user-agent from OkHttp, or bot-check cookies may not carry over.
    if (BuildConfig.DEBUG) Log.i(TAG, "WebView user agent: ${settings.userAgentString}")

    CookieManager.getInstance().setAcceptCookie(true)

    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame || Ao3.isAo3Url(request.url.toString())) return false
            openExternally(view.context, request.url)
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = onPageStarted()

        override fun onPageFinished(view: WebView, url: String) {
            CookieManager.getInstance().flush()
            // Debug builds only (release logs would record every page visited), and names only, never values:
            // lets us confirm the session cookie survives an app restart.
            if (!BuildConfig.DEBUG) return
            val names = CookieManager.getInstance().getCookie(url)
                ?.split(";")?.map { it.substringBefore("=").trim() }
            Log.d(TAG, "Cookies for $url: $names")
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) =
            onHistoryChanged(view.canGoBack(), view.canGoForward(), url)

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) onError(error.description.toString())
        }
    }
}

private fun openExternally(context: Context, uri: Uri) {
    if (!isOpenableExternally(uri.scheme)) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Log.w(TAG, "No app to open $uri")
    }
}
