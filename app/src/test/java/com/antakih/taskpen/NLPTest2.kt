package com.antakih.taskpen

import com.antakih.taskpen.domain.usecases.ParseHandwrittenTextUseCase
import org.junit.Test
import java.util.Calendar
import java.io.File

class NLPTest2 {
    @Test
    fun testParse() {
        val useCase = ParseHandwrittenTextUseCase()
        val result = useCase.invoke(listOf(Pair("Comprar pan 10/10/26", 0f)))
        
        val date = result.tasks[0].dueDate
        var dateStr = "null"
        if (date != null) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = date
            dateStr = cal.time.toString()
        }
        
        val out = """
        Tasks: ${result.tasks.size}
        Title: '${result.tasks[0].title}'
        Date: $dateStr
        """.trimIndent()
        
        File("test_output.txt").writeText(out)
    }
}
