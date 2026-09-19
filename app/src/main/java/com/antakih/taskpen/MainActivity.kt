package com.antakih.taskpen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.antakih.taskpen.notifications.SummaryScheduler
import com.antakih.taskpen.ui.screens.DashboardScreen
import com.antakih.taskpen.ui.theme.TaskpenTheme
import com.antakih.taskpen.ui.viewmodel.TaskViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: TaskViewModel by viewModels()

    @Inject lateinit var summaryScheduler: SummaryScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Asegurar que los Workers de resúmenes diarios están encolados
        lifecycleScope.launch {
            summaryScheduler.scheduleAll()
        }

        setContent {
            TaskpenTheme {
                DashboardScreen(viewModel = viewModel)
            }
        }
    }
}
