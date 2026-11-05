package se.anders.athenapodcaster.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.anders.athenapodcaster.R

/** Play / pause as one icon button; the icon shows what pressing it will do. */
@Composable
fun PlayPauseButton(playing: Boolean, onClick: () -> Unit, size: Dp = 48.dp, iconSize: Dp = 28.dp) {
    IconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Icon(
            painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = stringResource(if (playing) R.string.player_pause else R.string.player_play),
            tint = LocalContentColor.current,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Skip back or forward by [seconds]: a circular arrow with the number on it (10 and 30 in this app). */
@Composable
fun SkipButton(forward: Boolean, seconds: Int, onClick: () -> Unit, size: Dp = 48.dp, iconSize: Dp = 32.dp) {
    val label = stringResource(if (forward) R.string.player_forward_30 else R.string.player_back_10)
    IconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_arrow),
                contentDescription = label,
                tint = LocalContentColor.current,
                modifier = Modifier.size(iconSize).scale(scaleX = if (forward) -1f else 1f, scaleY = 1f),
            )
            // The number is part of the picture, so it keeps its size however large the system font is.
            Text(
                text = seconds.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = with(LocalDensity.current) { (iconSize.value * 0.3f).dp.toSp() },
                color = LocalContentColor.current,
                modifier = Modifier.padding(top = (iconSize.value * 0.12f).dp),
            )
        }
    }
}
