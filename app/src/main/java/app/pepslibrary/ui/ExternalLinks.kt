package app.pepslibrary.ui

/**
 * Only plain web links may be handed to other apps. Anything else (javascript:, file:, intent:, content:, ...)
 * is dropped rather than turned into an Intent.
 */
internal fun isOpenableExternally(scheme: String?): Boolean =
    scheme.equals("https", ignoreCase = true) || scheme.equals("http", ignoreCase = true)
