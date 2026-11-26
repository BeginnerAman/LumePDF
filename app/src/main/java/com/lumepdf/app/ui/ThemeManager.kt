package com.lumepdf.app.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Curated Apple-style glass themes with smooth ambient crossfades and crisp haptic feedback.
 */
object ThemeManager {

    enum class AppTheme(
        val id: String,
        val title: String,
        val tagline: String,
        val previewStart: Int,
        val previewEnd: Int
    ) {
        LAVENDER(
            id = "lavender",
            title = "Lavender Mist",
            tagline = "Dreamy violet-tinted glass",
            previewStart = 0xFFA78BFA.toInt(),
            previewEnd = 0xFF6D28D9.toInt()
        ),
        ROSE(
            id = "rose",
            title = "Rose Quartz",
            tagline = "Soft warm blush & rose",
            previewStart = 0xFFF472B6.toInt(),
            previewEnd = 0xFFBE185D.toInt()
        ),
        CHAMPAGNE(
            id = "champagne",
            title = "Champagne Pearl",
            tagline = "Minimalist warm amber-gold",
            previewStart = 0xFFFBBF24.toInt(),
            previewEnd = 0xFFB45309.toInt()
        ),
        ICE_BLUE(
            id = "ice_blue",
            title = "Ice Blue",
            tagline = "Classic crisp Apple frost",
            previewStart = 0xFF38BDF8.toInt(),
            previewEnd = 0xFF0A84FF.toInt()
        );

        companion object {
            fun fromId(id: String?): AppTheme {
                return entries.firstOrNull { it.id == id } ?: LAVENDER
            }
        }
    }

    data class ThemePalette(
        val theme: AppTheme,
        val isDark: Boolean,
        val bgTop: Int,
        val bgMid: Int,
        val bgBottom: Int,
        val glassSurface: Int,
        val glassSurfaceStrong: Int,
        val glassPill: Int,
        val glassStroke: Int,
        val glassStrokeSubtle: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textTertiary: Int,
        val accent: Int,
        val accentEnd: Int,
        val accentSoft: Int,
        val accentRed: Int,
        val accentRedSoft: Int,
        val readerBg: Int,
        val readerBarBg: Int
    )

    private var bgAnimator: ValueAnimator? = null
    private var currentBgColors: IntArray? = null

    fun isNightMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    fun getPalette(context: Context, theme: AppTheme): ThemePalette {
        val dark = isNightMode(context)
        return when (theme) {
            AppTheme.LAVENDER -> if (dark) {
                ThemePalette(
                    theme = theme,
                    isDark = true,
                    bgTop = 0xFF0E0A1D.toInt(),
                    bgMid = 0xFF151029.toInt(),
                    bgBottom = 0xFF1C1438.toInt(),
                    glassSurface = 0xB81E1838.toInt(),
                    glassSurfaceStrong = 0xE8211B3D.toInt(),
                    glassPill = 0xB3272047.toInt(),
                    glassStroke = 0x33FFFFFF,
                    glassStrokeSubtle = 0x22FFFFFF,
                    textPrimary = 0xFFF7F4FF.toInt(),
                    textSecondary = 0xFFB8B0D4.toInt(),
                    textTertiary = 0xFF8379A8.toInt(),
                    accent = 0xFFA78BFA.toInt(),
                    accentEnd = 0xFF7C3AED.toInt(),
                    accentSoft = 0x33A78BFA,
                    accentRed = 0xFFFF5E66.toInt(),
                    accentRedSoft = 0x29FF5E66,
                    readerBg = 0xFF090712.toInt(),
                    readerBarBg = 0xDB191430.toInt()
                )
            } else {
                ThemePalette(
                    theme = theme,
                    isDark = false,
                    bgTop = 0xFFF3EEFF.toInt(),
                    bgMid = 0xFFF9F6FF.toInt(),
                    bgBottom = 0xFFE8DFFF.toInt(),
                    glassSurface = 0xCCFFFFFF.toInt(),
                    glassSurfaceStrong = 0xEBFFFFFF.toInt(),
                    glassPill = 0xBCFFFFFF.toInt(),
                    glassStroke = 0xB3FFFFFF.toInt(),
                    glassStrokeSubtle = 0x262E1065,
                    textPrimary = 0xFF1B1230.toInt(),
                    textSecondary = 0xFF5D5378.toInt(),
                    textTertiary = 0xFF8B80A8.toInt(),
                    accent = 0xFF7C5CFF.toInt(),
                    accentEnd = 0xFF5B3DF5.toInt(),
                    accentSoft = 0x227C5CFF,
                    accentRed = 0xFFFF3B30.toInt(),
                    accentRedSoft = 0x1FFF3B30,
                    readerBg = 0xFFEDE8FA.toInt(),
                    readerBarBg = 0xE0FFFFFF.toInt()
                )
            }

            AppTheme.ROSE -> if (dark) {
                ThemePalette(
                    theme = theme,
                    isDark = true,
                    bgTop = 0xFF180A12.toInt(),
                    bgMid = 0xFF22101A.toInt(),
                    bgBottom = 0xFF2C1422.toInt(),
                    glassSurface = 0xB82E1724.toInt(),
                    glassSurfaceStrong = 0xE8331A28.toInt(),
                    glassPill = 0xB33B1F2F.toInt(),
                    glassStroke = 0x33FFFFFF,
                    glassStrokeSubtle = 0x22FFFFFF,
                    textPrimary = 0xFFFFF5F8.toInt(),
                    textSecondary = 0xFFD4B0C0.toInt(),
                    textTertiary = 0xFFA1788B.toInt(),
                    accent = 0xFFFF6B98.toInt(),
                    accentEnd = 0xFFDB2777.toInt(),
                    accentSoft = 0x33FF6B98,
                    accentRed = 0xFFFF646E.toInt(),
                    accentRedSoft = 0x29FF646E,
                    readerBg = 0xFF10070C.toInt(),
                    readerBarBg = 0xDB24121C.toInt()
                )
            } else {
                ThemePalette(
                    theme = theme,
                    isDark = false,
                    bgTop = 0xFFFFEFF5.toInt(),
                    bgMid = 0xFFFFF7FA.toInt(),
                    bgBottom = 0xFFFCE0EC.toInt(),
                    glassSurface = 0xCCFFFFFF.toInt(),
                    glassSurfaceStrong = 0xEBFFFFFF.toInt(),
                    glassPill = 0xBCFFFFFF.toInt(),
                    glassStroke = 0xB3FFFFFF.toInt(),
                    glassStrokeSubtle = 0x264A0E29,
                    textPrimary = 0xFF2B101D.toInt(),
                    textSecondary = 0xFF735061.toInt(),
                    textTertiary = 0xFFA17B8D.toInt(),
                    accent = 0xFFD94F7A.toInt(),
                    accentEnd = 0xFFB83260.toInt(),
                    accentSoft = 0x22D94F7A,
                    accentRed = 0xFFFF3B30.toInt(),
                    accentRedSoft = 0x1FFF3B30,
                    readerBg = 0xFFF9EBF1.toInt(),
                    readerBarBg = 0xE0FFFFFF.toInt()
                )
            }

            AppTheme.CHAMPAGNE -> if (dark) {
                ThemePalette(
                    theme = theme,
                    isDark = true,
                    bgTop = 0xFF151008.toInt(),
                    bgMid = 0xFF1E170D.toInt(),
                    bgBottom = 0xFF281F11.toInt(),
                    glassSurface = 0xB82B2214.toInt(),
                    glassSurfaceStrong = 0xE8302617.toInt(),
                    glassPill = 0xB3382C1B.toInt(),
                    glassStroke = 0x30FFFFFF,
                    glassStrokeSubtle = 0x20FFFFFF,
                    textPrimary = 0xFFFFFBF2.toInt(),
                    textSecondary = 0xFFD4C5AB.toInt(),
                    textTertiary = 0xFF9E8D70.toInt(),
                    accent = 0xFFF5B74D.toInt(),
                    accentEnd = 0xFFD97706.toInt(),
                    accentSoft = 0x33F5B74D,
                    accentRed = 0xFFFF6B63.toInt(),
                    accentRedSoft = 0x29FF6B63,
                    readerBg = 0xFF0E0A05.toInt(),
                    readerBarBg = 0xDB221B10.toInt()
                )
            } else {
                ThemePalette(
                    theme = theme,
                    isDark = false,
                    bgTop = 0xFFFDF5E6.toInt(),
                    bgMid = 0xFFFFFBF2.toInt(),
                    bgBottom = 0xFFF4E4C8.toInt(),
                    glassSurface = 0xCCFFFFFF.toInt(),
                    glassSurfaceStrong = 0xEBFFFFFF.toInt(),
                    glassPill = 0xBCFFFFFF.toInt(),
                    glassStroke = 0xB3FFFFFF.toInt(),
                    glassStrokeSubtle = 0x263D2706,
                    textPrimary = 0xFF291D0B.toInt(),
                    textSecondary = 0xFF6E5C42.toInt(),
                    textTertiary = 0xFF9E8B70.toInt(),
                    accent = 0xFFC47F17.toInt(),
                    accentEnd = 0xFFA16207.toInt(),
                    accentSoft = 0x22C47F17,
                    accentRed = 0xFFFF3B30.toInt(),
                    accentRedSoft = 0x1FFF3B30,
                    readerBg = 0xFFF6EFE2.toInt(),
                    readerBarBg = 0xE0FFFFFF.toInt()
                )
            }

            AppTheme.ICE_BLUE -> if (dark) {
                ThemePalette(
                    theme = theme,
                    isDark = true,
                    bgTop = 0xFF090D16.toInt(),
                    bgMid = 0xFF0E1422.toInt(),
                    bgBottom = 0xFF121B2E.toInt(),
                    glassSurface = 0xB81A2336.toInt(),
                    glassSurfaceStrong = 0xE61C263B.toInt(),
                    glassPill = 0xB3202B42.toInt(),
                    glassStroke = 0x2EFFFFFF,
                    glassStrokeSubtle = 0x1EFFFFFF,
                    textPrimary = 0xFFF4F7FF.toInt(),
                    textSecondary = 0xFFA3B1C9.toInt(),
                    textTertiary = 0xFF72819B.toInt(),
                    accent = 0xFF389BFF.toInt(),
                    accentEnd = 0xFF1D6FE8.toInt(),
                    accentSoft = 0x2E389BFF,
                    accentRed = 0xFFFF574D.toInt(),
                    accentRedSoft = 0x29FF574D,
                    readerBg = 0xFF080B12.toInt(),
                    readerBarBg = 0xD9151D2E.toInt()
                )
            } else {
                ThemePalette(
                    theme = theme,
                    isDark = false,
                    bgTop = 0xFFEEF3FF.toInt(),
                    bgMid = 0xFFF4F7FC.toInt(),
                    bgBottom = 0xFFE8EEF9.toInt(),
                    glassSurface = 0xCCFFFFFF.toInt(),
                    glassSurfaceStrong = 0xE6FFFFFF.toInt(),
                    glassPill = 0xB8FFFFFF.toInt(),
                    glassStroke = 0x99FFFFFF.toInt(),
                    glassStrokeSubtle = 0x330F172A,
                    textPrimary = 0xFF0F172A.toInt(),
                    textSecondary = 0xFF526077.toInt(),
                    textTertiary = 0xFF7C8AA0.toInt(),
                    accent = 0xFF0A84FF.toInt(),
                    accentEnd = 0xFF2563EB.toInt(),
                    accentSoft = 0x220A84FF,
                    accentRed = 0xFFFF3B30.toInt(),
                    accentRedSoft = 0x1FFF3B30,
                    readerBg = 0xFFE9EEF5.toInt(),
                    readerBarBg = 0xDCFFFFFF.toInt()
                )
            }
        }
    }

    fun performThemeSwitchHaptic(view: View) {
        runCatching {
            val ctx = view.context
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        vibrator.vibrate(
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        )
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(18L, 88))
                    }
                    else -> {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(18L)
                    }
                }
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }.onFailure {
            runCatching { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
        }
    }

    fun applyAmbientBackground(view: View, palette: ThemePalette, animate: Boolean) {
        bgAnimator?.cancel()
        val target = intArrayOf(palette.bgTop, palette.bgMid, palette.bgBottom)
        val start = currentBgColors

        if (!animate || start == null || start.size != 3) {
            currentBgColors = target
            view.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                target
            )
            return
        }

        val evaluator = ArgbEvaluator()
        val drawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, start.copyOf())
        view.background = drawable

        bgAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                val c0 = evaluator.evaluate(f, start[0], target[0]) as Int
                val c1 = evaluator.evaluate(f, start[1], target[1]) as Int
                val c2 = evaluator.evaluate(f, start[2], target[2]) as Int
                val step = intArrayOf(c0, c1, c2)
                currentBgColors = step
                drawable.colors = step
            }
            start()
        }
    }

    private fun dp(context: Context, value: Float): Float {
        return value * context.resources.displayMetrics.density
    }

    private fun roundMask(radiusPx: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(Color.WHITE)
        }
    }

    fun cardDrawable(context: Context, palette: ThemePalette): Drawable {
        val radius = dp(context, 20f)
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(palette.glassSurface)
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), palette.glassStroke)
        }
        return RippleDrawable(ColorStateList.valueOf(palette.accentSoft), content, roundMask(radius))
    }

    fun pillDrawable(context: Context, palette: ThemePalette): Drawable {
        val radius = dp(context, 999f)
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(palette.glassPill)
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), palette.glassStroke)
        }
        return RippleDrawable(ColorStateList.valueOf(palette.accentSoft), content, roundMask(radius))
    }

    fun activeChipDrawable(context: Context, palette: ThemePalette): Drawable {
        val radius = dp(context, 999f)
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(palette.accent)
        }
        return RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), content, roundMask(radius))
    }

    fun categoryTagDrawable(context: Context, palette: ThemePalette): Drawable {
        val radius = dp(context, 999f)
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(palette.accentSoft)
        }
        return RippleDrawable(ColorStateList.valueOf(palette.accentSoft), content, roundMask(radius))
    }

    fun fabDrawable(context: Context, palette: ThemePalette): Drawable {
        val radius = dp(context, 999f)
        val content = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(palette.accent, palette.accentEnd)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), 0x66FFFFFF)
        }
        return RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), content, roundMask(radius))
    }

    fun sheetDrawable(context: Context, palette: ThemePalette): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 26f)
            setColor(palette.glassSurfaceStrong)
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), palette.glassStroke)
        }
    }

    fun readerBarDrawable(context: Context, palette: ThemePalette): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 24f)
            setColor(palette.readerBarBg)
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), palette.glassStroke)
        }
    }

    fun checkCircleOnDrawable(palette: ThemePalette): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.accent)
        }
    }

    fun checkCircleOffDrawable(context: Context, palette: ThemePalette): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(context, 1.5f).toInt().coerceAtLeast(1), palette.textTertiary)
        }
    }

    fun swatchOrbDrawable(context: Context, theme: AppTheme): Drawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(theme.previewStart, theme.previewEnd)
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(context, 1.5f).toInt().coerceAtLeast(1), 0x80FFFFFF.toInt())
        }
    }

    fun themeCardDrawable(context: Context, palette: ThemePalette, selected: Boolean): Drawable {
        val radius = dp(context, 18f)
        val strokeColor = if (selected) palette.accent else palette.glassStroke
        val strokeW = dp(context, if (selected) 1.75f else 1f).toInt().coerceAtLeast(1)
        val fillColor = if (selected) palette.accentSoft else palette.glassPill
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fillColor)
            setStroke(strokeW, strokeColor)
        }
        return RippleDrawable(ColorStateList.valueOf(palette.accentSoft), content, roundMask(radius))
    }
}
