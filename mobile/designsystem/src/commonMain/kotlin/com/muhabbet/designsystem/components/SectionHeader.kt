package com.muhabbet.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing

/**
 * The heading above a group of related rows.
 *
 * Takes an optional leading indicator, in the two forms the app actually uses: a coloured dot (the
 * storage breakdown's legend) or an icon (the privacy dashboard's sections). Whichever is present
 * tints the title too, so the heading reads as one accented unit rather than a stray coloured mark
 * next to unrelated text. With neither, the title stays neutral but the row still carries a small
 * copper accent bar — every header gets a mark now, not just the ones a caller happened to pass one
 * for, which is what made this the one component in the family with no colour or shape of its own.
 *
 * The privacy dashboard had reimplemented the icon variant privately rather than extending this —
 * which is how the app ended up with two section headers that agreed on nothing but the font.
 *
 * [contentPadding] exists because the two parents are padded differently: the dashboard is a bare
 * `LazyColumn` and needs the header to supply its own inset, while the settings column already has
 * one and would double it.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dotColor: Color? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = MuhabbetSpacing.Large,
        vertical = MuhabbetSpacing.Medium
    )
) {
    // The dot carries its own colour (each storage category has one); the icon uses the accent.
    val titleColor = dotColor ?: if (icon != null) accentColor else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
    ) {
        when {
            dotColor != null -> Box(Modifier.size(MuhabbetSizes.IndicatorDot).clip(CircleShape).background(dotColor))

            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(MuhabbetSizes.IconLarge)
            )

            // Neither given: a small rounded accent bar instead of nothing, so a plain "Notifications"
            // or "Storage" heading still carries the brand's own colour and shape rather than reading
            // as an unstyled bold Text.
            else -> Box(
                Modifier
                    .size(width = MuhabbetSizes.SectionAccentWidth, height = MuhabbetSizes.SectionAccentHeight)
                    .clip(RoundedCornerShape(MuhabbetCorners.Hairline))
                    .background(accentColor)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = titleColor
        )
    }
}
