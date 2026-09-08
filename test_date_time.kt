import java.util.Calendar

fun getMonthNumber(monthName: String): Int {
    return when (monthName.lowercase()) {
        "enero" -> 0
        "febrero" -> 1
        "marzo" -> 2
        "abril" -> 3
        "mayo" -> 4
        "junio" -> 5
        "julio" -> 6
        "agosto" -> 7
        "septiembre" -> 8
        "octubre" -> 9
        "noviembre" -> 10
        "diciembre" -> 11
        else -> 0
    }
}

fun getDayOfWeek(dayName: String): Int {
    return when (dayName.lowercase().replace("é", "e").replace("á", "a")) {
        "domingo" -> Calendar.SUNDAY
        "lunes" -> Calendar.MONDAY
        "martes" -> Calendar.TUESDAY
        "miercoles" -> Calendar.WEDNESDAY
        "jueves" -> Calendar.THURSDAY
        "viernes" -> Calendar.FRIDAY
        "sabado" -> Calendar.SATURDAY
        else -> Calendar.MONDAY
    }
}

fun extractDate(text: String): Pair<String, Calendar?> {
    var cleanedText = text
    var cal: Calendar? = null
    val now = Calendar.getInstance()

    // 1. DD/MM/YYYY or DD/MM/YY
    val dateSlashRegex = Regex("(?i)\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b")
    dateSlashRegex.find(cleanedText)?.let { match ->
        cal = Calendar.getInstance()
        val day = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt() - 1
        var yearStr = match.groupValues[3]
        var year = now.get(Calendar.YEAR)
        if (yearStr.isNotEmpty()) {
            year = yearStr.toInt()
            if (year < 100) year += 2000
        }
        cal?.set(year, month, day)
        cleanedText = cleanedText.replace(match.value, "")
        return Pair(cleanedText.trim(), cal)
    }

    // 2. 13 de octubre de 2026
    val dateTextRegex = Regex("(?i)\\b(\\d{1,2})\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)(?:\\s+de\\s+(\\d{2,4}))?\\b")
    dateTextRegex.find(cleanedText)?.let { match ->
        cal = Calendar.getInstance()
        val day = match.groupValues[1].toInt()
        val month = getMonthNumber(match.groupValues[2])
        var yearStr = match.groupValues[3]
        var year = now.get(Calendar.YEAR)
        if (yearStr.isNotEmpty()) {
            year = yearStr.toInt()
            if (year < 100) year += 2000
        }
        cal?.set(year, month, day)
        cleanedText = cleanedText.replace(match.value, "")
        return Pair(cleanedText.trim(), cal)
    }

    // 3. mañana
    val dateTomorrowRegex = Regex("(?i)\\b(?:para\\s+)?mañana\\b")
    dateTomorrowRegex.find(cleanedText)?.let { match ->
        cal = Calendar.getInstance()
        cal?.add(Calendar.DAY_OF_YEAR, 1)
        cleanedText = cleanedText.replace(match.value, "")
        return Pair(cleanedText.trim(), cal)
    }

    // 4. dias de la semana
    val dateWeekdayRegex = Regex("(?i)\\b(?:para el|próximo|proximo|el|para|este)\\s+(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo)\\b")
    dateWeekdayRegex.find(cleanedText)?.let { match ->
        cal = Calendar.getInstance()
        val targetDay = getDayOfWeek(match.groupValues[1])
        var currentDay = cal!!.get(Calendar.DAY_OF_WEEK)
        var daysToAdd = targetDay - currentDay
        if (daysToAdd <= 0) daysToAdd += 7 // Always next week if same or past day
        cal?.add(Calendar.DAY_OF_YEAR, daysToAdd)
        cleanedText = cleanedText.replace(match.value, "")
        return Pair(cleanedText.trim(), cal)
    }

    // 5. en X dias
    val dateInXDaysRegex = Regex("(?i)\\ben\\s+(\\d+)\\s+días?\\b")
    dateInXDaysRegex.find(cleanedText)?.let { match ->
        cal = Calendar.getInstance()
        val days = match.groupValues[1].toInt()
        cal?.add(Calendar.DAY_OF_YEAR, days)
        cleanedText = cleanedText.replace(match.value, "")
        return Pair(cleanedText.trim(), cal)
    }

    return Pair(cleanedText, null)
}

fun extractTime(text: String): Pair<String, Pair<Int, Int>?> {
    var cleanedText = text
    var timePair: Pair<Int, Int>? = null

    // 1. en X horas
    val timeInXHoursRegex = Regex("(?i)\\ben\\s+(\\d+)\\s+horas?\\b")
    timeInXHoursRegex.find(cleanedText)?.let { match ->
        val now = Calendar.getInstance()
        val hours = match.groupValues[1].toInt()
        now.add(Calendar.HOUR_OF_DAY, hours)
        timePair = Pair(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        cleanedText = cleanedText.replace(match.value, "")
        return Pair(cleanedText.trim(), timePair)
    }

    // 2. 14:30 or 2:30 pm
    val timeColonRegex = Regex("(?i)\\b(?:a las\\s+)?(\\d{1,2}):(\\d{2})(?:\\s*(am|pm|a\\.m\\.|p\\.m\\.|hrs|horas))?\\b")
    timeColonRegex.find(cleanedText)?.let { match ->
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val ampm = match.groupValues[3].lowercase().replace(".", "")
        if (ampm == "pm" && hour < 12) hour += 12
        if (ampm == "am" && hour == 12) hour = 0
        timePair = Pair(hour, minute)
        cleanedText = cleanedText.replace(match.value, "")
        return Pair(cleanedText.trim(), timePair)
    }

    // 3. 3 pm
    val timeLiteralRegex = Regex("(?i)\\b(?:a las\\s+)?(\\d{1,2})\\s*(am|pm|a\\.m\\.|p\\.m\\.)\\b")
    timeLiteralRegex.find(cleanedText)?.let { match ->
        var hour = match.groupValues[1].toInt()
        val ampm = match.groupValues[2].lowercase().replace(".", "")
        if (ampm == "pm" && hour < 12) hour += 12
        if (ampm == "am" && hour == 12) hour = 0
        timePair = Pair(hour, 0)
        cleanedText = cleanedText.replace(match.value, "")
        return Pair(cleanedText.trim(), timePair)
    }

    return Pair(cleanedText, null)
}

fun test(input: String) {
    println("INPUT: $input")
    val (text1, date) = extractDate(input)
    val (text2, time) = extractTime(text1)
    
    val cal = Calendar.getInstance()
    var hasTime = false
    if (date != null) {
        cal.timeInMillis = date.timeInMillis
    }
    if (time != null) {
        cal.set(Calendar.HOUR_OF_DAY, time.first)
        cal.set(Calendar.MINUTE, time.second)
        hasTime = true
    } else {
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
    }
    
    println("Date: ${if (date != null) cal.time else "NULL"}")
    println("Time: ${if (time != null) "${time.first}:${time.second}" else "NULL"}")
    println("Cleaned Text: '$text2'\n")
}

fun main() {
    test("Comprar pan para el próximo martes a las 3 pm")
    test("Reunión 13 de Octubre de 2026 14:30")
    test("Llamar a Juan en 5 días")
    test("Enviar reporte mañana a las 10 a.m.")
    test("Cita con el doctor 12/04/24")
    test("Dormir en 2 horas")
}
