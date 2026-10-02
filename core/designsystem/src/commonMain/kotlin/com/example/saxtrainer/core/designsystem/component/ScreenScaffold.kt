package com.example.saxtrainer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.saxtrainer.core.designsystem.icon.ArrowBack
import com.example.saxtrainer.core.designsystem.icon.SaxTrainerIcons
import com.example.saxtrainer.core.designsystem.theme.Dimens
import com.example.saxtrainer.core.designsystem.theme.ProgressColors

/**
 * What a screen says for itself, above the rule at the top.
 *
 * Two kinds, because a screen being set up and a screen mid-exercise are saying different things: a
 * practice waiting to be started explains what it is, and a practice running asks for something.
 */
sealed interface ScreenHeader {

    /** What the screen is asking for right now. Set apart, because it is the task. */
    data class Question(val text: String) : ScreenHeader

    /** What this tool is, in plain words. A sentence or two, never a paragraph. */
    data class Description(val text: String) : ScreenHeader
}

/**
 * Time running out on the thing the player is doing.
 *
 * Drawn as a bar across the whole width, immediately above the buttons, so it is always in the same
 * place and always the same size - a glance down tells you how long is left without reading
 * anything. [fraction] is how much of the time has gone, 0 to 1.
 *
 * Above the bar the same line always reads `3 / 8  -  5 s`. Both numbers are given room for the
 * largest they will reach - [stepCount] steps and [secondsAtMost] seconds - so counting from 12 down
 * to 1, or from 23 seconds down to 1, never shifts the line about while it is being watched.
 */
data class ScreenTiming(
    /**
     * The bar that matters now, 0 to 1: how much of the current item has gone where the practice
     * times its items, and how much of the whole session has gone where it does not.
     */
    val fraction: Float,
    /**
     * The session underneath, drawn as a thinner line, and only where [fraction] is measuring
     * something smaller than the whole run. Null leaves it off rather than drawing a bar that
     * repeats the one above it.
     */
    val sessionFraction: Float? = null,
    /** Which step of the exercise, counted from one. */
    val step: Int,
    val stepCount: Int,
    /** Null where nothing is being timed; the line then reads just the position. */
    val secondsLeft: Int? = null,
    /** The most seconds this will ever show, which is what the space is reserved for. */
    val secondsAtMost: Int = 0,
    val paused: Boolean = false,
)

/**
 * Where the screens below sit, outermost first: `My Lessons / Scales / Test2`.
 *
 * Provided by whoever opened a screen for a reason of its own - a lesson running a practice - rather
 * than passed to it, so the practice never has to learn that it is inside a lesson. Empty, the
 * default, draws no trail.
 */
val LocalScreenTrail = staticCompositionLocalOf<List<String>> { emptyList() }

/**
 * Shared screen frame: title bar, an optional header, scrolling content, and an action panel
 * pinned to the bottom.
 *
 * The title bar and the action panel are drawn in the same raised surface, so the screen reads as
 * one band of chrome at the top, one at the bottom, and the exercise itself in between.
 *
 * The buttons that run an exercise stay in the same place whatever the screen is showing, because
 * a player looking for Stop should not have to scroll to find it - and on a running exercise the
 * content above them changes size from note to note. [content] scrolls under the panel; the panel
 * itself never moves.
 *
 * Screens therefore hand over their scrolling: everything in [content] is laid out in one column
 * that this frame scrolls, so a child of it must not ask for a vertical `weight`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /**
     * Icons at the far end of the title bar: what can be done with the screen as a whole - star it,
     * keep it in My Lessons - rather than with anything on it. See [PracticeTitleActions].
     */
    titleActions: (@Composable RowScope.() -> Unit)? = null,
    /**
     * Whether [LocalScreenTrail] is drawn as one line under the title, in the title's band. Off while
     * an exercise is under way: by then the player knows where they are, and the space is the
     * exercise's.
     */
    showTrail: Boolean = true,
    /**
     * What this screen says for itself. Pinned under the title with a rule below it, so it stays
     * put while everything it refers to scrolls.
     */
    header: ScreenHeader? = null,
    /**
     * The thing the exercise is about - the note being read, the grip being named.
     *
     * Pinned between the question and the content, with a rule of its own beneath it, so what is
     * being asked about is kept apart from whatever answers it: the options to choose from, the
     * fingering that goes with it, the meters measuring it.
     */
    subject: (@Composable ColumnScope.() -> Unit)? = null,
    /** How long is left, shown across the foot of the screen just above [actions]. */
    timing: ScreenTiming? = null,
    /** The pinned panel. Left out entirely on a screen that has nothing to press. */
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * Whether [content] scrolls when it is taller than the space between the rules.
     *
     * Off for a screen whose content sizes itself to the space instead - a spectrum that should
     * fill whatever is left over, say. A child may only ask for a vertical weight when this is off,
     * because a scrolling column has no height to share out.
     */
    scrollable: Boolean = true,
    /** How [content] is spaced within that space. */
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    /**
     * Whether the device should be kept from sleeping right now - see [KeepScreenOn]. Off by
     * default: only a screen mid-exercise passes true, and only for as long as it actually is.
     */
    keepScreenOn: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    KeepScreenOn(keepScreenOn)

    // The same surface family as the cards, one step below them, so band and cards always match.
    val chrome = MaterialTheme.colorScheme.surfaceContainer
    val trail = if (showTrail) LocalScreenTrail.current else emptyList()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = chrome),
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            InlineIcon(
                                icon = IconSource.Vector(SaxTrainerIcons.ArrowBack),
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = { titleActions?.invoke(this) },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (timing != null) {
                    TimingStrip(timing, rule = chrome)
                }
                if (actions != null) {
                    // Raised off the content so the panel reads as a fixed surface rather than as
                    // the end of a list that happens to have stopped there.
                    Surface(color = chrome, shadowElevation = PanelElevation) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(
                                    horizontal = Dimens.screenPadding,
                                    vertical = Dimens.spaceMd,
                                ),
                            content = actions,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (trail.isNotEmpty()) {
                TrailBar(trail, band = chrome)
            }
            if (header != null) {
                HeaderBar(header, rule = chrome)
            }
            if (subject != null) {
                // A band right across the screen, in the colour the landing page gives its
                // tiles: the subject reads as its own zone without becoming a floating object.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceMd),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                    content = subject,
                )
                HorizontalDivider(color = chrome)
            }
            // Always the whole space between the rules, so what is in it can be centred in the
            // screen rather than piling up under the question.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Dimens.screenPadding)
                    .padding(vertical = Dimens.spaceMd)
                    .then(
                        if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                    ),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    }
}

/**
 * What the screen says for itself, and the rule that separates it from the rest.
 *
 * A question is centred and set in the title face, because it is being asked. A description is
 * ordinary running text on the left, because it is only telling you where you are - dressing it up
 * as a question would make choosing a note set look like the exercise.
 *
 * The rule is the chrome colour rather than an outline: it should read as the edge of the band the
 * title sits in, not as a border drawn around something.
 */
@Composable
private fun HeaderBar(header: ScreenHeader, rule: Color) {
    val padding = Modifier
        .fillMaxWidth()
        .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceMd)

    when (header) {
        is ScreenHeader.Question -> Text(
            text = header.text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = padding,
        )

        is ScreenHeader.Description -> Text(
            text = header.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = padding,
        )
    }
    HorizontalDivider(color = rule)
}

/**
 * Where the screen sits, as one quiet line in the title's own band.
 *
 * Part of the chrome rather than of the content: it names the place, like the title above it, so it
 * shares the title's background and the header's rule still marks where the chrome ends. One line,
 * cut short at the end, because the innermost name is the one that matters.
 */
@Composable
private fun TrailBar(trail: List<String>, band: Color) {
    Text(
        text = trail.joinToString(TRAIL_SEPARATOR),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .background(band)
            .padding(horizontal = Dimens.screenPadding)
            .padding(bottom = Dimens.spaceSm),
    )
}

private const val TRAIL_SEPARATOR = " / "

/**
 * How long is left: the same rule as under the question, then the time in words, then the bar.
 *
 * The bar runs the full width with no padding of its own, so it reads as a measure of the screen
 * rather than as one more control sitting in a column of them. It sits on the content background
 * rather than on the panel, which is what leaves the rule visible above it.
 */
@Composable
private fun TimingStrip(timing: ScreenTiming, rule: Color) {
    HorizontalDivider(color = rule)
    TimingLine(timing)
    TimingBar(fraction = timing.fraction, color = ProgressColors.current)
    // The session underneath, the same size and told apart by colour alone: it is context, not a
    // second thing to watch. Only drawn where it would say something the bar above does not.
    if (timing.sessionFraction != null) {
        TimingBar(fraction = timing.sessionFraction, color = ProgressColors.session)
    }
}

@Composable
private fun TimingBar(fraction: Float, color: Color) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(TimingBarHeight),
        color = color,
        trackColor = ProgressColors.track,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/**
 * `3 / 8  -  5 s`, and `-  paused` when it is being held.
 *
 * The two numbers sit in slots of one shared width, wide enough for the largest either will ever
 * show, so a number growing a digit does not shove the line sideways while the player is watching
 * it. The line as a whole stays centred; only adding "paused" is allowed to move it, since that
 * happens once rather than every second.
 */
@Composable
private fun TimingLine(timing: ScreenTiming) {
    val style = MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val slot = with(LocalDensity.current) {
        val position = measurer.measure(positionOf(timing.stepCount, timing.stepCount), style)
        val seconds = timing.secondsLeft
            ?.let { measurer.measure(secondsOf(timing.secondsAtMost), style).size.width }
            ?: 0
        maxOf(position.size.width, seconds).toDp() + Dimens.spaceSm
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = positionOf(timing.step, timing.stepCount),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(slot),
        )
        if (timing.secondsLeft != null) {
            Dash(style)
            Text(
                text = secondsOf(timing.secondsLeft),
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(slot),
            )
        }
        if (timing.paused) {
            Dash(style)
            Text(
                text = "paused",
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Dash(style: TextStyle) {
    Text(
        text = "-",
        style = style,
        color = MaterialTheme.colorScheme.outline,
    )
}

private fun positionOf(step: Int, stepCount: Int): String = "$step / $stepCount"

private fun secondsOf(seconds: Int): String = "$seconds s"

private val PanelElevation: Dp = Dimens.spaceXs

/** Twice the height Material draws a linear indicator at, so it reads at a glance. */
private val TimingBarHeight: Dp = 8.dp

