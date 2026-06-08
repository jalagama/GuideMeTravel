package com.guideme.travel.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guideme.travel.domain.model.LanguageOptions
import com.guideme.travel.ui.components.GuideMeCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onCreatePlan: () -> Unit,
    onOpenTrip: (String) -> Unit
) {
    var languageExpanded by remember { mutableStateOf(false) }
    val selectedLanguage = LanguageOptions.supported.firstOrNull { it.code == uiState.languageCode }
        ?: LanguageOptions.supported.first()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Plan your next adventure",
                style = MaterialTheme.typography.displaySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Curated attractions, offline maps, and audio guides that start automatically as you arrive.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            GuideMeCard {
                Text("Create a trip", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = uiState.origin,
                    onValueChange = onOriginChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Starting from") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.destination,
                    onValueChange = onDestinationChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Destination") },
                    singleLine = true
                )
                ExposedDropdownMenuBox(
                    expanded = languageExpanded,
                    onExpandedChange = { languageExpanded = !languageExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedLanguage.displayName,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        label = { Text("Guide language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false }
                    ) {
                        LanguageOptions.supported.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language.displayName) },
                                onClick = {
                                    onLanguageChange(language.code)
                                    languageExpanded = false
                                }
                            )
                        }
                    }
                }
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = onCreatePlan,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Text("Create travel plan")
                    }
                }
            }
        }

        if (uiState.recentTrips.isNotEmpty()) {
            item {
                Text("Recent trips", style = MaterialTheme.typography.headlineMedium)
            }
            items(uiState.recentTrips, key = { it.id }) { trip ->
                GuideMeCard(
                    modifier = Modifier.clickable { onOpenTrip(trip.id) }
                ) {
                    Text("${trip.origin} → ${trip.destination}", style = MaterialTheme.typography.titleLarge)
                    Text("${trip.attractions.size} attractions • ${trip.status.name.lowercase()}")
                }
            }
        }
    }
}
