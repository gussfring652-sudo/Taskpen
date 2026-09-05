package com.antakih.taskpen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antakih.taskpen.ui.screens.DrawingScreen
import com.antakih.taskpen.ui.screens.TaskScreen
import com.antakih.taskpen.ui.theme.TaskpenTheme
import com.antakih.taskpen.ui.viewmodel.TaskViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Hilt inyecta el ViewModel aquí
    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TaskpenTheme {
                // Dividimos la pantalla mitad y mitad
                Column(modifier = Modifier.fillMaxSize()) {
                    // Mitad superior: La lista de tareas (reactiva con Room)
                    Box(modifier = Modifier.weight(1f)) {
                        TaskScreen(viewModel = viewModel)
                    }

                    // Separador visual
                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary)

                    // Mitad inferior: El lienzo del S-Pen
                    Box(modifier = Modifier.weight(1f)) {
                        DrawingScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
