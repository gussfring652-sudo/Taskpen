package com.antakih.taskpen

import org.junit.Test
import java.io.File

class NLPTest3 {
    @Test
    fun testRegex() {
        val dateSlashRegex = Regex("(?i)\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b")
        val match = dateSlashRegex.find("Comprar pan 10/10/26")
        
        var out = "Match result: ${match?.value}\n"
        
        File("regex_test_output.txt").writeText(out)
    }
}
