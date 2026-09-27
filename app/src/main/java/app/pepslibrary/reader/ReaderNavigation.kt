package app.pepslibrary.reader

import kotlin.math.roundToInt

/** A table-of-contents entry reduced to what the reader controls need. [path] is the file it points into. */
data class ChapterEntry(val title: String, val path: String)

/**
 * For each file in reading order, the title of the chapter it belongs to: the last table-of-contents entry at or
 * before it. That covers files the contents skip, such as AO3's title page, which falls under "Preface".
 */
fun chapterTitlesByFile(readingOrder: List<String>, chapters: List<ChapterEntry>): Map<String, String?> {
    val titleOfFile = chapters.groupBy { it.path }.mapValues { (_, entries) -> entries.first().title }
    var current: String? = null
    val result = LinkedHashMap<String, String?>()
    for (path in readingOrder) {
        titleOfFile[path]?.let { current = it }
        result[path] = current
    }
    return result
}

/** Whole-work progress as the reader shows it: a whole percent, 0 to 100. */
fun percentOf(totalProgression: Double?): Int? =
    totalProgression?.takeUnless { it.isNaN() }?.let { (it * 100).roundToInt().coerceIn(0, 100) }

fun positionLabel(chapterTitle: String?, percent: Int?): String =
    listOfNotNull(chapterTitle, percent?.let { "$it%" }).joinToString(" · ")
