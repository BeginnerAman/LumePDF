package com.lumepdf.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lumepdf.app.data.PdfScanner
import com.lumepdf.app.data.PdfStore
import com.lumepdf.app.databinding.ActivityMainBinding
import com.lumepdf.app.model.CategoryChip
import com.lumepdf.app.model.PdfFormatters
import com.lumepdf.app.model.PdfItem
import com.lumepdf.app.model.PdfListRow
import com.lumepdf.app.model.PdfSource
import com.lumepdf.app.model.SortOrder
import com.lumepdf.app.ui.CategoryChipAdapter
import com.lumepdf.app.ui.GlassDialogs
import com.lumepdf.app.ui.PdfListAdapter
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: PdfStore

    private lateinit var categoryAdapter: CategoryChipAdapter
    private lateinit var pdfAdapter: PdfListAdapter

    private var allPdfs: List<PdfItem> = emptyList()
    private var selectedCategory: String = ALL_CATEGORY
    private var searchQuery: String = ""
    private var sortOrder: SortOrder = SortOrder.DATE_DESC
    private var dateGrouped: Boolean = true

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            store.addImportedUri(uri.toString())
            scanLibrary()
        }
    }

    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updatePermissionBanner()
        scanLibrary()
    }

    private val manageFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionBanner()
        scanLibrary()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = PdfStore(this)
        sortOrder = store.getSortOrder()
        dateGrouped = store.isDateGrouped()

        applyEdgeToEdgeInsets()
        setupRecyclerViews()
        setupControls()
        updatePermissionBanner()
        updateSortAndDatePills()
        scanLibrary()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBanner()
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    private fun setupRecyclerViews() {
        categoryAdapter = CategoryChipAdapter(
            onChipClick = { chip ->
                when (chip) {
                    is CategoryChip.Filter -> {
                        selectedCategory = chip.name
                        renderList()
                    }

                    CategoryChip.AddNew -> {
                        GlassDialogs.showNewCategoryDialog(this) { newName ->
                            if (store.addCategory(newName)) {
                                selectedCategory = newName
                                renderList()
                            } else {
                                Toast.makeText(this, "Category already exists", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }
            },
            onChipLongClick = { catName ->
                if (catName != ALL_CATEGORY && catName != UNCATEGORIZED_CATEGORY) {
                    GlassDialogs.showDeleteConfirmDialog(
                        context = this,
                        title = "Delete Category?",
                        body = "Remove “$catName”? PDFs inside it will become Uncategorized."
                    ) {
                        store.deleteCategory(catName)
                        if (selectedCategory.equals(catName, ignoreCase = true)) {
                            selectedCategory = ALL_CATEGORY
                        }
                        renderList()
                    }
                }
            }
        )

        binding.rvCategories.apply {
            layoutManager =
                LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
            itemAnimator = null
        }

        pdfAdapter = PdfListAdapter(
            onOpenPdf = { item -> openReader(item) },
            onMoreClick = { item -> showPdfActions(item) }
        )

        binding.rvPdfs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = pdfAdapter
        }
    }

    private fun setupControls() {
        binding.btnRefresh.setOnClickListener { scanLibrary() }

        binding.btnGrantAccess.setOnClickListener { requestAutoScanPermission() }

        binding.fabAddPdf.setOnClickListener {
            pickPdfLauncher.launch(arrayOf("application/pdf"))
        }

        binding.btnSort.setOnClickListener {
            GlassDialogs.showSortDialog(this, sortOrder) { chosen ->
                sortOrder = chosen
                store.setSortOrder(chosen)
                updateSortAndDatePills()
                renderList()
            }
        }

        binding.btnDateToggle.setOnClickListener {
            dateGrouped = !dateGrouped
            store.setDateGrouped(dateGrouped)
            updateSortAndDatePills()
            renderList()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                binding.btnClearSearch.visibility =
                    if (searchQuery.isEmpty()) View.GONE else View.VISIBLE
                renderList()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    private fun updateSortAndDatePills() {
        binding.tvSortLabel.text = when (sortOrder) {
            SortOrder.DATE_DESC -> "Newest"
            SortOrder.DATE_ASC -> "Oldest"
            SortOrder.NAME_ASC -> "A–Z"
            SortOrder.NAME_DESC -> "Z–A"
            SortOrder.SIZE_DESC -> "Largest"
            SortOrder.SIZE_ASC -> "Smallest"
        }

        if (dateGrouped) {
            binding.btnDateToggle.setBackgroundResource(R.drawable.bg_category_tag)
            binding.tvDateToggle.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))
        } else {
            binding.btnDateToggle.setBackgroundResource(R.drawable.bg_glass_pill)
            binding.tvDateToggle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    private fun updatePermissionBanner() {
        val granted = PdfScanner.hasFullAutoScanPermission(this)
        binding.permissionBanner.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun requestAutoScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = runCatching {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            }.getOrElse {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
            manageFilesLauncher.launch(intent)
        } else {
            legacyPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun scanLibrary() {
        binding.progressScan.visibility = if (allPdfs.isEmpty()) View.VISIBLE else View.GONE
        binding.tvSubtitle.text = getString(R.string.subtitle_scanning)

        lifecycleScope.launch {
            val scanned = PdfScanner.scanAllPdfs(this@MainActivity, store)
            allPdfs = scanned
            binding.progressScan.visibility = View.GONE
            renderList()
        }
    }

    private fun renderList() {
        val categories = store.getCategories()
        val categoryMap = store.getCategoryMap()

        // Build category counts
        val countsByCat = HashMap<String, Int>()
        var uncategorizedCount = 0
        allPdfs.forEach { pdf ->
            val cat = categoryMap[pdf.key]
            if (cat.isNullOrBlank()) {
                uncategorizedCount++
            } else {
                countsByCat[cat] = (countsByCat[cat] ?: 0) + 1
            }
        }

        val chips = buildList {
            add(CategoryChip.Filter(ALL_CATEGORY, selectedCategory == ALL_CATEGORY, allPdfs.size))
            add(
                CategoryChip.Filter(
                    UNCATEGORIZED_CATEGORY,
                    selectedCategory == UNCATEGORIZED_CATEGORY,
                    uncategorizedCount
                )
            )
            categories.forEach { cat ->
                add(
                    CategoryChip.Filter(
                        name = cat,
                        isSelected = selectedCategory.equals(cat, ignoreCase = true),
                        count = countsByCat[cat] ?: 0
                    )
                )
            }
            add(CategoryChip.AddNew)
        }
        categoryAdapter.submitList(chips)

        // Filter by category and search
        val filtered = allPdfs.filter { item ->
            val itemCat = categoryMap[item.key]
            val matchesCat = when (selectedCategory) {
                ALL_CATEGORY -> true
                UNCATEGORIZED_CATEGORY -> itemCat.isNullOrBlank()
                else -> itemCat.equals(selectedCategory, ignoreCase = true)
            }
            val matchesQuery = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }

        // Sort
        val sorted = when (sortOrder) {
            SortOrder.DATE_DESC -> filtered.sortedByDescending { it.dateModifiedMillis }
            SortOrder.DATE_ASC -> filtered.sortedBy { it.dateModifiedMillis }
            SortOrder.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            SortOrder.NAME_DESC -> filtered.sortedByDescending { it.displayName.lowercase() }
            SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
            SortOrder.SIZE_ASC -> filtered.sortedBy { it.sizeBytes }
        }

        // Build rows (date-grouped or flat)
        val rows: List<PdfListRow> = if (dateGrouped &&
            (sortOrder == SortOrder.DATE_DESC || sortOrder == SortOrder.DATE_ASC)
        ) {
            val grouped = LinkedHashMap<String, MutableList<PdfItem>>()
            sorted.forEach { item ->
                val label = PdfFormatters.dateSectionLabel(item.dateModifiedMillis)
                grouped.getOrPut(label) { mutableListOf() }.add(item)
            }
            buildList<PdfListRow> {
                grouped.forEach { (label, items) ->
                    add(PdfListRow.DateHeader(label, items.size))
                    items.forEach { item ->
                        add(PdfListRow.PdfEntry(item, categoryMap[item.key]))
                    }
                }
            }
        } else {
            sorted.map { item -> PdfListRow.PdfEntry(item, categoryMap[item.key]) }
        }

        pdfAdapter.submitList(rows)

        val totalSize = PdfFormatters.formatSize(sorted.sumOf { it.sizeBytes })
        binding.tvSubtitle.text = "${sorted.size} PDF${if (sorted.size == 1) "" else "s"} • $totalSize"
        binding.emptyState.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openReader(item: PdfItem) {
        val intent = Intent(this, PdfReaderActivity::class.java).apply {
            putExtra(PdfReaderActivity.EXTRA_URI, item.uri.toString())
            putExtra(PdfReaderActivity.EXTRA_KEY, item.key)
            putExtra(PdfReaderActivity.EXTRA_PATH, item.filePath)
            putExtra(PdfReaderActivity.EXTRA_TITLE, item.displayName)
        }
        startActivity(intent)
    }

    private fun showPdfActions(item: PdfItem) {
        val currentCategory = store.getCategoryMap()[item.key]
        GlassDialogs.showPdfActionsDialog(
            context = this,
            item = item,
            currentCategory = currentCategory,
            onOpen = { openReader(item) },
            onCategorize = { showMoveCategory(item, currentCategory) },
            onShare = { sharePdf(item) },
            onDelete = { confirmDeletePdf(item) }
        )
    }

    private fun showMoveCategory(item: PdfItem, currentCategory: String?) {
        GlassDialogs.showCategoryPickerDialog(
            context = this,
            categories = store.getCategories(),
            currentCategory = currentCategory,
            onPickCategory = { chosen ->
                store.setPdfCategory(item.key, chosen)
                renderList()
            },
            onCreateNewCategory = {
                GlassDialogs.showNewCategoryDialog(this) { newCat ->
                    store.addCategory(newCat)
                    store.setPdfCategory(item.key, newCat)
                    renderList()
                }
            }
        )
    }

    private fun sharePdf(item: PdfItem) {
        val shareUri = if (!item.filePath.isNullOrBlank() && File(item.filePath).exists()) {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", File(item.filePath))
        } else {
            item.uri
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    private fun confirmDeletePdf(item: PdfItem) {
        val body = if (item.source == PdfSource.IMPORTED) {
            getString(R.string.delete_confirm_imported, item.name)
        } else {
            getString(R.string.delete_confirm_body, item.name)
        }
        GlassDialogs.showDeleteConfirmDialog(
            context = this,
            title = getString(R.string.delete_confirm_title),
            body = body,
            confirmLabel = if (item.source == PdfSource.IMPORTED) "Remove" else getString(R.string.delete_action)
        ) {
            lifecycleScope.launch {
                val ok = PdfScanner.deletePdf(this@MainActivity, item, store)
                if (ok) {
                    allPdfs = allPdfs.filterNot { it.key == item.key }
                    renderList()
                    Toast.makeText(this@MainActivity, "Removed", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Could not delete file. Grant file access in settings.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    companion object {
        private const val ALL_CATEGORY = "All"
        private const val UNCATEGORIZED_CATEGORY = "Uncategorized"
    }
}
