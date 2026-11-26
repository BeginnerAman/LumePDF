package com.lumepdf.app.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lumepdf.app.databinding.ItemPdfPageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PdfPageAdapter(
    private val renderer: PdfPageRenderer,
    private val scope: CoroutineScope,
    private val pageWidthPx: Int
) : RecyclerView.Adapter<PdfPageAdapter.PageVH>() {

    var nightMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    private val invertFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )

    override fun getItemCount(): Int = renderer.pageCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val binding = ItemPdfPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PageVH(binding)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        holder.bind(position)
    }

    override fun onViewRecycled(holder: PageVH) {
        holder.cancel()
        super.onViewRecycled(holder)
    }

    inner class PageVH(private val binding: ItemPdfPageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var job: Job? = null

        fun cancel() {
            job?.cancel()
            job = null
            binding.ivPage.setImageDrawable(null)
        }

        fun bind(position: Int) {
            job?.cancel()

            val ratio = renderer.getAspectRatio(position) ?: 1.414f
            val targetH = (pageWidthPx * ratio).toInt().coerceAtLeast(300)
            val lp = binding.root.layoutParams
            if (lp.height != targetH) {
                lp.height = targetH
                binding.root.layoutParams = lp
            }

            binding.root.setBackgroundColor(
                if (nightMode) android.graphics.Color.parseColor("#111622")
                else android.graphics.Color.WHITE
            )
            binding.ivPage.colorFilter = if (nightMode) invertFilter else null

            val cached = renderer.getCachedPage(position)
            if (cached != null && !cached.isRecycled) {
                binding.tvPagePlaceholder.visibility = View.GONE
                binding.ivPage.setImageBitmap(cached)
                return
            }

            binding.ivPage.setImageDrawable(null)
            binding.tvPagePlaceholder.visibility = View.VISIBLE
            binding.tvPagePlaceholder.text = "Page ${position + 1}"

            val renderTargetWidth = (pageWidthPx * 1.25f).toInt()
            job = scope.launch {
                val bmp = renderer.renderPage(position, renderTargetWidth)
                if (bmp != null && bindingAdapterPosition == position) {
                    val actualRatio = bmp.height.toFloat() / bmp.width.coerceAtLeast(1).toFloat()
                    val exactH = (pageWidthPx * actualRatio).toInt().coerceAtLeast(200)
                    if (kotlin.math.abs(binding.root.layoutParams.height - exactH) > 8) {
                        binding.root.layoutParams = binding.root.layoutParams.apply {
                            height = exactH
                        }
                    }
                    binding.tvPagePlaceholder.visibility = View.GONE
                    binding.ivPage.setImageBitmap(bmp)
                }
            }
        }
    }
}
