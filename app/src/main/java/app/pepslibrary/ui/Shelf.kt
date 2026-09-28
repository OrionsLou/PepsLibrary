package app.pepslibrary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pepslibrary.R

private val ShelfCatSize = 48.dp

// In pep_on_shelf.xml the body's base is at y=30 of 40: the line sits there, and the tail hangs this far below it.
private val TailBelowLine = ShelfCatSize * 10 / 40

/**
 * The line between a screen's header and its list, with Pep sitting on it at the right-hand end and her tail
 * hanging over the edge. [content] (e.g. the sort and filter buttons) sits on the shelf to her left, so she takes
 * no height of her own. The app's one prominent cat; everything around it stays quiet.
 */
@Composable
fun Shelf(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit = {}) {
    Box(modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = ShelfCatSize - TailBelowLine).padding(start = 4.dp, end = 84.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(TailBelowLine))
        }
        Icon(
            painterResource(R.drawable.pep_on_shelf),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp).size(ShelfCatSize),
        )
    }
}

/** An empty screen: Pep asleep, what's missing, and what to do about it. */
@Composable
fun SleepingCatMessage(message: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.pep_sleeping),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(width = 112.dp, height = 70.dp),
        )
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        action()
    }
}
