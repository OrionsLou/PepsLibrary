package app.pepslibrary.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.pepslibrary.data.AppDatabase
import app.pepslibrary.data.LibraryRepository

/** The browser is always composed; the library slides over it, so the WebView keeps its page and history. */
@Composable
fun PepsLibraryApp() {
    val context = LocalContext.current
    val repository = remember { LibraryRepository(AppDatabase.get(context).workDao()) }
    var showLibrary by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        BrowseScreen(repository = repository, onOpenLibrary = { showLibrary = true })

        if (showLibrary) {
            val works by repository.works.collectAsState(initial = emptyList())
            LibraryScreen(works = works, onBack = { showLibrary = false })
        }
    }
}
