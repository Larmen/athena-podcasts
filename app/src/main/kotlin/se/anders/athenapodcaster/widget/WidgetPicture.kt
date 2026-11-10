package se.anders.athenapodcaster.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.data.settings.TypefaceChoice
import se.anders.athenapodcaster.domain.EffectiveAppearance
import se.anders.athenapodcaster.ui.theme.sizeAdjust

/**
 * A widget cannot use app fonts, so its background and title are drawn into one picture with the app's typeface,
 * text size and theme (FR-120). The Play/Pause button sits on top of the right end.
 */
object WidgetPicture {
    private const val BUTTON_DP = 64
    private const val PAD_DP = 16

    fun draw(context: Context, text: String, appearance: EffectiveAppearance, wDp: Int, hDp: Int, bg: Int, fg: Int, art: ByteArray? = null): Bitmap {
        val dm = context.resources.displayMetrics
        val w = (wDp * dm.density).toInt()
        val h = (hDp * dm.density).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bg)
        // The cover is already in the app's chosen style (grayscale or colour, FR-042); it fills the left end, square.
        val cover = art?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
        var left = PAD_DP * dm.density
        if (cover != null) {
            val side = minOf(cover.width, cover.height)
            val src = android.graphics.Rect((cover.width - side) / 2, (cover.height - side) / 2, (cover.width + side) / 2, (cover.height + side) / 2)
            canvas.drawBitmap(cover, src, android.graphics.Rect(0, 0, h, h), null)
            left = h + 12 * dm.density
        }
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = fg
            typeface = typeface(context, appearance.typeface)
            // 16 sp, the system font size included (TypedValue), times the app's own size and the font's adjustment.
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, dm) * appearance.textScalePercent / 100f * appearance.typeface.sizeAdjust()
        }
        val textWidth = (w - left - BUTTON_DP * dm.density).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        canvas.translate(left, ((h - layout.height) / 2f).coerceAtLeast(0f))
        layout.draw(canvas)
        return bitmap
    }

    /** The same fonts as the app's [se.anders.athenapodcaster.ui.theme.fontFamily], as plain Android typefaces. */
    private fun typeface(context: Context, choice: TypefaceChoice): Typeface {
        fun res(id: Int) = ResourcesCompat.getFont(context, id) ?: Typeface.DEFAULT
        return when (choice) {
            TypefaceChoice.System -> Typeface.DEFAULT
            TypefaceChoice.Condensed -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            TypefaceChoice.Serif -> Typeface.SERIF
            TypefaceChoice.Monospace -> Typeface.MONOSPACE
            TypefaceChoice.Cursive -> Typeface.create("cursive", Typeface.NORMAL)
            TypefaceChoice.Casual -> Typeface.create("casual", Typeface.NORMAL)
            TypefaceChoice.Vt323 -> res(R.font.vt323_regular)
            TypefaceChoice.PlexMono -> res(R.font.ibm_plex_mono_regular)
            TypefaceChoice.SpaceMono -> res(R.font.space_mono_regular)
            TypefaceChoice.JetBrainsMono -> res(R.font.jetbrains_mono)
            TypefaceChoice.Inter -> res(R.font.inter)
            TypefaceChoice.Atkinson -> res(R.font.atkinson_hyperlegible_regular)
        }
    }
}
