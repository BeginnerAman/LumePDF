package com.lumepdf.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.lumepdf.app.model.PdfItem
import com.lumepdf.app.model.PdfSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfScanner {

    fun hasFullAutoScanPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun scanAllPdfs(
        context: Context,
        store: PdfStore,
        fullWalk: Boolean = true
    ): List<PdfItem> = withContext(Dispatchers.IO) {
        val byPath = LinkedHashMap<String, PdfItem>()
        val importedOnly = mutableListOf<PdfItem>()
        val hasFullAccess = hasFullAutoScanPermission(context)

        if (fullWalk) {
            // Full directory walk (first launch, permission change, or manual refresh)
            if (hasFullAccess) {
                @Suppress("DEPRECATION")
                val root = Environment.getExternalStorageDirectory()
                if (root != null && root.exists() && root.canRead()) {
                    walkDirectoryForPdfs(context, root, byPath)
                }
            }
        } else {
            // Fast cache-first sync: validate cached file-backed entries without walking entire storage
            val cached = store.getCachedPdfs()
            for (item in cached) {
                if (item.source != PdfSource.AUTO_SCANNED) continue
                val path = item.filePath
                if (!path.isNullOrBlank()) {
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        byPath[item.key] = item.copy(
                            sizeBytes = file.length(),
                            dateModifiedMillis = file.lastModified()
                        )
                    }
                }
            }
        }

        // Fast MediaStore query catches newly downloaded/received PDFs
        scanMediaStore(context, byPath)

        // Manually imported SAF URIs
        val validUris = store.getImportedUris().toMutableSet()
        val staleUris = mutableListOf<String>()
        for (uriStr in validUris) {
            val uri = runCatching { Uri.parse(uriStr) }.getOrNull()
            if (uri == null) {
                staleUris.add(uriStr)
                continue
            }
            val item = resolveSafPdf(context, uri)
            if (item == null) {
                staleUris.add(uriStr)
            } else {
                val alreadyPresent = byPath.values.any {
                    it.name == item.name && it.sizeBytes == item.sizeBytes
                }
                if (!alreadyPresent) {
                    importedOnly.add(item)
                }
            }
        }
        staleUris.forEach { store.removeImportedUri(it) }

        val result = (byPath.values + importedOnly).toList()
        store.saveCachedPdfs(result)
        store.setLastScanHadFullAccess(hasFullAccess)
        result
    }

    private fun walkDirectoryForPdfs(
        context: Context,
        dir: File,
        out: MutableMap<String, PdfItem>
    ) {
        val stack = ArrayDeque<File>()
        stack.addLast(dir)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                val name = child.name
                if (name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (name.equals("Android", ignoreCase = true) &&
                        child.parentFile?.absolutePath == Environment.getExternalStorageDirectory().absolutePath
                    ) {
                        continue
                    }
                    stack.addLast(child)
                } else if (child.isFile && name.endsWith(".pdf", ignoreCase = true)) {
                    val canonical = runCatching { child.canonicalPath }.getOrDefault(child.absolutePath)
                    if (!out.containsKey(canonical)) {
                        val contentUri = runCatching {
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                child
                            )
                        }.getOrElse { Uri.fromFile(child) }
                        out[canonical] = PdfItem(
                            key = canonical,
                            uri = contentUri,
                            filePath = canonical,
                            name = name,
                            sizeBytes = child.length(),
                            dateModifiedMillis = child.lastModified(),
                            source = PdfSource.AUTO_SCANNED
                        )
                    }
                }
            }
        }
    }

    private fun scanMediaStore(context: Context, out: MutableMap<String, PdfItem>) {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA
        )
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE ?"
        val selectionArgs = arrayOf("application/pdf", "%.pdf")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Document.pdf"
                    val size = cursor.getLong(sizeCol)
                    val dateSec = cursor.getLong(dateCol)
                    val rawPath = if (dataCol >= 0) cursor.getString(dataCol) else null

                    val canonicalPath = rawPath?.let { path ->
                        val f = File(path)
                        if (f.exists()) runCatching { f.canonicalPath }.getOrDefault(path) else null
                    }
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val key = canonicalPath ?: contentUri.toString()

                    if (!out.containsKey(key)) {
                        out[key] = PdfItem(
                            key = key,
                            uri = contentUri,
                            filePath = canonicalPath,
                            name = name,
                            sizeBytes = size,
                            dateModifiedMillis = dateSec * 1000L,
                            source = PdfSource.AUTO_SCANNED
                        )
                    }
                }
            }
        }
    }

    fun resolveSafPdf(context: Context, uri: Uri): PdfItem? {
        return runCatching {
            var displayName = "Document.pdf"
            var size = 0L
            var modified = System.currentTimeMillis()

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    displayName = cursor.getString(nameIdx) ?: displayName
                }
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    size = cursor.getLong(sizeIdx)
                }
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (modIdx >= 0 && !cursor.isNull(modIdx)) {
                    modified = cursor.getLong(modIdx)
                }
            } ?: return null

            PdfItem(
                key = uri.toString(),
                uri = uri,
                filePath = null,
                name = displayName,
                sizeBytes = size,
                dateModifiedMillis = modified,
                source = PdfSource.IMPORTED
            )
        }.getOrNull()
    }

    suspend fun deletePdf(context: Context, item: PdfItem, store: PdfStore): Boolean =
        withContext(Dispatchers.IO) {
            if (item.source == PdfSource.IMPORTED) {
                runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, item.uri)
                }
                store.removePdfMetadata(item.key)
                return@withContext true
            }

            var deleted = false
            val path = item.filePath
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists() && file.delete()) {
                    deleted = true
                    MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                }
            }

            if (!deleted) {
                deleted = runCatching {
                    context.contentResolver.delete(item.uri, null, null) > 0
                }.getOrDefault(false)
            }

            if (deleted) {
                store.removePdfMetadata(item.key)
            }
            deleted
        }

    suspend fun renamePdf(
        context: Context,
        item: PdfItem,
        newRawName: String,
        store: PdfStore
    ): Result<PdfItem> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = newRawName.trim()
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
                .trim()
            require(trimmed.isNotEmpty()) { "Name cannot be empty" }
            require(!trimmed.contains('/') && !trimmed.contains('\\')) { "Invalid characters in name" }

            val newFileName = "$trimmed.pdf"
            if (newFileName == item.name) return@runCatching item

            val path = item.filePath
            if (!path.isNullOrBlank()) {
                val oldFile = File(path)
                val parent = oldFile.parentFile ?: error("Parent folder unavailable")
                val targetFile = File(parent, newFileName)
                require(!targetFile.exists()) { "A PDF with this name already exists" }
                check(oldFile.renameTo(targetFile)) { "Permission denied to rename this file" }

                val canonical = runCatching { targetFile.canonicalPath }.getOrDefault(targetFile.absolutePath)
                val newUri = runCatching {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        targetFile
                    )
                }.getOrElse { Uri.fromFile(targetFile) }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(path, canonical),
                    null,
                    null
                )
                store.migratePdfKey(item.key, canonical)
                item.copy(
                    key = canonical,
                    uri = newUri,
                    filePath = canonical,
                    name = newFileName
                )
            } else {
                if (item.source == PdfSource.AUTO_SCANNED && !hasFullAutoScanPermission(context)) {
                    error("Allow file access in settings to rename this PDF")
                }
                val renamedUri = runCatching {
                    DocumentsContract.renameDocument(
                        context.contentResolver,
                        item.uri,
                        newFileName
                    )
                }.getOrNull() ?: error("Rename not supported for this file location")
                val newKey = renamedUri.toString()
                store.migratePdfKey(item.key, newKey)
                item.copy(
                    key = newKey,
                    uri = renamedUri,
                    name = newFileName
                )
            }
        }
    }
}
