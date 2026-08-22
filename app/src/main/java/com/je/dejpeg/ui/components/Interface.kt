package com.je.dejpeg.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val PillOuter = 50.dp
val PillInner = 6.dp

@Composable
fun toolbarSegmentColors(isActive: Boolean): Triple<Color, Color, Color> {
    val iconColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )
    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )
    return Triple(iconColor, containerColor, contentColor)
}

@Composable
fun ToolbarSegmentButton(
    isActive: Boolean,
    icon: ImageVector,
    label: String,
    isLeading: Boolean, // controls which corners pinch on press
    onClick: () -> Unit,
) {
    val (iconColor, containerColor, contentColor) = toolbarSegmentColors(isActive)
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(
            shape = RoundedCornerShape(PillOuter),
            pressedShape = RoundedCornerShape(
                topStart = if (isLeading) PillOuter else PillInner,
                bottomStart = if (isLeading) PillOuter else PillInner,
                topEnd = if (isLeading) PillInner else PillOuter,
                bottomEnd = if (isLeading) PillInner else PillOuter,
            ),
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor, contentColor = contentColor
        ),
        modifier = Modifier
            .height(52.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Icon(icon, contentDescription = label, tint = iconColor)
        if (!isActive) {
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(label)
        }
    }
}

enum class CardPosition { Leading, Center, Trailing, Solo }

val GroupedListSpacing: Dp = 2.dp
val ScreenHorizontalPadding: Dp = 16.dp

fun cardShape(
    position: CardPosition, outer: Dp = 16.dp, inner: Dp = 6.dp
): RoundedCornerShape = when (position) {
    CardPosition.Leading -> RoundedCornerShape(
        topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner
    )

    CardPosition.Center -> RoundedCornerShape(inner)
    CardPosition.Trailing -> RoundedCornerShape(
        topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer
    )

    CardPosition.Solo -> RoundedCornerShape(outer)
}

fun positionFor(index: Int, count: Int): CardPosition = when {
    count <= 1 -> CardPosition.Solo
    index == 1 -> CardPosition.Leading // start is 1 for simplicity
    index == count -> CardPosition.Trailing // end so trail
    else -> CardPosition.Center
}

inline fun Modifier.thenIf(condition: Boolean, factory: Modifier.() -> Modifier): Modifier =
    if (condition) factory() else this

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GroupedRow(
    position: CardPosition,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    elevation: Dp = 10.dp,
    hideExtras: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    verticalPadding: Dp = 14.dp,
    horizontalPadding: Dp = 14.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    tooltip: String = "",
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedOuter by animateDpAsState(
        targetValue = if (isPressed) 16.dp else 6.dp,
        label = "groupedRowOuterCorner",
    )
    val shape = cardShape(position, inner = animatedOuter)
    val background = if (selected) {
        MaterialTheme.colorScheme.surfaceColorAtElevation(elevation * 4)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(elevation)
    }
    val selectionBorderColor by animateColorAsState(
        targetValue = if (selected && !hideExtras) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "selectionBorder"
    )
    val row = @Composable {
        Box(modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .alpha(if (enabled) 1f else 0.6f)
                    .background(background, shape)
                    .border(2.dp, selectionBorderColor, shape)
                    .thenIf(enabled && (onClick != null || onLongClick != null)) {
                        combinedClickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = { onClick?.invoke() },
                            onLongClick = onLongClick,
                        )
                    }
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding,
                    ),
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
            if (!hideExtras) {
                AnimatedVisibility(
                    visible = selected,
                    modifier = Modifier.align(Alignment.TopEnd),
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Box(
                        Modifier
                            .padding(6.dp)
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
    if (tooltip.isNotEmpty()) {
        val tooltipState = rememberTooltipState()
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip {
                    Text(tooltip)
                }
            },
            state = tooltipState,
        ) {
            row()
        }
    } else {
        row()
    }
}

//@Composable
//fun ScreenScaffold(
//    modifier: Modifier = Modifier,
//    topBar: @Composable () -> Unit = {},
//    bottomBar: @Composable () -> Unit = {},
//    snackbarHost: @Composable () -> Unit = {},
//    content: @Composable (PaddingValues) -> Unit,
//) {
//    Scaffold(
//        modifier = modifier,
//        topBar = topBar,
//        bottomBar = bottomBar,
//        snackbarHost = snackbarHost,
//        content = content,
//    )
//}
//
//fun Modifier.screenContentPadding(innerPadding: PaddingValues): Modifier =
//    this
//        .padding(innerPadding)
//        .consumeWindowInsets(innerPadding)
//        .padding(horizontal = ScreenHorizontalPadding)
