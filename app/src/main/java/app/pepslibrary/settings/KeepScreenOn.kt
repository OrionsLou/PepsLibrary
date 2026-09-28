package app.pepslibrary.settings

/** Whether the reader keeps the phone's screen from sleeping. */
sealed interface KeepScreenOn {
    /** On for as long as the reader is showing. The default. */
    data object Always : KeepScreenOn

    /** On for [minutes] after the last touch (a page turn, a tap, the bottom bar), then the phone's own timer. */
    data class Timed(val minutes: Int) : KeepScreenOn

    /** The phone's own sleep timer, as in any other app. */
    data object Off : KeepScreenOn

    /** How it's saved: "always", "off", or "timed:<minutes>". */
    fun toSaved(): String = when (this) {
        Always -> "always"
        Off -> "off"
        is Timed -> "timed:$minutes"
    }

    companion object {
        val DEFAULT: KeepScreenOn = Always
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 240

        /** What Timed starts at when first chosen. */
        const val DEFAULT_MINUTES = 10

        /** Anything unreadable, from this or a later version, falls back to [DEFAULT]; minutes are kept in range. */
        fun fromSaved(saved: String?): KeepScreenOn = when {
            saved == "always" -> Always
            saved == "off" -> Off
            saved != null && saved.startsWith("timed:") ->
                saved.removePrefix("timed:").toIntOrNull()?.let { Timed(clampMinutes(it)) } ?: DEFAULT
            else -> DEFAULT
        }

        fun clampMinutes(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

        /** Typed minutes, if they're a whole number in range; null for anything else (empty, 0, "1.5", "abc"). */
        fun parseMinutes(text: String): Int? =
            text.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()
                ?.takeIf { it in MIN_MINUTES..MAX_MINUTES }
    }
}
