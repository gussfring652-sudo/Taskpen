package com.antakih.taskpen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.antakih.taskpen.data.local.entities.CategoryEntity
import com.antakih.taskpen.ui.screens.CategoryScreen
import com.antakih.taskpen.ui.screens.TaskScreen
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
    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var inTaskScreen by remember { mutableStateOf(false) }

    if (!inTaskScreen) {
        CategoryScreen(
            viewModel = viewModel,
            onCategorySelected = { category ->
                selectedCategory = category
                inTaskScreen = true
            }
        )
    } else {
        TaskScreen(
            viewModel = viewModel,
            category = selectedCategory,
            onBack = {
                inTaskScreen = false
                selectedCategory = null
                viewModel.setActiveCategory(null)
            }
        )
    }
}
