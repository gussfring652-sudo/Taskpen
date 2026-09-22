package com.antakih.taskpen.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderOffsetPicker(
    label: String,
    initialValueMinutes: Int?,
    isCascadeMode: Boolean = false,
    onOffsetChanged: (Int?) -> Unit
) {
    val units = if (isCascadeMode) listOf("Horas", "Días") else listOf("Minutos", "Horas")
    
    var amount by remember { mutableIntStateOf(1) }
    var selectedUnit by remember { mutableStateOf(units.first()) }
    var isEnabled by remember { mutableStateOf(initialValueMinutes != null) }

    LaunchedEffect(initialValueMinutes) {
        if (initialValueMinutes != null) {
            isEnabled = true
            if (isCascadeMode) {
                if (initialValueMinutes >= 1440 && initialValueMinutes % 1440 == 0) {
                    amount = initialValueMinutes / 1440
                    selectedUnit = "Días"
                } else {
                    amount = initialValueMinutes / 60
                    selectedUnit = "Horas"
                }
            } else {
                if (initialValueMinutes >= 60 && initialValueMinutes % 60 == 0) {
                    amount = initialValueMinutes / 60
                    selectedUnit = "Horas"
                } else {
                    amount = initialValueMinutes
                    selectedUnit = "Minutos"
                }
            }
        }
    }

    val updateParent = {
        if (isEnabled) {
            val multiplier = when (selectedUnit) {
                "Días" -> 1440
                "Horas" -> 60
                else -> 1
            }
            onOffsetChanged(amount * multiplier)
        } else {
            onOffsetChanged(null)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(
                checked = isEnabled,
                onCheckedChange = { 
                    isEnabled = it
                    updateParent()
                }
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }

        if (isEnabled) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 48.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Dropdown para Cantidad
                var expandedAmount by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedAmount,
                    onExpandedChange = { expandedAmount = !expandedAmount },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = amount.toString(),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAmount) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedAmount,
                        onDismissRequest = { expandedAmount = false }
                    ) {
                        (1..60).forEach { num ->
                            DropdownMenuItem(
                                text = { Text(num.toString()) },
                                onClick = { 
                                    amount = num
                                    expandedAmount = false
                                    updateParent()
                                }
                            )
                        }
                    }
                }

                // Dropdown para Unidad
                var expandedUnit by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedUnit,
                    onExpandedChange = { expandedUnit = !expandedUnit },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedUnit,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnit) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedUnit,
                        onDismissRequest = { expandedUnit = false }
                    ) {
                        units.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u) },
                                onClick = { 
                                    selectedUnit = u
                                    expandedUnit = false
                                    updateParent()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
