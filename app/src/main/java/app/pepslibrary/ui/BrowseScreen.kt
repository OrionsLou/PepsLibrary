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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.pepslibrary.ao3.Ao3
import app.pepslibrary.data.LibraryRepository
import app.pepslibrary.download.DownloadResult
import app.pepslibrary.download.EpubDownloader
import app.pepslibrary.network.Ao3Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "PepsLibrary"

/** Hosts AO3 in a WebView. Sign-in happens on the site itself; the WebView's CookieManager owns the session. */
@Composable
fun BrowseScreen(repository: LibraryRepository, onOpenLibrary: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

    // The client must send the WebView's exact user-agent, so it is built from the WebView's own settings.
    val downloader = remember {
        EpubDownloader(Ao3Http.createClient(webView.settings.userAgentString), File(context.filesDir, "works"))
    }
    val workId = Ao3.workIdFromUrl(currentUrl)

    fun goBack() { error = null; webView.goBack() }
    fun goForward() { error = null; webView.goForward() }
    fun refresh() {
        error = null
        refreshTarget(webView.url)?.let { webView.loadUrl(it) } ?: webView.reload()
    }

    BackHandler(enabled = canGoBack) { goBack() }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
                )
            }

            error?.let { message ->
                ErrorOverlay(message = message, onRetry = ::refresh)
            }
        }

        // Sits between the page and the footer rather than over the page, so AO3's own content is never covered.
        if (workId != null) {
            DownloadBar(
                status = downloadStatus,
                enabled = !downloading,
                onDownload = {
                    downloading = true
                    downloadStatus = "Downloading work $workId..."
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { downloader.download(workId) }
                        downloadStatus = when (result) {
                            is DownloadResult.Success -> try {
                                withContext(Dispatchers.IO) { repository.saveDownload(workId, result) }
                                describe(result)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Saved ${result.file} but could not record it", e)
                                "Saved ${result.file.name} but couldn't add it to the library: ${e.message}"
                            }
                            is DownloadResult.Failure -> describe(result)
                        }
                        downloading = false
                    }
                },
            )
        }

        BrowserToolbar(
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            onBack = ::goBack,
            onForward = ::goForward,
            onRefresh = ::refresh,
            onOpenLibrary = onOpenLibrary,
        )
    }
}

private fun describe(result: DownloadResult): String = when (result) {
    is DownloadResult.Success -> {
        Log.i(TAG, "Saved ${result.file} (${result.bytes} bytes) from ${result.epubUrl}")
        "Saved \"${result.metadata.title ?: result.file.name}\" (${result.bytes / 1024} KB) to your library"
    }
    is DownloadResult.Failure -> {
        Log.w(TAG, "Download failed: ${result.kind}: ${result.message}")
        "${result.kind}: ${result.message}"
    }
}

@Composable
private fun DownloadBar(status: String?, enabled: Boolean, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 6.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDownload, enabled = enabled) { Text("Download EPUB") }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
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
    Log.i(TAG, "WebView user agent: ${settings.userAgentString}")

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
            // Names only, never values: lets us confirm the session cookie survives an app restart.
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
