package com.antakih.taskpen.ui.utils

import com.antakih.taskpen.data.local.entities.TaskEntity

fun groupTasksChronologically(tasks: List<TaskEntity>): List<Pair<String, List<TaskEntity>>> {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis

    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
    cal.set(java.util.Calendar.MINUTE, 59)
    cal.set(java.util.Calendar.SECOND, 59)
    val todayEnd = cal.timeInMillis

    val calWeek = java.util.Calendar.getInstance()
    calWeek.timeInMillis = todayEnd
    calWeek.firstDayOfWeek = java.util.Calendar.MONDAY
    calWeek.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
    if (calWeek.timeInMillis < todayEnd) {
        calWeek.add(java.util.Calendar.WEEK_OF_YEAR, 1)
    }
    val weekEnd = calWeek.timeInMillis

    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    val pasadas = mutableListOf<TaskEntity>()
    val hoy = mutableListOf<TaskEntity>()
    val estaSemana = mutableListOf<TaskEntity>()
    val porMeses = mutableMapOf<String, MutableList<TaskEntity>>()
    val proximoAno = mutableListOf<TaskEntity>()
    val sinFecha = mutableListOf<TaskEntity>()

    tasks.forEach { task ->
        if (task.dueDate == null) {
            sinFecha.add(task)
            return@forEach
        }
        val date = task.dueDate
        if (date < todayStart) {
            pasadas.add(task)
        } else if (date <= todayEnd) {
            hoy.add(task)
        } else if (date <= weekEnd) {
            estaSemana.add(task)
        } else {
            val taskCal = java.util.Calendar.getInstance()
            taskCal.timeInMillis = date
            val taskYear = taskCal.get(java.util.Calendar.YEAR)
            if (taskYear > currentYear) {
                proximoAno.add(task)
            } else {
                val monthName = java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault()).format(java.util.Date(date)).replaceFirstChar { it.uppercase() }
                val key = "$monthName $taskYear"
                porMeses.getOrPut(key) { mutableListOf() }.add(task)
            }
        }
    }

    val result = mutableListOf<Pair<String, List<TaskEntity>>>()
    if (pasadas.isNotEmpty()) result.add(Pair("Pasadas", pasadas.sortedBy { it.dueDate }))
    if (hoy.isNotEmpty()) result.add(Pair("Hoy", hoy.sortedBy { it.dueDate }))
    if (estaSemana.isNotEmpty()) result.add(Pair("Esta semana", estaSemana.sortedBy { it.dueDate }))
    
    val sortedMonths = porMeses.values.toList().sortedBy { it.first().dueDate }
    sortedMonths.forEach { monthTasks ->
        val sortedTasks = monthTasks.sortedBy { it.dueDate }
        val date = java.util.Date(sortedTasks.first().dueDate!!)
        val monthName = java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault()).format(date).replaceFirstChar { it.uppercase() }
        result.add(Pair(monthName, sortedTasks))
    }

    if (proximoAno.isNotEmpty()) result.add(Pair("Próximo año", proximoAno.sortedBy { it.dueDate }))
    if (sinFecha.isNotEmpty()) result.add(Pair("Sin fecha", sinFecha.sortedByDescending { it.createdAt }))

    return result
}
