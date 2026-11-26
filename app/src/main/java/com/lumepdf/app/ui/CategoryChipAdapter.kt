package com.lumepdf.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lumepdf.app.R
import com.lumepdf.app.databinding.ItemCategoryChipBinding
import com.lumepdf.app.model.CategoryChip

class CategoryChipAdapter(
    private val onChipClick: (CategoryChip) -> Unit,
    private val onChipLongClick: (String) -> Unit
) : ListAdapter<CategoryChip, CategoryChipAdapter.ChipVH>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipVH {
        val binding = ItemCategoryChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChipVH(binding)
    }

    override fun onBindViewHolder(holder: ChipVH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChipVH(private val binding: ItemCategoryChipBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chip: CategoryChip) {
            val ctx = binding.root.context
            when (chip) {
                is CategoryChip.Filter -> {
                    binding.tvChip.text = if (chip.count > 0) {
                        "${chip.name} · ${chip.count}"
                    } else {
                        chip.name
                    }
                    if (chip.isSelected) {
                        binding.tvChip.setBackgroundResource(R.drawable.bg_glass_chip_active)
                        binding.tvChip.setTextColor(Color.WHITE)
                    } else {
                        binding.tvChip.setBackgroundResource(R.drawable.bg_glass_chip_inactive)
                        binding.tvChip.setTextColor(
                            ContextCompat.getColor(ctx, R.color.text_primary)
                        )
                    }
                    binding.tvChip.setOnClickListener { onChipClick(chip) }
                    binding.tvChip.setOnLongClickListener {
                        onChipLongClick(chip.name)
                        true
                    }
                }

                CategoryChip.AddNew -> {
                    binding.tvChip.text = ctx.getString(R.string.new_category)
                    binding.tvChip.setBackgroundResource(R.drawable.bg_glass_chip_inactive)
                    binding.tvChip.setTextColor(
                        ContextCompat.getColor(ctx, R.color.accent_blue)
                    )
                    binding.tvChip.setOnClickListener { onChipClick(chip) }
                    binding.tvChip.setOnLongClickListener(null)
                }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<CategoryChip>() {
        override fun areItemsTheSame(oldItem: CategoryChip, newItem: CategoryChip): Boolean {
            return when {
                oldItem is CategoryChip.Filter && newItem is CategoryChip.Filter ->
                    oldItem.name == newItem.name
                oldItem is CategoryChip.AddNew && newItem is CategoryChip.AddNew -> true
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: CategoryChip, newItem: CategoryChip): Boolean =
            oldItem == newItem
    }
}
