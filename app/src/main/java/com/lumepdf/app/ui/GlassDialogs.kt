package com.lumepdf.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lumepdf.app.R
import com.lumepdf.app.data.PdfStore
import com.lumepdf.app.databinding.DialogGlassConfirmBinding
import com.lumepdf.app.databinding.DialogGlassInputBinding
import com.lumepdf.app.databinding.DialogGlassListBinding
import com.lumepdf.app.databinding.DialogGlassMultiPickBinding
import com.lumepdf.app.databinding.DialogGlassThemeBinding
import com.lumepdf.app.databinding.DialogGlassWelcomeBinding
import com.lumepdf.app.databinding.ItemPickPdfBinding
import com.lumepdf.app.model.PdfFormatters
import com.lumepdf.app.model.PdfItem
import com.lumepdf.app.model.PdfSource
import com.lumepdf.app.model.SortOrder
import kotlin.math.min

object GlassDialogs {

    private fun resolvePalette(context: Context): ThemeManager.ThemePalette {
        val theme = ThemeManager.AppTheme.fromId(PdfStore(context).getAppThemeId())
        return ThemeManager.getPalette(context, theme)
    }

    private fun createGlassDialog(context: Context, view: View): Dialog {
        val dm = context.resources.displayMetrics
        val maxW = (500 * dm.density).toInt()
        val width = min((dm.widthPixels * 0.92f).toInt(), maxW)
        return Dialog(context, R.style.Theme_LumePDF_GlassDialog).apply {
            setContentView(view)
            window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            window?.setGravity(Gravity.CENTER)
        }
    }

    private fun addOptionRow(
        context: Context,
        container: LinearLayout,
        palette: ThemeManager.ThemePalette,
        label: String,
        isSelected: Boolean = false,
        isDestructive: Boolean = false,
        onClick: () -> Unit
    ) {
        val tv = TextView(context).apply {
            text = if (isSelected) "✓  $label" else label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(
                when {
                    isDestructive -> palette.accentRed
                    isSelected -> palette.accent
                    else -> palette.textPrimary
                }
            )
            background = if (isSelected) {
                ThemeManager.categoryTagDrawable(context, palette)
            } else {
                ThemeManager.pillDrawable(context, palette)
            }
            val padH = (16 * context.resources.displayMetrics.density).toInt()
            val padV = (11 * context.resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * context.resources.displayMetrics.density).toInt()
            }
            layoutParams = lp
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        container.addView(tv)
    }

    fun showThemePickerDialog(
        context: Context,
        currentTheme: ThemeManager.AppTheme,
        onThemeSelected: (ThemeManager.AppTheme) -> Unit
    ) {
        val binding = DialogGlassThemeBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        var selectedTheme = currentTheme

        fun renderThemeRows(animateSheet: Boolean) {
            val palette = ThemeManager.getPalette(context, selectedTheme)
            if (animateSheet) {
                binding.themeSheetRoot.animate().alpha(0.82f).setDuration(90L).withEndAction {
                    binding.themeSheetRoot.background = ThemeManager.sheetDrawable(context, palette)
                    binding.themeSheetRoot.animate().alpha(1f).setDuration(180L).start()
                }.start()
            } else {
                binding.themeSheetRoot.background = ThemeManager.sheetDrawable(context, palette)
            }
            binding.tvThemeTitle.setTextColor(palette.textPrimary)
            binding.tvThemeSubtitle.setTextColor(palette.textSecondary)
            binding.btnThemeDone.background = ThemeManager.fabDrawable(context, palette)

            binding.themeOptionsContainer.removeAllViews()
            val d = context.resources.displayMetrics.density

            ThemeManager.AppTheme.entries.forEach { theme ->
                val isSelected = theme == selectedTheme
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = ThemeManager.themeCardDrawable(context, palette, isSelected)
                    setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (9 * d).toInt()
                    }
                }

                val swatch = View(context).apply {
                    background = ThemeManager.swatchOrbDrawable(context, theme)
                    layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt())
                }

                val textCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        marginStart = (12 * d).toInt()
                        marginEnd = (10 * d).toInt()
                    }
                }

                val tvTitle = TextView(context).apply {
                    text = theme.title
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(if (isSelected) palette.accent else palette.textPrimary)
                }

                val tvTagline = TextView(context).apply {
                    text = theme.tagline
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(palette.textSecondary)
                }

                textCol.addView(tvTitle)
                textCol.addView(tvTagline)

                val check = ImageView(context).apply {
                    val size = (22 * d).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size)
                    setPadding((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                    if (isSelected) {
                        background = ThemeManager.checkCircleOnDrawable(palette)
                        setImageResource(R.drawable.ic_check)
                    } else {
                        background = ThemeManager.checkCircleOffDrawable(context, palette)
                        setImageDrawable(null)
                    }
                }

                row.addView(swatch)
                row.addView(textCol)
                row.addView(check)

                row.setOnClickListener {
                    ThemeManager.performThemeSwitchHaptic(row)
                    if (selectedTheme != theme) {
                        selectedTheme = theme
                        renderThemeRows(animateSheet = true)
                        onThemeSelected(theme)
                    }
                }

                binding.themeOptionsContainer.addView(row)
            }
        }

        binding.btnThemeDone.setOnClickListener { dialog.dismiss() }
        renderThemeRows(animateSheet = false)
        dialog.show()
    }

    fun showWelcomeDialog(
        context: Context,
        palette: ThemeManager.ThemePalette,
        onChooseTheme: () -> Unit
    ) {
        val binding = DialogGlassWelcomeBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)

        binding.welcomeSheetRoot.background = ThemeManager.sheetDrawable(context, palette)
        binding.welcomeEmblemBg.background = ThemeManager.fabDrawable(context, palette)
        binding.tvWelcomeTitle.setTextColor(palette.textPrimary)
        binding.tvWelcomeSubtitle.setTextColor(palette.accent)
        binding.tvWelcomeBody.setTextColor(palette.textSecondary)
        binding.btnWelcomeTheme.background = ThemeManager.pillDrawable(context, palette)
        binding.btnWelcomeTheme.setTextColor(palette.textPrimary)
        binding.btnWelcomeStart.background = ThemeManager.fabDrawable(context, palette)

        binding.btnWelcomeTheme.setOnClickListener {
            ThemeManager.performThemeSwitchHaptic(it)
            dialog.dismiss()
            onChooseTheme()
        }
        binding.btnWelcomeStart.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    fun showSortDialog(
        context: Context,
        current: SortOrder,
        palette: ThemeManager.ThemePalette = resolvePalette(context),
        onSelected: (SortOrder) -> Unit
    ) {
        val binding = DialogGlassListBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.root.background = ThemeManager.sheetDrawable(context, palette)
        binding.tvDialogTitle.text = "Sort PDFs"
        binding.tvDialogTitle.setTextColor(palette.textPrimary)

        SortOrder.entries.forEach { order ->
            addOptionRow(
                context = context,
                container = binding.listContainer,
                palette = palette,
                label = order.label,
                isSelected = order == current
            ) {
                dialog.dismiss()
                onSelected(order)
            }
        }
        dialog.show()
    }

    fun showPdfActionsDialog(
        context: Context,
        item: PdfItem,
        currentCategory: String?,
        palette: ThemeManager.ThemePalette = resolvePalette(context),
        onOpen: () -> Unit,
        onCategorize: () -> Unit,
        onRename: () -> Unit,
        onShare: () -> Unit,
        onDelete: () -> Unit
    ) {
        val binding = DialogGlassListBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.root.background = ThemeManager.sheetDrawable(context, palette)
        binding.tvDialogTitle.text = item.displayName
        binding.tvDialogTitle.setTextColor(palette.textPrimary)
        binding.tvDialogSubtitle.visibility = View.VISIBLE
        binding.tvDialogSubtitle.text = currentCategory?.let { "Category: $it" } ?: "Other"
        binding.tvDialogSubtitle.setTextColor(palette.textSecondary)

        addOptionRow(context, binding.listContainer, palette, "Open & Read") {
            dialog.dismiss()
            onOpen()
        }
        addOptionRow(context, binding.listContainer, palette, "Move to Category") {
            dialog.dismiss()
            onCategorize()
        }
        addOptionRow(context, binding.listContainer, palette, "Rename PDF") {
            dialog.dismiss()
            onRename()
        }
        addOptionRow(context, binding.listContainer, palette, "Share PDF") {
            dialog.dismiss()
            onShare()
        }
        addOptionRow(
            context = context,
            container = binding.listContainer,
            palette = palette,
            label = if (item.source == PdfSource.IMPORTED) "Remove from LumePDF" else "Delete PDF",
            isDestructive = true
        ) {
            dialog.dismiss()
            onDelete()
        }
        dialog.show()
    }

    fun showCategoryPickerDialog(
        context: Context,
        title: String = "Move to Category",
        categories: List<String>,
        currentCategory: String?,
        palette: ThemeManager.ThemePalette = resolvePalette(context),
        onPickCategory: (String?) -> Unit,
        onCreateNewCategory: () -> Unit
    ) {
        val binding = DialogGlassListBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.root.background = ThemeManager.sheetDrawable(context, palette)
        binding.tvDialogTitle.text = title
        binding.tvDialogTitle.setTextColor(palette.textPrimary)

        addOptionRow(
            context = context,
            container = binding.listContainer,
            palette = palette,
            label = context.getString(R.string.uncategorized),
            isSelected = currentCategory == null
        ) {
            dialog.dismiss()
            onPickCategory(null)
        }

        categories.forEach { cat ->
            addOptionRow(
                context = context,
                container = binding.listContainer,
                palette = palette,
                label = cat,
                isSelected = currentCategory != null && cat.equals(currentCategory, ignoreCase = true)
            ) {
                dialog.dismiss()
                onPickCategory(cat)
            }
        }

        addOptionRow(
            context = context,
            container = binding.listContainer,
            palette = palette,
            label = "+ Create New Category"
        ) {
            dialog.dismiss()
            onCreateNewCategory()
        }
        dialog.show()
    }

    fun showAssignCategoryDialog(
        context: Context,
        targetCategory: String,
        allPdfs: List<PdfItem>,
        categoryMap: Map<String, String>,
        palette: ThemeManager.ThemePalette = resolvePalette(context),
        onApply: (Set<String>) -> Unit
    ) {
        val binding = DialogGlassMultiPickBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.root.background = ThemeManager.sheetDrawable(context, palette)
        binding.tvPickTitle.setTextColor(palette.textPrimary)
        binding.tvPickSubtitle.setTextColor(palette.textSecondary)
        binding.btnPickSelectAll.background = ThemeManager.pillDrawable(context, palette)
        binding.btnPickSelectAll.setTextColor(palette.accent)
        binding.btnPickCancel.background = ThemeManager.pillDrawable(context, palette)
        binding.btnPickCancel.setTextColor(palette.textSecondary)
        binding.btnPickConfirm.background = ThemeManager.activeChipDrawable(context, palette)
        binding.btnPickConfirm.setTextColor(Color.WHITE)

        val dm = context.resources.displayMetrics
        val listHeight = min((290 * dm.density).toInt(), (dm.heightPixels * 0.38f).toInt())
        binding.rvPickPdfs.layoutParams = binding.rvPickPdfs.layoutParams.apply {
            height = listHeight
        }

        val selected = HashSet<String>()
        allPdfs.forEach { pdf ->
            if (categoryMap[pdf.key].equals(targetCategory, ignoreCase = true)) {
                selected.add(pdf.key)
            }
        }

        var filtered = allPdfs.sortedByDescending { it.dateModifiedMillis }

        binding.tvPickTitle.text = "Add to $targetCategory"

        fun updateLabels() {
            binding.tvPickSubtitle.text = "${selected.size} selected of ${allPdfs.size} PDFs"
            val allVisibleSelected =
                filtered.isNotEmpty() && filtered.all { selected.contains(it.key) }
            binding.btnPickSelectAll.text = if (allVisibleSelected) "Clear" else "Select All"
            binding.btnPickConfirm.text = if (selected.isEmpty()) "Save" else "Save (${selected.size})"
        }

        class PickVH(val itemBinding: ItemPickPdfBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        val pickAdapter = object : RecyclerView.Adapter<PickVH>() {
            override fun getItemCount(): Int = filtered.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PickVH {
                val itemBinding = ItemPickPdfBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                return PickVH(itemBinding)
            }

            override fun onBindViewHolder(holder: PickVH, position: Int) {
                val pdf = filtered[position]
                val itemBinding = holder.itemBinding
                itemBinding.root.background = ThemeManager.cardDrawable(context, palette)
                itemBinding.tvPickName.text = pdf.displayName
                itemBinding.tvPickName.setTextColor(palette.textPrimary)
                val existingCat = categoryMap[pdf.key]
                val catLabel = existingCat ?: "Other"
                val sizeStr = PdfFormatters.formatSize(pdf.sizeBytes)
                itemBinding.tvPickMeta.text = "$catLabel • $sizeStr"
                itemBinding.tvPickMeta.setTextColor(palette.textSecondary)

                val checked = selected.contains(pdf.key)
                if (checked) {
                    itemBinding.ivPickCheck.background = ThemeManager.checkCircleOnDrawable(palette)
                    itemBinding.ivPickCheck.setImageResource(R.drawable.ic_check)
                } else {
                    itemBinding.ivPickCheck.background = ThemeManager.checkCircleOffDrawable(context, palette)
                    itemBinding.ivPickCheck.setImageDrawable(null)
                }

                itemBinding.root.setOnClickListener {
                    if (selected.contains(pdf.key)) {
                        selected.remove(pdf.key)
                    } else {
                        selected.add(pdf.key)
                    }
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos)
                    updateLabels()
                }
            }
        }

        binding.rvPickPdfs.layoutManager = LinearLayoutManager(context)
        binding.rvPickPdfs.adapter = pickAdapter

        binding.etPickSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim().orEmpty()
                filtered = allPdfs
                    .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
                    .sortedByDescending { it.dateModifiedMillis }
                pickAdapter.notifyDataSetChanged()
                updateLabels()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnPickSelectAll.setOnClickListener {
            val allVisibleSelected =
                filtered.isNotEmpty() && filtered.all { selected.contains(it.key) }
            if (allVisibleSelected) {
                filtered.forEach { selected.remove(it.key) }
            } else {
                filtered.forEach { selected.add(it.key) }
            }
            pickAdapter.notifyDataSetChanged()
            updateLabels()
        }

        binding.btnPickCancel.setOnClickListener { dialog.dismiss() }
        binding.btnPickConfirm.setOnClickListener {
            dialog.dismiss()
            onApply(selected)
        }

        updateLabels()
        dialog.show()
    }

    private fun showInputDialog(
        context: Context,
        title: String,
        hint: String,
        initialValue: String = "",
        maxLen: Int = 24,
        palette: ThemeManager.ThemePalette = resolvePalette(context),
        onSubmit: (String) -> Unit
    ) {
        val binding = DialogGlassInputBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.root.background = ThemeManager.sheetDrawable(context, palette)
        binding.tvInputTitle.text = title
        binding.tvInputTitle.setTextColor(palette.textPrimary)
        binding.etInput.background = ThemeManager.pillDrawable(context, palette)
        binding.etInput.setTextColor(palette.textPrimary)
        binding.etInput.setHintTextColor(palette.textTertiary)
        binding.etInput.hint = hint
        binding.etInput.filters = arrayOf(android.text.InputFilter.LengthFilter(maxLen))
        if (initialValue.isNotEmpty()) {
            binding.etInput.setText(initialValue)
            binding.etInput.setSelection(initialValue.length)
        }
        binding.btnInputCancel.background = ThemeManager.pillDrawable(context, palette)
        binding.btnInputCancel.setTextColor(palette.textSecondary)
        binding.btnInputSave.background = ThemeManager.activeChipDrawable(context, palette)
        binding.btnInputSave.setTextColor(Color.WHITE)

        binding.btnInputCancel.setOnClickListener { dialog.dismiss() }
        binding.btnInputSave.setOnClickListener {
            val text = binding.etInput.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                dialog.dismiss()
                onSubmit(text)
            }
        }
        dialog.show()
    }

    fun showNewCategoryDialog(
        context: Context,
        palette: ThemeManager.ThemePalette = resolvePalette(context),
        onCreate: (String) -> Unit
    ) {
        showInputDialog(
            context = context,
            title = "New Category",
            hint = "Category name",
            palette = palette,
            onSubmit = onCreate
        )
    }

    fun showRenamePdfDialog(
        context: Context,
        currentName: String,
        palette: ThemeManager.ThemePalette = resolvePalette(context),
        onRename: (String) -> Unit
    ) {
        showInputDialog(
            context = context,
            title = "Rename PDF",
            hint = "File name",
            initialValue = currentName,
            maxLen = 80,
            palette = palette,
            onSubmit = onRename
        )
    }

    fun showDeleteConfirmDialog(
        context: Context,
        title: String,
        body: String,
        confirmLabel: String = context.getString(R.string.delete_action),
        palette: ThemeManager.ThemePalette = resolvePalette(context),
        onConfirm: () -> Unit
    ) {
        val binding = DialogGlassConfirmBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.root.background = ThemeManager.sheetDrawable(context, palette)
        binding.tvConfirmTitle.text = title
        binding.tvConfirmTitle.setTextColor(palette.textPrimary)
        binding.tvConfirmBody.text = body
        binding.tvConfirmBody.setTextColor(palette.textSecondary)
        binding.btnConfirmCancel.background = ThemeManager.pillDrawable(context, palette)
        binding.btnConfirmCancel.setTextColor(palette.textSecondary)
        binding.btnConfirmDelete.text = confirmLabel
        binding.btnConfirmDelete.setTextColor(palette.accentRed)
        binding.btnConfirmCancel.setOnClickListener { dialog.dismiss() }
        binding.btnConfirmDelete.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
    }
}
