package com.lumepdf.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lumepdf.app.R
import com.lumepdf.app.databinding.ItemDateHeaderBinding
import com.lumepdf.app.databinding.ItemPdfBinding
import com.lumepdf.app.model.PdfFormatters
import com.lumepdf.app.model.PdfItem
import com.lumepdf.app.model.PdfListRow

class PdfListAdapter(
    private val onOpenPdf: (PdfItem) -> Unit,
    private val onMoreClick: (PdfItem) -> Unit,
    private val onToggleSelect: (PdfItem) -> Unit,
    private val onEnterSelection: (PdfItem) -> Unit
) : ListAdapter<PdfListRow, RecyclerView.ViewHolder>(DiffCallback) {

    var selectionMode: Boolean = false
        private set

    private var selectedKeys: Set<String> = emptySet()

    fun updateSelectionState(enabled: Boolean, keys: Set<String>) {
        val modeChanged = selectionMode != enabled
        selectionMode = enabled
        selectedKeys = keys
        if (modeChanged) {
            notifyDataSetChanged()
        } else {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is PdfListRow.DateHeader -> TYPE_HEADER
            is PdfListRow.PdfEntry -> TYPE_PDF
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(ItemDateHeaderBinding.inflate(inflater, parent, false))
            else -> PdfVH(ItemPdfBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is PdfListRow.DateHeader -> (holder as HeaderVH).bind(row)
            is PdfListRow.PdfEntry -> (holder as PdfVH).bind(row)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_SELECTION) && holder is PdfVH) {
            val row = getItem(position) as? PdfListRow.PdfEntry ?: return
            holder.bindSelection(row.item)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class HeaderVH(private val binding: ItemDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PdfListRow.DateHeader) {
            binding.tvDateHeader.text = row.label
            binding.tvDateCount.text = "${row.count}"
        }
    }

    inner class PdfVH(private val binding: ItemPdfBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PdfListRow.PdfEntry) {
            val item = row.item
            binding.tvPdfName.text = item.displayName

            val dateStr = PdfFormatters.formatShortDate(item.dateModifiedMillis)
            val sizeStr = PdfFormatters.formatSize(item.sizeBytes)
            binding.tvPdfMeta.text = "$dateStr • $sizeStr"

            if (row.category.isNullOrBlank()) {
                binding.tvCategoryBadge.visibility = View.GONE
            } else {
                binding.tvCategoryBadge.visibility = View.VISIBLE
                binding.tvCategoryBadge.text = row.category
            }

            bindSelection(item)

            binding.root.setOnClickListener {
                if (selectionMode) {
                    onToggleSelect(item)
                } else {
                    onOpenPdf(item)
                }
            }
            binding.root.setOnLongClickListener {
                if (!selectionMode) {
                    onEnterSelection(item)
                } else {
                    onToggleSelect(item)
                }
                true
            }
            binding.btnMore.setOnClickListener { onMoreClick(item) }
        }

        fun bindSelection(item: PdfItem) {
            if (selectionMode) {
                binding.btnMore.visibility = View.GONE
                binding.ivSelectCheck.visibility = View.VISIBLE
                val checked = selectedKeys.contains(item.key)
                if (checked) {
                    binding.ivSelectCheck.setBackgroundResource(R.drawable.bg_check_circle_on)
                    binding.ivSelectCheck.setImageResource(R.drawable.ic_check)
                } else {
                    binding.ivSelectCheck.setBackgroundResource(R.drawable.bg_check_circle_off)
                    binding.ivSelectCheck.setImageDrawable(null)
                }
            } else {
                binding.ivSelectCheck.visibility = View.GONE
                binding.btnMore.visibility = View.VISIBLE
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<PdfListRow>() {
        override fun areItemsTheSame(oldItem: PdfListRow, newItem: PdfListRow): Boolean {
            return when {
                oldItem is PdfListRow.DateHeader && newItem is PdfListRow.DateHeader ->
                    oldItem.label == newItem.label
                oldItem is PdfListRow.PdfEntry && newItem is PdfListRow.PdfEntry ->
                    oldItem.item.key == newItem.item.key
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: PdfListRow, newItem: PdfListRow): Boolean =
            oldItem == newItem
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_PDF = 1
        private const val PAYLOAD_SELECTION = "selection"
    }
}
