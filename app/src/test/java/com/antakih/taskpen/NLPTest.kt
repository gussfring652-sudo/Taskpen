package com.antakih.taskpen

import com.antakih.taskpen.domain.usecases.ParseHandwrittenTextUseCase
import org.junit.Test

class NLPTest {
    @Test
    fun testDateSlash() {
        val useCase = ParseHandwrittenTextUseCase()
        val result = useCase.invoke(listOf(Pair("Comprar pan 10/10/26", 0f)))
        println("Tasks length: " + result.tasks.size)
        println("Title: " + result.tasks[0].title)
        println("DueDate: " + result.tasks[0].dueDate)
    }
}
