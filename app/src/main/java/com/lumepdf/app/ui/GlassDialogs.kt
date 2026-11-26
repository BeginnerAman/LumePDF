package com.lumepdf.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lumepdf.app.R
import com.lumepdf.app.databinding.DialogGlassConfirmBinding
import com.lumepdf.app.databinding.DialogGlassInputBinding
import com.lumepdf.app.databinding.DialogGlassListBinding
import com.lumepdf.app.model.PdfItem
import com.lumepdf.app.model.PdfSource
import com.lumepdf.app.model.SortOrder

object GlassDialogs {

    private fun createGlassDialog(context: Context, view: View): Dialog {
        return Dialog(context, R.style.Theme_LumePDF_GlassDialog).apply {
            setContentView(view)
            window?.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
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
            val padV = (12 * context.resources.displayMetrics.density).toInt()
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
        onShare: () -> Unit,
        onDelete: () -> Unit
    ) {
        val binding = DialogGlassListBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.tvDialogTitle.text = item.displayName
        binding.tvDialogSubtitle.visibility = View.VISIBLE
        binding.tvDialogSubtitle.text = currentCategory?.let { "Category: $it" } ?: "Uncategorized"

        addOptionRow(context, binding.listContainer, "Open & Read") {
            dialog.dismiss()
            onOpen()
        }
        addOptionRow(context, binding.listContainer, "Move to Category") {
            dialog.dismiss()
            onCategorize()
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
        categories: List<String>,
        currentCategory: String?,
        onPickCategory: (String?) -> Unit,
        onCreateNewCategory: () -> Unit
    ) {
        val binding = DialogGlassListBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.tvDialogTitle.text = "Move to Category"

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
                isSelected = cat.equals(currentCategory, ignoreCase = true)
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

    fun showNewCategoryDialog(
        context: Context,
        onCreate: (String) -> Unit
    ) {
        val binding = DialogGlassInputBinding.inflate(LayoutInflater.from(context))
        val dialog = createGlassDialog(context, binding.root)
        binding.tvInputTitle.text = "New Category"
        binding.btnInputCancel.setOnClickListener { dialog.dismiss() }
        binding.btnInputSave.setOnClickListener {
            val text = binding.etInput.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                dialog.dismiss()
                onCreate(text)
            }
        }
        dialog.show()
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
