package com.guideme.travel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guideme.travel.R
import com.guideme.travel.domain.model.MapPoi
import com.guideme.travel.util.routeDistanceKm

data class RoutePoiDisplay(
    val poi: MapPoi,
    val description: String? = null,
    val estimatedMinutes: Int? = null
)

@Composable
fun PoiRouteMapSection(
    stops: List<RoutePoiDisplay>,
    modifier: Modifier = Modifier,
    mapHeight: androidx.compose.ui.unit.Dp = 400.dp,
    highlightPoiId: String? = null
) {
    if (stops.isEmpty()) return

    val sorted = stops.sortedBy { it.poi.orderIndex }
    val pois = sorted.map { it.poi }
    val start = sorted.first()
    val end = sorted.last()
    val distanceKm = routeDistanceKm(pois)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RouteSummaryCard(
            startName = start.poi.name,
            endName = end.poi.name,
            stopCount = sorted.size,
            distanceKm = distanceKm
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 2.dp
        ) {
            PoiMapView(
                pois = pois,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeight),
                styleUrl = PoiMapStyle.defaultStyleUrl(),
                highlightPoiId = highlightPoiId
            )
        }

        PoiCategoryLegend(categories = pois.map { it.category })

        Text(
            text = stringResource(R.string.route_order),
            style = MaterialTheme.typography.titleMedium
        )

        GuideMeCard {
            Column {
                sorted.forEachIndexed { index, stop ->
                    RouteStopRow(
                        stop = stop,
                        position = index + 1,
                        isStart = index == 0,
                        isEnd = index == sorted.lastIndex,
                        showConnector = index < sorted.lastIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteSummaryCard(
    startName: String,
    endName: String,
    stopCount: Int,
    distanceKm: Double
) {
    GuideMeCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.full_route_summary),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RouteEndpointChip(label = stringResource(R.string.route_start), color = Color(0xFF2E7D32))
                Text(
                    text = startName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Text(
                text = "↓",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RouteEndpointChip(label = stringResource(R.string.route_end), color = Color(0xFFD32F2F))
                Text(
                    text = endName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Text(
                text = stringResource(
                    R.string.route_stats,
                    stopCount,
                    String.format("%.0f", distanceKm)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RouteEndpointChip(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun RouteStopRow(
    stop: RoutePoiDisplay,
    position: Int,
    isStart: Boolean,
    isEnd: Boolean,
    showConnector: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        when {
                            isStart -> Color(0xFF2E7D32)
                            isEnd -> Color(0xFFD32F2F)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isStart -> "S"
                        isEnd -> "E"
                        else -> position.toString()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            if (showConnector) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (showConnector) 16.dp else 0.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PoiCategoryIcon(
                    category = stop.poi.category,
                    modifier = Modifier.size(32.dp),
                    iconSize = 18.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stop.poi.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stop.poi.category.displayName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(stop.poi.category.mapTintColor)
                    )
                }
            }
            stop.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            stop.estimatedMinutes?.let { minutes ->
                Text(
                    text = stringResource(R.string.estimated_visit_minutes, minutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
