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

    private var showDailyReportFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            summaryScheduler.scheduleAll()
        }

        handleIntent(intent)

        setContent {
            val showReport by showDailyReportFlow.collectAsState()
            TaskpenTheme {
                DashboardScreen(
                    viewModel = viewModel, 
                    initialShowDailyReport = showReport,
                    onDismissDailyReport = { showDailyReportFlow.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra("showDailyReport", false) == true) {
            showDailyReportFlow.value = true
            // Limpiar el intent para que no se vuelva a disparar al rotar la pantalla
            intent.removeExtra("showDailyReport")
        }
    }
}
