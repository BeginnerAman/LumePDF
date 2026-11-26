package com.lumepdf.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.lumepdf.app.model.PdfItem
import com.lumepdf.app.model.PdfSource
import com.lumepdf.app.model.SortOrder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight SharedPreferences + org.json persistence for categories,
 * PDF-to-category assignments, imported URIs, sort state, and reader progress.
 */
class PdfStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCategories(): List<String> {
        val raw = prefs.getString(KEY_CATEGORIES, null)
        if (raw.isNullOrBlank()) {
            val defaults = listOf("Study", "Work", "Bills", "Books")
            saveCategories(defaults)
            return defaults
        }
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }.getOrElse { listOf("Study", "Work", "Bills", "Books") }
    }

    fun addCategory(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        if (clean.equals("All", ignoreCase = true) ||
            clean.equals("Other", ignoreCase = true) ||
            clean.equals("Uncategorized", ignoreCase = true)
        ) {
            return false
        }
        val current = getCategories().toMutableList()
        if (current.any { it.equals(clean, ignoreCase = true) }) return false
        current.add(clean)
        saveCategories(current)
        return true
    }

    fun deleteCategory(name: String) {
        val current = getCategories().filterNot { it.equals(name, ignoreCase = true) }
        saveCategories(current)

        val map = getCategoryMap().toMutableMap()
        val keysToRemove = map.filterValues { it.equals(name, ignoreCase = true) }.keys
        keysToRemove.forEach { map.remove(it) }
        saveCategoryMap(map)
    }

    private fun saveCategories(categories: List<String>) {
        val arr = JSONArray()
        categories.forEach { arr.put(it) }
        prefs.edit().putString(KEY_CATEGORIES, arr.toString()).apply()
    }

    fun getCategoryMap(): Map<String, String> {
        val raw = prefs.getString(KEY_CATEGORY_MAP, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.optString(k)
                    if (v.isNotEmpty()) put(k, v)
                }
            }
        }.getOrElse { emptyMap() }
    }

    fun setPdfCategory(pdfKey: String, category: String?) {
        val map = getCategoryMap().toMutableMap()
        if (category.isNullOrBlank()) {
            map.remove(pdfKey)
        } else {
            map[pdfKey] = category
        }
        saveCategoryMap(map)
    }

    fun removePdfMetadata(pdfKey: String) {
        val map = getCategoryMap().toMutableMap()
        if (map.remove(pdfKey) != null) {
            saveCategoryMap(map)
        }
        removeImportedUri(pdfKey)
        val pages = getLastPages().toMutableMap()
        if (pages.remove(pdfKey) != null) {
            saveLastPages(pages)
        }
        val counts = getPageCounts().toMutableMap()
        if (counts.remove(pdfKey) != null) {
            savePageCounts(counts)
        }
    }

    fun migratePdfKey(oldKey: String, newKey: String) {
        if (oldKey == newKey) return
        val catMap = getCategoryMap().toMutableMap()
        val oldCat = catMap.remove(oldKey)
        if (oldCat != null) {
            catMap[newKey] = oldCat
            saveCategoryMap(catMap)
        }

        val pages = getLastPages().toMutableMap()
        val oldPage = pages.remove(oldKey)
        if (oldPage != null) {
            pages[newKey] = oldPage
            saveLastPages(pages)
        }

        val counts = getPageCounts().toMutableMap()
        val oldCount = counts.remove(oldKey)
        if (oldCount != null) {
            counts[newKey] = oldCount
            savePageCounts(counts)
        }

        val imported = getImportedUris()
        if (imported.contains(oldKey)) {
            val updated = imported.toMutableSet()
            updated.remove(oldKey)
            updated.add(newKey)
            prefs.edit().putStringSet(KEY_IMPORTED_URIS, updated).apply()
        }
    }

    private fun saveCategoryMap(map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_CATEGORY_MAP, obj.toString()).apply()
    }

    fun getImportedUris(): Set<String> {
        return prefs.getStringSet(KEY_IMPORTED_URIS, emptySet())?.toSet() ?: emptySet()
    }

    fun addImportedUri(uriString: String) {
        val updated = getImportedUris().toMutableSet()
        updated.add(uriString)
        prefs.edit().putStringSet(KEY_IMPORTED_URIS, updated).apply()
    }

    fun removeImportedUri(uriString: String) {
        val current = getImportedUris()
        if (!current.contains(uriString)) return
        val updated = current.toMutableSet()
        updated.remove(uriString)
        prefs.edit().putStringSet(KEY_IMPORTED_URIS, updated).apply()
    }

    fun getSortOrder(): SortOrder {
        val name = prefs.getString(KEY_SORT_ORDER, SortOrder.DATE_DESC.name)
        return runCatching { SortOrder.valueOf(name ?: SortOrder.DATE_DESC.name) }
            .getOrDefault(SortOrder.DATE_DESC)
    }

    fun setSortOrder(order: SortOrder) {
        prefs.edit().putString(KEY_SORT_ORDER, order.name).apply()
    }

    fun isDateGrouped(): Boolean = prefs.getBoolean(KEY_DATE_GROUPED, true)

    fun setDateGrouped(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DATE_GROUPED, enabled).apply()
    }

    fun getLastPage(pdfKey: String): Int = getLastPages()[pdfKey] ?: 0

    fun setLastPage(pdfKey: String, pageIndex: Int) {
        val map = getLastPages().toMutableMap()
        map[pdfKey] = pageIndex.coerceAtLeast(0)
        saveLastPages(map)
    }

    fun getLastPages(): Map<String, Int> {
        val raw = prefs.getString(KEY_LAST_PAGES, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, obj.optInt(k, 0))
                }
            }
        }.getOrElse { emptyMap() }
    }

    private fun saveLastPages(map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_LAST_PAGES, obj.toString()).apply()
    }

    fun setPageCount(pdfKey: String, totalPages: Int) {
        if (totalPages <= 0) return
        val map = getPageCounts().toMutableMap()
        if (map[pdfKey] == totalPages) return
        map[pdfKey] = totalPages
        savePageCounts(map)
    }

    fun getPageCounts(): Map<String, Int> {
        val raw = prefs.getString(KEY_PAGE_COUNTS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, obj.optInt(k, 0))
                }
            }
        }.getOrElse { emptyMap() }
    }

    private fun savePageCounts(map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_PAGE_COUNTS, obj.toString()).apply()
    }

    fun isReaderNightMode(): Boolean = prefs.getBoolean(KEY_READER_NIGHT, false)

    fun setReaderNightMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_READER_NIGHT, enabled).apply()
    }

    fun isReaderCropMargins(): Boolean = prefs.getBoolean(KEY_READER_CROP, false)

    fun setReaderCropMargins(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_READER_CROP, enabled).apply()
    }

    fun isKeepScreenOn(): Boolean = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    }

    fun getLastScanHadFullAccess(): Boolean = prefs.getBoolean(KEY_LAST_FULL_ACCESS, false)

    fun setLastScanHadFullAccess(granted: Boolean) {
        prefs.edit().putBoolean(KEY_LAST_FULL_ACCESS, granted).apply()
    }

    fun hasCachedPdfs(): Boolean = prefs.contains(KEY_CACHED_PDFS)

    fun getCachedPdfs(): List<PdfItem> {
        val raw = prefs.getString(KEY_CACHED_PDFS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val key = obj.optString("k")
                    val uriStr = obj.optString("u")
                    val name = obj.optString("n")
                    if (key.isEmpty() || uriStr.isEmpty() || name.isEmpty()) continue
                    val path = obj.optString("p").takeIf { it.isNotEmpty() }
                    val size = obj.optLong("s", 0L)
                    val date = obj.optLong("d", 0L)
                    val srcName = obj.optString("r", PdfSource.AUTO_SCANNED.name)
                    val source = runCatching { PdfSource.valueOf(srcName) }
                        .getOrDefault(PdfSource.AUTO_SCANNED)
                    add(
                        PdfItem(
                            key = key,
                            uri = Uri.parse(uriStr),
                            filePath = path,
                            name = name,
                            sizeBytes = size,
                            dateModifiedMillis = date,
                            source = source
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun saveCachedPdfs(items: List<PdfItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("k", item.key)
                put("u", item.uri.toString())
                put("p", item.filePath ?: "")
                put("n", item.name)
                put("s", item.sizeBytes)
                put("d", item.dateModifiedMillis)
                put("r", item.source.name)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_CACHED_PDFS, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "lumepdf_store"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_CATEGORY_MAP = "category_map"
        private const val KEY_IMPORTED_URIS = "imported_uris"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_DATE_GROUPED = "date_grouped"
        private const val KEY_LAST_PAGES = "last_pages"
        private const val KEY_PAGE_COUNTS = "page_counts"
        private const val KEY_READER_NIGHT = "reader_night"
        private const val KEY_READER_CROP = "reader_crop"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_CACHED_PDFS = "cached_pdfs"
        private const val KEY_LAST_FULL_ACCESS = "last_full_access"
    }
}
