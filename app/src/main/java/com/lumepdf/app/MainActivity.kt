package com.lumepdf.app

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
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
    private var currentVisiblePdfs: List<PdfItem> = emptyList()
    private var selectedCategory: String = ALL_CATEGORY
    private var searchQuery: String = ""
    private var sortOrder: SortOrder = SortOrder.DATE_DESC
    private var dateGrouped: Boolean = true

    private var isMultiSelectMode: Boolean = false
    private val selectedKeys = LinkedHashSet<String>()

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                isMultiSelectMode -> exitMultiSelect()
                searchQuery.isNotEmpty() -> binding.etSearch.setText("")
            }
        }
    }

    private val pickPdfsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                store.addImportedUri(uri.toString())
            }
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

        if (savedInstanceState != null) {
            selectedCategory = savedInstanceState.getString(STATE_CATEGORY, ALL_CATEGORY)
            searchQuery = savedInstanceState.getString(STATE_SEARCH, "")
        }

        applyEdgeToEdgeInsets()
        onBackPressedDispatcher.addCallback(this, backCallback)
        setupRecyclerViews()
        setupControls()
        updatePermissionBanner()
        updateSortAndDatePills()
        updateFloatingControls()
        loadInitialLibrary()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CATEGORY, selectedCategory)
        outState.putString(STATE_SEARCH, searchQuery)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBanner()
        val hasFullAccess = PdfScanner.hasFullAutoScanPermission(this)
        if (hasFullAccess != store.getLastScanHadFullAccess()) {
            scanLibrary(fullWalk = true)
        } else if (allPdfs.isNotEmpty()) {
            // Refresh reading progress indicators after returning from PdfReaderActivity
            renderList()
        }
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }
    }

    private fun setupRecyclerViews() {
        categoryAdapter = CategoryChipAdapter(
            onChipClick = { chip ->
                when (chip) {
                    is CategoryChip.Filter -> {
                        selectedCategory = chip.name
                        exitMultiSelect()
                        renderList()
                    }

                    CategoryChip.AddNew -> {
                        GlassDialogs.showNewCategoryDialog(this) { newName ->
                            if (store.addCategory(newName)) {
                                selectedCategory = newName
                                exitMultiSelect()
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
                        body = "Remove “$catName”? PDFs inside it will move to Other."
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
            onMoreClick = { item -> showPdfActions(item) },
            onToggleSelect = { item -> toggleItemSelection(item) },
            onEnterSelection = { item ->
                isMultiSelectMode = true
                selectedKeys.clear()
                selectedKeys.add(item.key)
                updateMultiSelectUi()
            }
        )

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        binding.rvPdfs.apply {
            layoutManager = if (isLandscape) {
                GridLayoutManager(this@MainActivity, 2).apply {
                    spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            return if (pdfAdapter.getItemViewType(position) == PdfListAdapter.TYPE_HEADER) {
                                2
                            } else {
                                1
                            }
                        }
                    }
                }
            } else {
                LinearLayoutManager(this@MainActivity)
            }
            adapter = pdfAdapter
        }
    }

    private fun setupControls() {
        binding.btnRefresh.setOnClickListener { scanLibrary() }
        binding.btnGrantAccess.setOnClickListener { requestAutoScanPermission() }

        binding.fabAddPdf.setOnClickListener { handlePrimaryAddClick() }
        binding.btnEmptyAction.setOnClickListener { handlePrimaryAddClick() }

        binding.btnSelectMode.setOnClickListener {
            if (isMultiSelectMode) {
                exitMultiSelect()
            } else {
                isMultiSelectMode = true
                selectedKeys.clear()
                updateMultiSelectUi()
            }
        }

        binding.btnMultiSelectAll.setOnClickListener {
            val allSelected = currentVisiblePdfs.isNotEmpty() &&
                currentVisiblePdfs.all { selectedKeys.contains(it.key) }
            if (allSelected) {
                selectedKeys.clear()
            } else {
                selectedKeys.clear()
                currentVisiblePdfs.forEach { selectedKeys.add(it.key) }
            }
            updateMultiSelectUi()
        }

        binding.btnMultiMove.setOnClickListener {
            if (selectedKeys.isEmpty()) {
                Toast.makeText(this, "Select at least one PDF", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showMoveMultipleCategory(selectedKeys.toList())
        }

        binding.btnMultiDelete.setOnClickListener {
            if (selectedKeys.isEmpty()) {
                Toast.makeText(this, "Select at least one PDF", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            confirmDeleteMultiple(selectedKeys.toList())
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

        if (searchQuery.isNotEmpty()) {
            binding.etSearch.setText(searchQuery)
            binding.btnClearSearch.visibility = View.VISIBLE
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

    private fun isSpecificCategorySelected(): Boolean {
        return selectedCategory != ALL_CATEGORY && selectedCategory != UNCATEGORIZED_CATEGORY
    }

    private fun handlePrimaryAddClick() {
        if (isSpecificCategorySelected()) {
            if (allPdfs.isEmpty()) {
                Toast.makeText(this, "No PDFs in library yet. Import PDFs first.", Toast.LENGTH_SHORT)
                    .show()
                pickPdfsLauncher.launch(arrayOf("application/pdf"))
                return
            }
            val targetCategory = selectedCategory
            GlassDialogs.showAssignCategoryDialog(
                context = this,
                targetCategory = targetCategory,
                allPdfs = allPdfs,
                categoryMap = store.getCategoryMap()
            ) { chosenKeys ->
                val currentMap = store.getCategoryMap()
                allPdfs.forEach { pdf ->
                    val wasInTarget = currentMap[pdf.key].equals(targetCategory, ignoreCase = true)
                    val isNowSelected = chosenKeys.contains(pdf.key)
                    if (isNowSelected && !wasInTarget) {
                        store.setPdfCategory(pdf.key, targetCategory)
                    } else if (!isNowSelected && wasInTarget) {
                        store.setPdfCategory(pdf.key, null)
                    }
                }
                renderList()
            }
        } else {
            pickPdfsLauncher.launch(arrayOf("application/pdf"))
        }
    }

    private fun toggleItemSelection(item: PdfItem) {
        if (selectedKeys.contains(item.key)) {
            selectedKeys.remove(item.key)
        } else {
            selectedKeys.add(item.key)
        }
        updateMultiSelectUi()
    }

    private fun exitMultiSelect() {
        if (!isMultiSelectMode && selectedKeys.isEmpty()) return
        isMultiSelectMode = false
        selectedKeys.clear()
        updateMultiSelectUi()
    }

    private fun updateMultiSelectUi() {
        pdfAdapter.updateSelectionState(isMultiSelectMode, selectedKeys.toSet())
        updateFloatingControls()
    }

    private fun updateFloatingControls() {
        backCallback.isEnabled = isMultiSelectMode || searchQuery.isNotEmpty()
        if (isMultiSelectMode) {
            binding.btnSelectMode.text = getString(R.string.done_label)
            binding.btnSelectMode.setBackgroundResource(R.drawable.bg_category_tag)
            binding.btnSelectMode.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))
            binding.fabAddPdf.visibility = View.GONE
            binding.multiSelectBar.visibility = View.VISIBLE
            binding.tvMultiCount.text = "${selectedKeys.size} selected"
            val allSelected = currentVisiblePdfs.isNotEmpty() &&
                currentVisiblePdfs.all { selectedKeys.contains(it.key) }
            binding.btnMultiSelectAll.text = if (allSelected) "None" else "All"
        } else {
            binding.btnSelectMode.text = getString(R.string.select_label)
            binding.btnSelectMode.setBackgroundResource(R.drawable.bg_glass_pill)
            binding.btnSelectMode.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            binding.multiSelectBar.visibility = View.GONE
            binding.fabAddPdf.visibility = View.VISIBLE
            binding.tvFabLabel.text = if (isSpecificCategorySelected()) {
                "Add to $selectedCategory"
            } else {
                getString(R.string.add_pdf)
            }
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

    private fun loadInitialLibrary() {
        val cached = store.getCachedPdfs()
        val permissionChanged =
            PdfScanner.hasFullAutoScanPermission(this) != store.getLastScanHadFullAccess()

        if (cached.isNotEmpty()) {
            allPdfs = cached
            renderList()
            scanLibrary(fullWalk = permissionChanged, silent = true)
        } else {
            scanLibrary(fullWalk = true, silent = false)
        }
    }

    private fun scanLibrary(fullWalk: Boolean = true, silent: Boolean = false) {
        if (!silent && allPdfs.isEmpty()) {
            binding.progressScan.visibility = View.VISIBLE
            binding.tvSubtitle.text = getString(R.string.subtitle_scanning)
        }

        lifecycleScope.launch {
            val scanned = PdfScanner.scanAllPdfs(this@MainActivity, store, fullWalk = fullWalk)
            binding.progressScan.visibility = View.GONE
            if (!silent || scanned != allPdfs) {
                allPdfs = scanned
                renderList()
            }
        }
    }

    private fun renderList() {
        val categories = store.getCategories()
        val categoryMap = store.getCategoryMap()

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

        val sorted = when (sortOrder) {
            SortOrder.DATE_DESC -> filtered.sortedByDescending { it.dateModifiedMillis }
            SortOrder.DATE_ASC -> filtered.sortedBy { it.dateModifiedMillis }
            SortOrder.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            SortOrder.NAME_DESC -> filtered.sortedByDescending { it.displayName.lowercase() }
            SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
            SortOrder.SIZE_ASC -> filtered.sortedBy { it.sizeBytes }
        }

        currentVisiblePdfs = sorted
        val lastPages = store.getLastPages()
        val pageCounts = store.getPageCounts()

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
                        add(
                            PdfListRow.PdfEntry(
                                item = item,
                                category = categoryMap[item.key],
                                currentPage = lastPages[item.key] ?: 0,
                                totalPages = pageCounts[item.key] ?: 0
                            )
                        )
                    }
                }
            }
        } else {
            sorted.map { item ->
                PdfListRow.PdfEntry(
                    item = item,
                    category = categoryMap[item.key],
                    currentPage = lastPages[item.key] ?: 0,
                    totalPages = pageCounts[item.key] ?: 0
                )
            }
        }

        pdfAdapter.submitList(rows)

        val totalSize = PdfFormatters.formatSize(sorted.sumOf { it.sizeBytes })
        binding.tvSubtitle.text = "${sorted.size} PDF${if (sorted.size == 1) "" else "s"} • $totalSize"

        updateEmptyState(sorted.isEmpty())
        updateFloatingControls()
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (!isEmpty) {
            binding.emptyState.visibility = View.GONE
            return
        }
        binding.emptyState.visibility = View.VISIBLE
        when {
            searchQuery.isNotEmpty() -> {
                binding.tvEmptyTitle.text = "No Matching PDFs"
                binding.tvEmptySubtitle.text = "No PDFs match “$searchQuery”."
                binding.btnEmptyAction.visibility = View.GONE
            }

            isSpecificCategorySelected() -> {
                binding.tvEmptyTitle.text = "No PDFs in $selectedCategory"
                binding.tvEmptySubtitle.text =
                    "Select PDFs from your library to organize them in $selectedCategory."
                binding.btnEmptyAction.visibility = View.VISIBLE
                binding.btnEmptyAction.text = "+ Add PDFs to $selectedCategory"
            }

            selectedCategory == UNCATEGORIZED_CATEGORY -> {
                binding.tvEmptyTitle.text = "All Caught Up"
                binding.tvEmptySubtitle.text = "Every PDF in your library is already categorized."
                binding.btnEmptyAction.visibility = View.GONE
            }

            else -> {
                binding.tvEmptyTitle.text = getString(R.string.empty_title)
                binding.tvEmptySubtitle.text = getString(R.string.empty_subtitle)
                binding.btnEmptyAction.visibility = View.VISIBLE
                binding.btnEmptyAction.text = "+ Add PDF"
            }
        }
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
            onRename = { showRenamePdf(item) },
            onShare = { sharePdf(item) },
            onDelete = { confirmDeletePdf(item) }
        )
    }

    private fun showRenamePdf(item: PdfItem) {
        GlassDialogs.showRenamePdfDialog(this, item.displayName) { newName ->
            lifecycleScope.launch {
                val res = PdfScanner.renamePdf(this@MainActivity, item, newName, store)
                res.onSuccess { updated ->
                    allPdfs = allPdfs.map { if (it.key == item.key) updated else it }
                    store.saveCachedPdfs(allPdfs)
                    renderList()
                    Toast.makeText(this@MainActivity, "Renamed", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(
                        this@MainActivity,
                        err.message ?: "Unable to rename this PDF",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
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

    private fun showMoveMultipleCategory(keys: List<String>) {
        GlassDialogs.showCategoryPickerDialog(
            context = this,
            title = "Move ${keys.size} PDFs to",
            categories = store.getCategories(),
            currentCategory = null,
            onPickCategory = { chosen ->
                keys.forEach { key -> store.setPdfCategory(key, chosen) }
                exitMultiSelect()
                renderList()
            },
            onCreateNewCategory = {
                GlassDialogs.showNewCategoryDialog(this) { newCat ->
                    store.addCategory(newCat)
                    keys.forEach { key -> store.setPdfCategory(key, newCat) }
                    exitMultiSelect()
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
                    store.saveCachedPdfs(allPdfs)
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

    private fun confirmDeleteMultiple(keys: List<String>) {
        val itemsToDelete = allPdfs.filter { keys.contains(it.key) }
        if (itemsToDelete.isEmpty()) return

        GlassDialogs.showDeleteConfirmDialog(
            context = this,
            title = "Delete ${itemsToDelete.size} PDFs?",
            body = "Selected PDFs will be permanently deleted (imported links will be removed from LumePDF)."
        ) {
            lifecycleScope.launch {
                val deletedKeys = HashSet<String>()
                itemsToDelete.forEach { item ->
                    if (PdfScanner.deletePdf(this@MainActivity, item, store)) {
                        deletedKeys.add(item.key)
                    }
                }
                allPdfs = allPdfs.filterNot { deletedKeys.contains(it.key) }
                store.saveCachedPdfs(allPdfs)
                exitMultiSelect()
                renderList()
                val msg = if (deletedKeys.size == itemsToDelete.size) {
                    "Deleted ${deletedKeys.size} PDFs"
                } else {
                    "Deleted ${deletedKeys.size} of ${itemsToDelete.size} PDFs"
                }
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val ALL_CATEGORY = "All"
        private const val UNCATEGORIZED_CATEGORY = "Other"
        private const val STATE_CATEGORY = "state_category"
        private const val STATE_SEARCH = "state_search"
    }
}
