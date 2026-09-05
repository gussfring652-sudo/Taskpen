package com.antakih.taskpen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
                TaskScreen(viewModel = viewModel)
            }
        }
    }
}
