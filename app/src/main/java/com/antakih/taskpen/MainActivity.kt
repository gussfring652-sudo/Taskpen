package com.antakih.taskpen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.antakih.taskpen.data.local.entities.CategoryEntity
import com.antakih.taskpen.ui.screens.DashboardScreen
import com.antakih.taskpen.ui.screens.TaskDetailScreen
import com.antakih.taskpen.ui.theme.TaskpenTheme
import com.antakih.taskpen.ui.viewmodel.TaskViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TaskpenTheme {
                TaskpenNavigation(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun TaskpenNavigation(viewModel: TaskViewModel) {
    var selectedTask by remember { mutableStateOf<TaskEntity?>(null) }
    
    if (selectedTask != null) {
        TaskDetailScreen(
            task = selectedTask!!,
            viewModel = viewModel,
            onBack = { selectedTask = null }
        )
    } else {
        DashboardScreen(
            viewModel = viewModel,
            onTaskClick = { task -> selectedTask = task }
        )
    }
}
