package com.lumepdf.app.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class PdfSource {
    AUTO_SCANNED,
    IMPORTED
}

data class PdfItem(
    val key: String,
    val uri: Uri,
    val filePath: String?,
    val name: String,
    val sizeBytes: Long,
    val dateModifiedMillis: Long,
    val source: PdfSource
) {
    val displayName: String
        get() = if (name.endsWith(".pdf", ignoreCase = true)) {
            name.substring(0, name.length - 4)
        } else {
            name
        }
}

enum class SortOrder(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First")
}

sealed class CategoryChip {
    data class Filter(val name: String, val isSelected: Boolean, val count: Int) : CategoryChip()
    data object AddNew : CategoryChip()
}

sealed class PdfListRow {
    data class DateHeader(val label: String, val count: Int) : PdfListRow()
    data class PdfEntry(val item: PdfItem, val category: String?) : PdfListRow()
}

object PdfFormatters {
    private val dateTimeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }
    private val monthYearFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatShortDate(millis: Long): String {
        if (millis <= 0L) return "Unknown date"
        return dateTimeFormat.get()?.format(Date(millis)) ?: ""
    }

    fun dateSectionLabel(millis: Long): String {
        if (millis <= 0L) return "Earlier"
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = millis }

        val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
        val dayDiff = now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR)

        if (sameYear && dayDiff == 0) return "Today"
        if (sameYear && dayDiff == 1) return "Yesterday"

        val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        if (target.after(weekAgo)) return "This Week"

        return monthYearFormat.get()?.format(Date(millis)) ?: "Earlier"
    }
}
