package com.antakih.taskpen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antakih.taskpen.data.local.entities.CategoryEntity
import com.antakih.taskpen.ui.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    viewModel: TaskViewModel,
    onCategorySelected: (CategoryEntity?) -> Unit
) {
    val categories by viewModel.allCategories.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TaskPen", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nueva categoría")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                CategoryCard(
                    name = "General",
                    subtitle = "Tareas sin categoría",
                    onClick = { onCategorySelected(null) }
                )
            }

            items(categories, key = { it.id }) { category ->
                CategoryCard(
                    name = category.name,
                    subtitle = null,
                    onClick = {
                        viewModel.setActiveCategory(category.id)
                        onCategorySelected(category)
                    }
                )
            }

            if (categories.isEmpty()) {
                item {
                    Text(
                        text = "Sugerencias:",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
                val suggestions = listOf("Universidad", "Trabajo", "Casa", "Personal")
                items(suggestions) { suggestion ->
                    SuggestionChip(
                        onClick = { viewModel.createCategory(suggestion) },
                        label = { Text("+ $suggestion") }
                    )
                }
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false; newCategoryName = "" },
                title = { Text("Nueva categoría") },
                text = {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Nombre (ej. Universidad)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (newCategoryName.isNotBlank()) {
                            viewModel.createCategory(newCategoryName.trim())
                            newCategoryName = ""
                            showCreateDialog = false
                        }
                    }) { Text("Crear") }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false; newCategoryName = "" }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
private fun CategoryCard(name: String, subtitle: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = name, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
