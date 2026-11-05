package se.anders.athenapodcaster.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import se.anders.athenapodcaster.data.art.ArtworkStore
import se.anders.athenapodcaster.domain.ArtStyle
import se.anders.athenapodcaster.domain.GRAYSCALE_MATRIX

/** Provided once at the top of the app, so rows do not need the store or the style passed down. */
val LocalArtworkStore = compositionLocalOf<ArtworkStore?> { null }
val LocalArtStyle = compositionLocalOf { ArtStyle.Grayscale }

/**
 * A square cover picture: the first of [urls] that is stored on the phone. Shows nothing at all, and takes no
 * space, when there is none: no placeholder (FR-043). Decorative for screen readers, since the title says it all.
 */
@Composable
fun ArtImage(urls: List<String?>, size: Dp, modifier: Modifier = Modifier) {
    val store = LocalArtworkStore.current ?: return
    val style = LocalArtStyle.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val version by store.version.collectAsState()
    val bitmap by produceState<ImageBitmap?>(initialValue = null, urls, px, version) {
        value = store.firstBitmap(urls, px)
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = if (style == ArtStyle.Grayscale) ColorFilter.colorMatrix(ColorMatrix(GRAYSCALE_MATRIX)) else null,
            modifier = modifier.size(size),
        )
    }
}

/** Whether [ArtImage] would show something now, so a row can decide to leave out the gap beside it. */
@Composable
fun hasArt(urls: List<String?>): Boolean {
    val store = LocalArtworkStore.current ?: return false
    val version by store.version.collectAsState()
    return version >= 0 && urls.any { store.has(it) } // read the version first, so a late download updates the row
}
