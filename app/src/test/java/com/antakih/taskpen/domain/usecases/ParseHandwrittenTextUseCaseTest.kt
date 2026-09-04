package com.antakih.taskpen.domain.usecases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseHandwrittenTextUseCaseTest {

    @Test
    fun `verificar maquina de estados con horas, herencia y multilineas`() {
        val useCase = ParseHandwrittenTextUseCase()

        // 1. Añadimos el caso del pan al final del texto escaneado
        val rawScannedText = """
            4 de septiembre
            * Bañar al perro a las 4 pm
            Asegurarse de usar agua tibia
            Comprar el shampoo especial
            Robótica
            - Terminar reporte de simulacion
            - Investigar sensores Adm. Proy
            * comprar pan el lunes a las 3 am
        """.trimIndent()

        val tasks = useCase(rawScannedText)

        println("=== TAREAS EXTRAÍDAS ===")
        tasks.forEach { task ->
            println("Título: ${task.title}")
            println("Descripción:\n${task.description ?: "N/A"}")
            println("Materia: ${task.subjectId ?: "General"}")
            println("Tiene hora exacta: ${task.hasSpecificTime}")
            // Imprimimos la fecha en milisegundos para comprobar que existe
            println("Fecha Límite (Timestamp): ${task.dueDate}")
            println("--------------------------")
        }

        // 2. Ahora esperamos 4 tareas en total
        assertEquals(4, tasks.size)

        // Tarea 1: Bañar al perro (Prueba de limpieza de "a las")
        assertEquals("Bañar al perro", tasks[0].title)
        assertTrue(tasks[0].hasSpecificTime)

        // Tarea 2: Robótica (Prueba de herencia multilínea)
        assertEquals("Terminar reporte de simulacion", tasks[1].title)
        assertEquals("Robótica", tasks[1].subjectId)

        // Tarea 3: Inline Subject (Prueba de borrar materia del título)
        assertEquals("Investigar sensores", tasks[2].title)
        assertEquals("Adm. Proy", tasks[2].subjectId)

        // Tarea 4: El Pan (Prueba de días relativos y limpieza doble)
        assertEquals("comprar pan", tasks[3].title) // Debe estar perfectamente limpio
        assertTrue(tasks[3].hasSpecificTime) // Detectó las 3 am
    }
}