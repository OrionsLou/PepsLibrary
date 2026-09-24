package app.pepslibrary.data

import androidx.room.TypeConverter
import app.pepslibrary.download.FailureKind

/** Stores enums by name. Room needs an explicit converter for any column type it doesn't know natively. */
object EnumConverters {
    @TypeConverter
    @JvmStatic
    fun fromQueueStatus(status: QueueStatus): String = status.name

    @TypeConverter
    @JvmStatic
    fun toQueueStatus(value: String): QueueStatus = QueueStatus.valueOf(value)

    @TypeConverter
    @JvmStatic
    fun fromFailureKind(kind: FailureKind?): String? = kind?.name

    @TypeConverter
    @JvmStatic
    fun toFailureKind(value: String?): FailureKind? = value?.let { FailureKind.valueOf(it) }
}
