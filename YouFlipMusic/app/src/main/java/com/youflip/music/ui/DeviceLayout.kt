package com.youflip.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Galaxy Z Flip 7's FlexWindow is a ~4.1" almost-square panel (948 x 1048 px), roughly
 * 380 x 420 dp at its default density. The main screen is a tall 21:9 panel (~411 x 960 dp).
 * We treat any small, near-square window as the cover screen. This also gives a sensible
 * compact UI in pop-up / split windows on the main screen.
 */
object CoverScreenSpec {
    /** The full FlexWindow is ~1.11 tall:wide; anything taller is a letterboxed compat window. */
    const val MAX_FULL_TALLNESS = 1.18f

    const val MAX_SHORT_SIDE_DP = 520
    const val MAX_LONG_SIDE_DP = 620
    const val MAX_ASPECT = 1.4f

    /** Lists on the cover screen run to the bottom edge; only a small breathing gap is kept. */
    val LIST_BOTTOM_PADDING = 12.dp

    /**
     * Everything on the cover screen (text and UI) is drawn this much larger. One UI runs the app there
     * in size-compat mode (scaled to ~0.67), which made it look small; 1.5x brings it back to full size.
     */
    const val UI_SCALE = 1.5f

    /** Scale for the narrow portrait letterbox (~590 px wide instead of 948). */
    const val UI_SCALE_TALL = 1.15f

    /** Text there ends up ≈ 0.98× (vs 1.08× on the full window): icons shrink more than the text. */
    const val FONT_SCALE_TALL = 0.85f

    /** Text grows a little less than the rest of the UI (1.5 × 0.72 ≈ 1.08×), so it doesn't feel oversized. */
    const val FONT_SCALE = 0.72f
}

fun isCoverLike(widthDp: Int, heightDp: Int): Boolean {
    if (widthDp <= 0 || heightDp <= 0) return false
    val short = min(widthDp, heightDp)
    val long = max(widthDp, heightDp)
    return short <= CoverScreenSpec.MAX_SHORT_SIDE_DP &&
        long <= CoverScreenSpec.MAX_LONG_SIDE_DP &&
        long.toFloat() / short <= CoverScreenSpec.MAX_ASPECT
}

@Composable
fun rememberUseCoverLayout(): Boolean {
    val cfg = LocalConfiguration.current
    return isCoverLike(cfg.screenWidthDp, cfg.screenHeightDp)
}

/**
 * Where the flash + two rear lenses sit on the FlexWindow, in the window's pixel coordinates.
 *
 * If the system reports display cut-outs in the bottom-right quarter we use those exact rects.
 * Otherwise we fall back to proportions measured from the Flip 7 cover (flash and lenses in one
 * row near the bottom edge): flash centre at 51.6% width, lens centres at 66.8% / 87.4% width,
 * all at 88.6% height; lens radius ≈ 8.6% of the width, flash radius ≈ 3.1%.
 */
data class CameraIsland(val lenses: List<Circle>) {
    data class Circle(val cx: Float, val cy: Float, val r: Float)
    val left get() = lenses.minOf { it.cx - it.r }
    val right get() = lenses.maxOf { it.cx + it.r }
    val top get() = lenses.minOf { it.cy - it.r }
    val bottom get() = lenses.maxOf { it.cy + it.r }

    companion object {
        fun fallback(w: Float, h: Float): CameraIsland {
            val cy = h * 0.886f
            val lensR = w * 0.086f
            return CameraIsland(
                listOf(
                    Circle(w * 0.516f, cy, w * 0.031f),
                    Circle(w * 0.668f, cy, lensR),
                    Circle(w * 0.874f, cy, lensR),
                )
            )
        }

        fun fromCutouts(rects: List<android.graphics.Rect>, w: Float, h: Float): CameraIsland? {
            // Only trust circle-like cut-outs. The Flip 7 reports one coarse 219x88 dp box
            // (428,839 → 948,1048 px) that runs to the screen edges; the measured lens
            // positions in [fallback] sit inside it, so we use those instead.
            val cams = rects.filter {
                it.centerX() > w / 2 && it.centerY() > h / 2 && it.width() > 0 &&
                    max(it.width(), it.height()).toFloat() / min(it.width(), it.height()) < 1.3f
            }
            if (cams.isEmpty()) return null
            return CameraIsland(cams.map { Circle(it.exactCenterX(), it.exactCenterY(), max(it.width(), it.height()) / 2f) })
        }
    }
}

@Composable
fun rememberCameraIsland(widthPx: Float, heightPx: Float): CameraIsland {
    val view = androidx.compose.ui.platform.LocalView.current
    return androidx.compose.runtime.remember(widthPx, heightPx) {
        val cutouts = if (android.os.Build.VERSION.SDK_INT >= 28) view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty() else emptyList()
        CameraIsland.fromCutouts(cutouts, widthPx, heightPx) ?: CameraIsland.fallback(widthPx, heightPx)
    }
}

/**
 * True when the app is on the cover screen but One UI is showing it letterboxed (the forced
 * portrait / landscape positions of the FlexWindow compat toggle) instead of filling the
 * near-square panel. The cover window is ~1:1.1 when full; the forced modes are ~16:9.
 */
@Composable
fun rememberCoverLetterboxed(): Boolean {
    val cfg = LocalConfiguration.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(cfg) {
        val activity = ctx as? android.app.Activity ?: return@remember false
        @Suppress("DEPRECATION")
        val displayId = if (android.os.Build.VERSION.SDK_INT >= 30) activity.display?.displayId else activity.windowManager.defaultDisplay.displayId
        if (displayId == null || displayId == android.view.Display.DEFAULT_DISPLAY) return@remember false
        val (w, h) = if (android.os.Build.VERSION.SDK_INT >= 30) {
            activity.windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
        } else cfg.screenWidthDp to cfg.screenHeightDp
        val short = min(w, h)
        // Full screen is 948 × 1048 (height/width ≈ 1.11). Landscape-shaped, or noticeably taller than
        // that, means One UI is letterboxing the app.
        short > 0 && (w > h || h.toFloat() / w > CoverScreenSpec.MAX_FULL_TALLNESS)
    }
}
