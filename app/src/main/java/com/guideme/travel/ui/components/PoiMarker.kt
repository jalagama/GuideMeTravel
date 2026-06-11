package com.guideme.travel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guideme.travel.R
import com.guideme.travel.domain.model.PoiCategory

@Composable
fun PoiCategoryIcon(
    category: PoiCategory,
    modifier: Modifier = Modifier.size(40.dp),
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier.background(Color(category.mapTintColor), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(category.iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun PoiListHeader(
    category: PoiCategory,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PoiCategoryIcon(category = category)
        Column {
            Text(
                text = stringResource(R.string.poi_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = category.displayName(),
                style = MaterialTheme.typography.labelLarge,
                color = Color(category.mapTintColor)
            )
        }
    }
}

@Composable
fun PoiCategoryLegend(
    categories: List<PoiCategory>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        categories.distinct().forEach { category ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PoiCategoryIcon(
                    category = category,
                    modifier = Modifier.size(24.dp),
                    iconSize = 14.dp
                )
                Text(
                    text = category.displayName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PoiCategory.displayName(): String {
    return when (this) {
        PoiCategory.PARK -> stringResource(R.string.poi_category_park)
        PoiCategory.MONUMENT -> stringResource(R.string.poi_category_monument)
        PoiCategory.VIEWPOINT -> stringResource(R.string.poi_category_viewpoint)
        PoiCategory.BEACH -> stringResource(R.string.poi_category_beach)
        PoiCategory.LANDMARK -> stringResource(R.string.poi_category_landmark)
    }
}
