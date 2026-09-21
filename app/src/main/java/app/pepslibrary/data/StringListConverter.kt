package app.pepslibrary.data

import androidx.room.TypeConverter

/**
 * Stores a list of strings (tags, authors, ...) in one column. Items are joined with the ASCII "unit separator",
 * a control character that does not occur in AO3 text, so commas and quotes in tag names are safe.
 */
object StringListConverter {
    private const val SEPARATOR = ''

    @TypeConverter
    @JvmStatic
    fun fromList(list: List<String>): String = list.joinToString(SEPARATOR.toString())

    @TypeConverter
    @JvmStatic
    fun toList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(SEPARATOR)
}
