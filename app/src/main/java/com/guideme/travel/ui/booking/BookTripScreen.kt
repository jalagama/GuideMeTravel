package com.guideme.travel.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guideme.travel.R
import com.guideme.travel.ui.components.GuideMeCard

@Composable
fun BookTripScreen(
    uiState: BookTripUiState,
    onDownloadOffline: () -> Unit,
    onStartOnline: () -> Unit
) {
    when {
        uiState.isLoading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }
        uiState.detail == null -> {
            Text(
                text = uiState.errorMessage ?: "Trip not available",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(20.dp)
            )
        }
        else -> {
            val detail = uiState.detail
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GuideMeCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "${detail.region} • ${detail.days} days • ${detail.spots.size} spots",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = stringResource(R.string.free_badge),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.book_trip_explainer),
                    style = MaterialTheme.typography.bodyLarge
                )

                GuideMeCard {
                    Text(
                        stringResource(R.string.download_offline_pack),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(
                            R.string.offline_pack_optional_hint,
                            uiState.estimatedPackSizeMb
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                GuideMeCard {
                    Text(
                        stringResource(R.string.start_trip_online),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.start_trip_online_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.errorMessage != null) {
                    Text(
                        uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = onDownloadOffline,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isBooking
                ) {
                    if (uiState.isBooking) {
                        CircularProgressIndicator()
                    } else {
                        Text(stringResource(R.string.download_offline_pack))
                    }
                }

                OutlinedButton(
                    onClick = onStartOnline,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isBooking
                ) {
                    Text(stringResource(R.string.start_trip_online))
                }
            }
        }
    }
}
