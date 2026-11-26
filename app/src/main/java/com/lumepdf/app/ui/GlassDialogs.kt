package com.lumepdf.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lumepdf.app.R
import com.lumepdf.app.databinding.DialogGlassConfirmBinding
import com.lumepdf.app.databinding.DialogGlassInputBinding
import com.lumepdf.app.databinding.DialogGlassListBinding
import com.lumepdf.app.databinding.DialogGlassMultiPickBinding
import com.lumepdf.app.databinding.ItemPickPdfBinding
import com.lumepdf.app.model.PdfFormatters
import com.lumepdf.app.model.PdfItem
import com.lumepdf.app.model.PdfSource
import com.lumepdf.app.model.SortOrder
import kotlin.math.min

object GlassDialogs {

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
                    isDestructive -> ContextCompat.getColor(context, R.color.accent_red)
                    isSelected -> ContextCompat.getColor(context, R.color.accent_blue)
                    else -> ContextCompat.getColor(context, R.color.text_primary)
                }
            )
            setBackgroundResource(
                if (isSelected) R.drawable.bg_category_tag else R.drawable.bg_glass_pill
            )
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

    fun showSortDialog(
        context: Context,
        current: SortOrder,
        onSelected: (SortOrder) -> Unit
    ) {
        val binding = DialogGlassListBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.tvDialogTitle.text = "Sort PDFs"

        SortOrder.entries.forEach { order ->
            addOptionRow(
                context = context,
                container = binding.listContainer,
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
        onOpen: () -> Unit,
        onCategorize: () -> Unit,
        onRename: () -> Unit,
        onShare: () -> Unit,
        onDelete: () -> Unit
    ) {
        val binding = DialogGlassListBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.tvDialogTitle.text = item.displayName
        binding.tvDialogSubtitle.visibility = View.VISIBLE
        binding.tvDialogSubtitle.text = currentCategory?.let { "Category: $it" } ?: "Other"

        addOptionRow(context, binding.listContainer, "Open & Read") {
            dialog.dismiss()
            onOpen()
        }
        addOptionRow(context, binding.listContainer, "Move to Category") {
            dialog.dismiss()
            onCategorize()
        }
        addOptionRow(context, binding.listContainer, "Rename PDF") {
            dialog.dismiss()
            onRename()
        }
        addOptionRow(context, binding.listContainer, "Share PDF") {
            dialog.dismiss()
            onShare()
        }
        addOptionRow(
            context = context,
            container = binding.listContainer,
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
        onPickCategory: (String?) -> Unit,
        onCreateNewCategory: () -> Unit
    ) {
        val binding = DialogGlassListBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.tvDialogTitle.text = title

        addOptionRow(
            context = context,
            container = binding.listContainer,
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
        onApply: (Set<String>) -> Unit
    ) {
        val binding = DialogGlassMultiPickBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)

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
                itemBinding.tvPickName.text = pdf.displayName
                val existingCat = categoryMap[pdf.key]
                val catLabel = existingCat ?: "Other"
                val sizeStr = PdfFormatters.formatSize(pdf.sizeBytes)
                itemBinding.tvPickMeta.text = "$catLabel • $sizeStr"

                val checked = selected.contains(pdf.key)
                if (checked) {
                    itemBinding.ivPickCheck.setBackgroundResource(R.drawable.bg_check_circle_on)
                    itemBinding.ivPickCheck.setImageResource(R.drawable.ic_check)
                } else {
                    itemBinding.ivPickCheck.setBackgroundResource(R.drawable.bg_check_circle_off)
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
        onSubmit: (String) -> Unit
    ) {
        val binding = DialogGlassInputBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.tvInputTitle.text = title
        binding.etInput.hint = hint
        binding.etInput.filters = arrayOf(android.text.InputFilter.LengthFilter(maxLen))
        if (initialValue.isNotEmpty()) {
            binding.etInput.setText(initialValue)
            binding.etInput.setSelection(initialValue.length)
        }
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
        onCreate: (String) -> Unit
    ) {
        showInputDialog(
            context = context,
            title = "New Category",
            hint = "Category name",
            onSubmit = onCreate
        )
    }

    fun showRenamePdfDialog(
        context: Context,
        currentName: String,
        onRename: (String) -> Unit
    ) {
        showInputDialog(
            context = context,
            title = "Rename PDF",
            hint = "File name",
            initialValue = currentName,
            maxLen = 80,
            onSubmit = onRename
        )
    }

    fun showDeleteConfirmDialog(
        context: Context,
        title: String,
        body: String,
        confirmLabel: String = context.getString(R.string.delete_action),
        onConfirm: () -> Unit
    ) {
        val binding = DialogGlassConfirmBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.tvConfirmTitle.text = title
        binding.tvConfirmBody.text = body
        binding.btnConfirmDelete.text = confirmLabel
        binding.btnConfirmCancel.setOnClickListener { dialog.dismiss() }
        binding.btnConfirmDelete.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
    }
}
