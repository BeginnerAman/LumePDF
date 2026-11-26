package com.lumepdf.app.data

import android.content.Context
import android.content.SharedPreferences
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

    private fun getLastPages(): Map<String, Int> {
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

    fun isReaderNightMode(): Boolean = prefs.getBoolean(KEY_READER_NIGHT, false)

    fun setReaderNightMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_READER_NIGHT, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "lumepdf_store"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_CATEGORY_MAP = "category_map"
        private const val KEY_IMPORTED_URIS = "imported_uris"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_DATE_GROUPED = "date_grouped"
        private const val KEY_LAST_PAGES = "last_pages"
        private const val KEY_READER_NIGHT = "reader_night"
    }
}
