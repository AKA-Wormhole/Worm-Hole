package holocore.browser.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The single dark-pill / hairline-border look used on the home surface and the
 * browser bottom bar. Every sheet, menu, and settings row should build on these
 * instead of Material's tonal surfaceContainer* scale, so the whole app reads
 * as one consistent design language rather than a mix of styles.
 */
object HoloCoreSurface {
    val PillShape: Shape = RoundedCornerShape(percent = 50)
    val CardShape: Shape = RoundedCornerShape(20.dp)
    val SheetShape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Fill for pills, cards, and chrome. Follows light/dark. */
    val Fill: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerLowest

    val FillRaised: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainer

    val HairlineBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    @Composable
    @ReadOnlyComposable
    fun border(width: Dp = 1.dp) = BorderStroke(width, HairlineBorder)
}

/** A pill or card surface filled with [HoloCoreSurface.Fill] and a hairline border, tappable. */
@Composable
fun HoloCoreTile(
    modifier: Modifier = Modifier,
    shape: Shape = HoloCoreSurface.PillShape,
    color: Color = HoloCoreSurface.Fill,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = shape,
        color = color,
        border = HoloCoreSurface.border(),
        modifier = if (onClick != null) modifier.bouncyClickable(onClick = onClick) else modifier,
    ) {
        content()
    }
}

/**
 * A settings/menu row styled like the rest of the app: dark pill, hairline border,
 * optional leading icon, title + subtitle, optional trailing slot.
 */
@Composable
fun HoloCoreRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    HoloCoreTile(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else HoloCoreSurface.Fill,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else iconTint,
                    )
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            trailing?.invoke()
        }
    }
}

/** Circular icon badge on the dark fill, used for menu quick-access icons and sheet avatars. */
@Composable
fun HoloCoreIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = (if (onClick != null) modifier.bouncyClickable(onClick = onClick) else modifier)
            .size(size)
            .background(HoloCoreSurface.Fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}

/** Custom on/off switch matching the app's pill language instead of Material's default track. */
@Composable
fun HoloCoreSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor = if (checked) MaterialTheme.colorScheme.primary else HoloCoreSurface.FillRaised
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(HoloCoreSurface.PillShape)
            .background(if (enabled) trackColor else trackColor.copy(alpha = 0.4f))
            .border(1.dp, HoloCoreSurface.HairlineBorder, HoloCoreSurface.PillShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    (if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                        .let { if (enabled) it else it.copy(alpha = 0.6f) },
                ),
        )
    }
}
