package com.lumepdf.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Toast
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var cropMargins: Boolean = false
    private var keepScreenOn: Boolean = true
    private var shouldPersistProgress: Boolean = false
    private var autoHideJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityPdfReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = PdfStore(this)
        nightMode = store.isReaderNightMode()
        cropMargins = store.isReaderCropMargins()
        keepScreenOn = store.isKeepScreenOn()
        applyNightBackground()
        applyKeepScreenOn()
        updateToggleStates()
        applyOrientationBarLayout(
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        )

        ViewCompat.setOnApplyWindowInsetsListener(binding.readerRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        val isExternalView = intent.action == Intent.ACTION_VIEW
        val uri: Uri?
        val filePath: String?
        val title: String

        if (isExternalView) {
            // Untrusted external caller: only accept intent.data and ignore internal extras
            uri = intent.data
            filePath = null
            pdfKey = ""
            shouldPersistProgress = false
            title = resolveTitle(uri) ?: "Document"
        } else {
            val uriStr = intent.getStringExtra(EXTRA_URI) ?: intent.data?.toString()
            uri = uriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
            filePath = intent.getStringExtra(EXTRA_PATH)
            pdfKey = intent.getStringExtra(EXTRA_KEY) ?: uriStr.orEmpty()
            shouldPersistProgress = pdfKey.isNotEmpty()
            title = intent.getStringExtra(EXTRA_TITLE) ?: resolveTitle(uri) ?: "Document"
        }
        binding.tvReaderTitle.text = title

        binding.btnBack.setOnClickListener { finish() }

        binding.btnScreenOnToggle.setOnClickListener {
            keepScreenOn = !keepScreenOn
            store.setKeepScreenOn(keepScreenOn)
            applyKeepScreenOn()
            updateToggleStates()
            Toast.makeText(
                this,
                if (keepScreenOn) "Keep Screen On: On" else "Keep Screen On: Off",
                Toast.LENGTH_SHORT
            ).show()
            scheduleAutoHide()
        }

        binding.btnCropToggle.setOnClickListener {
            cropMargins = !cropMargins
            store.setReaderCropMargins(cropMargins)
            updateToggleStates()
            pageAdapter?.cropMargins = cropMargins
            Toast.makeText(
                this,
                if (cropMargins) "Auto-Crop Margins: On" else "Auto-Crop Margins: Off",
                Toast.LENGTH_SHORT
            ).show()
            scheduleAutoHide()
        }

        binding.btnNightToggle.setOnClickListener {
            nightMode = !nightMode
            store.setReaderNightMode(nightMode)
            applyNightBackground()
            updateToggleStates()
            pageAdapter?.nightMode = nightMode
            scheduleAutoHide()
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
        applyOrientationBarLayout(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
        binding.rvPages.resetZoom()
        binding.rvPages.post {
            val newWidth = calculatePageWidthPx()
            pageAdapter?.updatePageWidth(newWidth)
            if (::layoutManager.isInitialized && totalPages > 0) {
                layoutManager.scrollToPositionWithOffset(currentPage.coerceIn(0, totalPages - 1), 0)
            }
        }
    }

    private fun applyOrientationBarLayout(isLandscape: Boolean) {
        val d = resources.displayMetrics.density
        val topLp = binding.topBar.layoutParams as FrameLayout.LayoutParams
        val bottomLp = binding.bottomBar.layoutParams as FrameLayout.LayoutParams

        if (isLandscape) {
            topLp.height = (42 * d).toInt()
            topLp.topMargin = (6 * d).toInt()
            topLp.marginStart = (18 * d).toInt()
            topLp.marginEnd = (18 * d).toInt()

            bottomLp.width = (400 * d).toInt()
            bottomLp.height = (40 * d).toInt()
            bottomLp.bottomMargin = (8 * d).toInt()
            bottomLp.marginStart = 0
            bottomLp.marginEnd = 0
            bottomLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        } else {
            topLp.height = (50 * d).toInt()
            topLp.topMargin = (10 * d).toInt()
            topLp.marginStart = (14 * d).toInt()
            topLp.marginEnd = (14 * d).toInt()

            bottomLp.width = ViewGroup.LayoutParams.MATCH_PARENT
            bottomLp.height = (50 * d).toInt()
            bottomLp.bottomMargin = (14 * d).toInt()
            bottomLp.marginStart = (14 * d).toInt()
            bottomLp.marginEnd = (14 * d).toInt()
            bottomLp.gravity = Gravity.BOTTOM
        }

        binding.topBar.layoutParams = topLp
        binding.bottomBar.layoutParams = bottomLp
    }

    private fun applyKeepScreenOn() {
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun updateToggleStates() {
        val activeColor = ContextCompat.getColor(this, R.color.accent_blue)
        val normalColor = ContextCompat.getColor(this, R.color.text_primary)
        binding.btnScreenOnToggle.setColorFilter(if (keepScreenOn) activeColor else normalColor)
        binding.btnCropToggle.setColorFilter(if (cropMargins) activeColor else normalColor)
        binding.btnNightToggle.setColorFilter(if (nightMode) activeColor else normalColor)
    }

    private fun calculatePageWidthPx(): Int {
        val rvWidth = binding.rvPages.width
        val baseWidth = if (rvWidth > 0) rvWidth else resources.displayMetrics.widthPixels
        val marginPx = (12 * resources.displayMetrics.density).toInt()
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
                if (shouldPersistProgress) {
                    store.setPageCount(pdfKey, totalPages)
                }

                val widthPx = calculatePageWidthPx()

                layoutManager = LinearLayoutManager(this@PdfReaderActivity)
                pageAdapter = PdfPageAdapter(pdfRenderer, lifecycleScope, widthPx).apply {
                    this.nightMode = this@PdfReaderActivity.nightMode
                    this.cropMargins = this@PdfReaderActivity.cropMargins
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
                        val dominant = resolveDominantVisiblePage(recyclerView)
                        if (dominant != currentPage) {
                            currentPage = dominant
                            updatePageLabel(dominant)
                            binding.seekPage.progress = dominant
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

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        autoHideJob?.cancel()
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        scheduleAutoHide()
                    }
                })

                scheduleAutoHide()
            }
        }
    }

    private fun resolveDominantVisiblePage(recyclerView: RecyclerView): Int {
        if (totalPages <= 0) return 0
        if (!recyclerView.canScrollVertically(1) ||
            layoutManager.findLastCompletelyVisibleItemPosition() == totalPages - 1
        ) {
            return totalPages - 1
        }
        if (!recyclerView.canScrollVertically(-1) ||
            layoutManager.findFirstCompletelyVisibleItemPosition() == 0
        ) {
            return 0
        }

        val rvTop = recyclerView.paddingTop
        val rvBottom = recyclerView.height - recyclerView.paddingBottom
        var bestPos = layoutManager.findFirstVisibleItemPosition()
        var bestVisibleHeight = -1

        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            val pos = recyclerView.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION) continue
            val visibleTop = maxOf(child.top, rvTop)
            val visibleBottom = minOf(child.bottom, rvBottom)
            val visibleH = (visibleBottom - visibleTop).coerceAtLeast(0)
            if (visibleH > bestVisibleHeight) {
                bestVisibleHeight = visibleH
                bestPos = pos
            }
        }
        return bestPos.coerceIn(0, totalPages - 1)
    }

    private fun updatePageLabel(index: Int) {
        val text = "${index + 1} / $totalPages"
        binding.tvPageCounter.text = text
        binding.tvCornerPagePill.text = text
    }

    private fun scheduleAutoHide() {
        autoHideJob?.cancel()
        if (!barsVisible) return
        autoHideJob = lifecycleScope.launch {
            delay(2500L)
            if (barsVisible) setBarsVisible(false)
        }
    }

    private fun toggleBars() {
        autoHideJob?.cancel()
        setBarsVisible(!barsVisible)
        if (barsVisible) {
            scheduleAutoHide()
        }
    }

    private fun setBarsVisible(visible: Boolean) {
        barsVisible = visible
        val targetAlpha = if (visible) 1f else 0f

        binding.topBar.animate().cancel()
        binding.topBar.animate().alpha(targetAlpha).setDuration(160).withStartAction {
            if (visible) binding.topBar.visibility = View.VISIBLE
        }.withEndAction {
            if (!barsVisible) binding.topBar.visibility = View.GONE
        }.start()

        binding.bottomBar.animate().cancel()
        binding.bottomBar.animate().alpha(targetAlpha).setDuration(160).withStartAction {
            if (visible) binding.bottomBar.visibility = View.VISIBLE
        }.withEndAction {
            if (!barsVisible) binding.bottomBar.visibility = View.GONE
        }.start()

        val pillAlpha = if (visible) 0f else 0.92f
        binding.tvCornerPagePill.animate().cancel()
        binding.tvCornerPagePill.animate().alpha(pillAlpha).setDuration(160).withStartAction {
            if (!visible && totalPages > 0) binding.tvCornerPagePill.visibility = View.VISIBLE
        }.withEndAction {
            if (barsVisible) binding.tvCornerPagePill.visibility = View.GONE
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
        if (shouldPersistProgress && totalPages > 0) {
            store.setLastPage(pdfKey, currentPage)
        }
    }

    override fun onDestroy() {
        autoHideJob?.cancel()
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
