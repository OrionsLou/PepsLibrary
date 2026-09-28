package app.pepslibrary.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pepslibrary.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How the opening moment plays: only on a cold start, and without motion when Android's animations are off. */
enum class OpeningStyle { ANIMATED, STILL, NONE }

internal fun openingStyle(coldStart: Boolean, animationsOff: Boolean): OpeningStyle = when {
    !coldStart -> OpeningStyle.NONE
    animationsOff -> OpeningStyle.STILL
    else -> OpeningStyle.ANIMATED
}

// Where Android 12+'s splash draws the adaptive icon: its 108-unit canvas at 288dp, masked to a 192dp circle (measured
// on the emulator by comparing screenshots across the hand-off; the docs' 240dp is for icons with a separate
// background colour). Matching it makes the switch from the system splash to this screen invisible.
private val SplashIconSize = 288.dp
private val SplashCircleSize = 192.dp
private val Lift = 64.dp
private val NameOffset = 84.dp

/**
 * The app's one orchestrated motion moment: it starts exactly where the system splash leaves off (Pep on the coat
 * colour), then Pep rises a little and "Pep's Library" appears beneath before the app is revealed, about 1.5 s in
 * all. A tap anywhere skips straight to the app. The app itself is already composing underneath, so AO3 starts
 * loading while this plays.
 */
@Composable
fun OpeningScreen(style: OpeningStyle, onFinished: () -> Unit) {
    val lift = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val screen = remember { Animatable(1f) }
    var skipped by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    suspend fun reveal(millis: Int) {
        if (finished) return
        screen.animateTo(0f, tween(millis))
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        if (style == OpeningStyle.STILL) {
            lift.snapTo(1f)
            name.snapTo(1f)
            delay(900)
        } else {
            delay(300) // hold the splash's own frame for a moment, so the hand-off reads as one scene
            coroutineScope {
                launch { lift.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
                launch {
                    delay(250)
                    name.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
                }
            }
            delay(550)
        }
        reveal(250)
    }
    LaunchedEffect(skipped) { if (skipped) reveal(150) }

    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = screen.value }
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { skipped = true } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .graphicsLayer { translationY = -with(density) { Lift.toPx() } * lift.value }
                .size(SplashCircleSize)
                .clip(CircleShape)
                .background(colorResource(R.color.ic_launcher_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(SplashIconSize),
            )
        }
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.graphicsLayer {
                alpha = name.value
                translationY = with(density) { (NameOffset + 8.dp * (1f - name.value)).toPx() }
            },
        )
    }
}
