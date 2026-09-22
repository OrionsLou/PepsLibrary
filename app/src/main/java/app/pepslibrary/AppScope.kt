package app.pepslibrary

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * For work that must finish even if the screen that started it goes away, such as saving a reading position as the
 * reader closes. Activity-scoped coroutines are cancelled at that moment; this one lives as long as the process.
 */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
