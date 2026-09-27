package app.pepslibrary.epub

import org.json.JSONObject

/**
 * Edits to a saved Readium Locator's JSON. Uses org.json (Android's copy), so it isn't covered by the JVM unit
 * tests; the decisions that drive it are, in [reconcilePosition].
 */
object LocatorJson {
    /** The zip path the locator points into, or null if the JSON is unreadable. */
    fun path(json: String): String? =
        runCatching { JSONObject(json).optString("href") }.getOrNull()?.takeIf { it.isNotEmpty() }?.let(EpubChapters::pathOf)

    /**
     * Same spot, new file. The in-chapter progression stays valid; `position` and `totalProgression` count across
     * the whole old book, so they're dropped and Readium recomputes them on open.
     */
    fun moveTo(json: String, path: String): String = JSONObject(json).apply {
        put("href", EpubChapters.hrefOf(path))
        optJSONObject("locations")?.apply { remove("position"); remove("totalProgression") }
    }.toString()

    fun startOf(path: String): String = JSONObject()
        .put("href", EpubChapters.hrefOf(path))
        .put("type", "application/xhtml+xml")
        .put("locations", JSONObject().put("progression", 0.0))
        .toString()
}
