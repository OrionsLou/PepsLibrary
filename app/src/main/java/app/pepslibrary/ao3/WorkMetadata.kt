package app.pepslibrary.ao3

/** What AO3 says about a work, as read from its work page. Every field is best effort; see [Ao3.parseWorkMetadata]. */
data class WorkMetadata(
    val title: String? = null,
    val authors: List<String> = emptyList(),
    val summary: String? = null,
    val rating: String? = null,
    val warnings: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val fandoms: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val language: String? = null,
    val words: Int? = null,
    val chaptersPublished: Int? = null,
    /** Null while the author hasn't fixed a total ("3/?"). */
    val chaptersTotal: Int? = null,
    /** ISO date, e.g. 2026-03-06. */
    val publishedDate: String? = null,
    /** ISO date of the last update, or of completion for a finished work. */
    val updatedDate: String? = null,
)
