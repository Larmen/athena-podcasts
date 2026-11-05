package se.anders.athenapodcaster.domain

/** FR-042: how cover art is drawn. Grayscale fits the calm, monochrome look and is the default. */
enum class ArtStyle { Original, Grayscale }

/**
 * The 4x5 colour matrix for [ArtStyle.Grayscale]: colour removed and contrast reduced (0.75), the same
 * treatment as the launcher's grayscale icons. Values are in the Android / Compose ColorMatrix layout.
 */
val GRAYSCALE_MATRIX: FloatArray = run {
    val c = 0.75f
    val offset = (1f - c) / 2f * 255f
    val r = 0.213f * c
    val g = 0.715f * c
    val b = 0.072f * c
    floatArrayOf(
        r, g, b, 0f, offset,
        r, g, b, 0f, offset,
        r, g, b, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )
}
