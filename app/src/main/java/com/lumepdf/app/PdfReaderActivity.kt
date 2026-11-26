package com.lumepdf.app

import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lumepdf.app.data.PdfStore
import com.lumepdf.app.databinding.ActivityPdfReaderBinding
import com.lumepdf.app.ui.PdfPageAdapter
import com.lumepdf.app.ui.PdfPageRenderer
import kotlinx.coroutines.launch

class PdfReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfReaderBinding
    private lateinit var store: PdfStore
    private lateinit var layoutManager: LinearLayoutManager

    private var renderer: PdfPageRenderer? = null
    private var pageAdapter: PdfPageAdapter? = null

    private var pdfKey: String = ""
    private var currentPage: Int = 0
    private var totalPages: Int = 0
    private var barsVisible: Boolean = true
    private var nightMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityPdfReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = PdfStore(this)
        nightMode = store.isReaderNightMode()
        applyNightBackground()

        ViewCompat.setOnApplyWindowInsetsListener(binding.readerRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        val uriStr = intent.getStringExtra(EXTRA_URI) ?: intent.data?.toString()
        val uri = uriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val filePath = intent.getStringExtra(EXTRA_PATH)
        pdfKey = intent.getStringExtra(EXTRA_KEY) ?: uriStr.orEmpty()

        val title = intent.getStringExtra(EXTRA_TITLE) ?: resolveTitle(uri) ?: "Document"
        binding.tvReaderTitle.text = title

        binding.btnBack.setOnClickListener { finish() }
        binding.btnNightToggle.setOnClickListener {
            nightMode = !nightMode
            store.setReaderNightMode(nightMode)
            applyNightBackground()
            pageAdapter?.nightMode = nightMode
        }

        binding.rvPages.onSingleTap = { toggleBars() }

        if (uri == null) {
            showError("Invalid PDF document.")
            return
        }

        openDocument(uri, filePath)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.rvPages.post {
            val newWidth = calculatePageWidthPx()
            pageAdapter?.updatePageWidth(newWidth)
            if (::layoutManager.isInitialized && totalPages > 0) {
                layoutManager.scrollToPositionWithOffset(currentPage.coerceIn(0, totalPages - 1), 0)
            }
        }
    }

    private fun calculatePageWidthPx(): Int {
        val rvWidth = binding.rvPages.width
        val baseWidth = if (rvWidth > 0) rvWidth else resources.displayMetrics.widthPixels
        val marginPx = (20 * resources.displayMetrics.density).toInt()
        return (baseWidth - marginPx).coerceAtLeast(480)
    }

    private fun openDocument(uri: Uri, filePath: String?) {
        lifecycleScope.launch {
            val result = PdfPageRenderer.open(this@PdfReaderActivity, uri, filePath)
            binding.progressReader.visibility = View.GONE

            result.onFailure { err ->
                showError(err.localizedMessage ?: "Unable to open this PDF (it may be protected or damaged).")
            }.onSuccess { pdfRenderer ->
                renderer = pdfRenderer
                totalPages = pdfRenderer.pageCount
                if (totalPages <= 0) {
                    showError("This PDF has no pages.")
                    return@onSuccess
                }

                val widthPx = calculatePageWidthPx()

                layoutManager = LinearLayoutManager(this@PdfReaderActivity)
                pageAdapter = PdfPageAdapter(pdfRenderer, lifecycleScope, widthPx).apply {
                    this.nightMode = this@PdfReaderActivity.nightMode
                }

                binding.rvPages.layoutManager = layoutManager
                binding.rvPages.adapter = pageAdapter
                binding.rvPages.setItemViewCacheSize(3)

                val savedPage = store.getLastPage(pdfKey).coerceIn(0, totalPages - 1)
                currentPage = savedPage
                updatePageLabel(savedPage)

                binding.seekPage.max = (totalPages - 1).coerceAtLeast(0)
                binding.seekPage.progress = savedPage

                if (savedPage > 0) {
                    layoutManager.scrollToPositionWithOffset(savedPage, 0)
                }

                binding.rvPages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val first = layoutManager.findFirstVisibleItemPosition()
                        if (first != RecyclerView.NO_POSITION && first != currentPage) {
                            currentPage = first
                            updatePageLabel(first)
                            binding.seekPage.progress = first
                        }
                    }
                })

                binding.seekPage.setOnSeekBarChangeListener(object :
                    SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            currentPage = progress
                            updatePageLabel(progress)
                            layoutManager.scrollToPositionWithOffset(progress, 0)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            }
        }
    }

    private fun updatePageLabel(index: Int) {
        binding.tvPageCounter.text = "${index + 1} / $totalPages"
    }

    private fun toggleBars() {
        barsVisible = !barsVisible
        val targetAlpha = if (barsVisible) 1f else 0f
        binding.topBar.animate().alpha(targetAlpha).setDuration(160).withStartAction {
            if (barsVisible) binding.topBar.visibility = View.VISIBLE
        }.withEndAction {
            if (!barsVisible) binding.topBar.visibility = View.GONE
        }.start()

        binding.bottomBar.animate().alpha(targetAlpha).setDuration(160).withStartAction {
            if (barsVisible) binding.bottomBar.visibility = View.VISIBLE
        }.withEndAction {
            if (!barsVisible) binding.bottomBar.visibility = View.GONE
        }.start()
    }

    private fun applyNightBackground() {
        val bgColor = if (nightMode) {
            Color.parseColor("#05070B")
        } else {
            ContextCompat.getColor(this, R.color.reader_bg)
        }
        binding.readerRoot.setBackgroundColor(bgColor)
    }

    private fun resolveTitle(uri: Uri?): String? {
        if (uri == null) return null
        return runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return@use c.getString(idx)
                }
                null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }

    private fun showError(msg: String) {
        binding.progressReader.visibility = View.GONE
        binding.readerError.visibility = View.VISIBLE
        binding.tvReaderError.text = msg
    }

    override fun onPause() {
        super.onPause()
        if (pdfKey.isNotEmpty() && totalPages > 0) {
            store.setLastPage(pdfKey, currentPage)
        }
    }

    override fun onDestroy() {
        renderer?.close()
        renderer = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_KEY = "extra_key"
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_TITLE = "extra_title"
    }
}
