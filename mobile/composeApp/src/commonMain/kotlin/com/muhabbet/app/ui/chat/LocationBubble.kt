package com.muhabbet.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.util.Log
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.attach_location
import com.muhabbet.composeapp.generated.resources.location_open_in_maps
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.LocationData
import kotlinx.serialization.json.Json
import com.muhabbet.designsystem.Muhabbet
import org.jetbrains.compose.resources.stringResource

private const val TAG = "LocationBubble"

private val locationJson = Json { ignoreUnknownKeys = true }

/**
 * A shared location: label, coordinates, and a tap that opens the point in a map.
 *
 * @param onOpenUrl handed the maps URL; the caller opens it and reports a failure, because the
 *   snackbar host lives on the screen rather than on the bubble.
 */
@Composable
fun LocationBubble(
    content: String,
    isOwn: Boolean,
    onOpenUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val locationData = remember(content) {
        val parsed = try {
            locationJson.decodeFromString<LocationData>(content)
        } catch (e: Exception) {
            // The bubble renders nothing for an unreadable payload rather than throwing out of
            // composition, but a message arriving in a shape this screen cannot read is worth a
            // line in the log — silently drawing an empty bubble hides it completely.
            Log.w(TAG, "Unreadable location payload: ${e.message}")
            null
        }
        // Out-of-range or NaN coordinates parse fine and would render, and their map link would
        // point nowhere. Treat them exactly like an unreadable payload rather than offering a tap
        // that lands in the middle of the ocean. NaN fails both range checks by definition.
        parsed?.takeIf { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
    }

    if (locationData == null) return

    val openInMapsLabel = stringResource(Res.string.location_open_in_maps)

    Row(
        modifier = modifier
            .clickable(onClickLabel = openInMapsLabel) { onOpenUrl(mapsUrlFor(locationData)) }
            .padding(horizontal = MuhabbetSpacing.Small, vertical = MuhabbetSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Muhabbet.icons.Location,
            // Named, not decorative: with the pin unlabelled a screen reader announced a pair of
            // bare numbers and "double tap to activate", which says nothing about what this is.
            contentDescription = stringResource(Res.string.attach_location),
            modifier = Modifier.size(32.dp),
            tint = if (isOwn) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(MuhabbetSpacing.Small))
        Column {
            locationData.label?.let { labelText ->
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOwn) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${formatCoord(locationData.latitude)}, ${formatCoord(locationData.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * A plain `https://` maps link rather than a `geo:` URI or an expect/actual pair.
 *
 * `geo:` is Android-only and iOS drops it, and a platform-specific opener would be two more source
 * sets for a feature whose entire job is to hand a coordinate to something else. This one link is
 * claimed by the Google Maps app on Android, by Google Maps on iOS when it is installed, and falls
 * back to the browser everywhere else — degraded on an iPhone without Google Maps, but never a
 * dead tap, which is what it is today.
 *
 * The coordinates go through [formatCoord] so the URL never carries a locale's decimal comma or
 * `Double.toString`'s scientific notation, either of which the maps query parser reads as garbage.
 */
private fun mapsUrlFor(data: LocationData): String =
    "https://www.google.com/maps/search/?api=1&query=" +
        "${formatCoord(data.latitude)},${formatCoord(data.longitude)}"

private fun formatCoord(d: Double): String {
    val sign = if (d < 0) "-" else ""
    val abs = if (d < 0) -d else d
    var whole = abs.toLong()
    var frac = ((abs - whole) * 100000 + 0.5).toLong()
    // Rounding the fraction up can carry into the whole part: 41.999997 became 41 + 100000 and
    // printed as "41.100000". That was a cosmetic wart while this text was only displayed; now the
    // same text is the maps query, and it would drop the pin somewhere else entirely.
    if (frac >= 100000) {
        whole += 1
        frac = 0
    }
    return "$sign$whole.${frac.toString().padStart(5, '0')}"
}
