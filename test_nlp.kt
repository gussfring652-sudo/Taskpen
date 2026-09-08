fun cleanDanglingWords(text: String): String {
    var clean = text.trim()
    val danglingRegex = "(?i)\\s+(de|en|para|el|la|los|las|a|un|una|del|al|sobre)$".toRegex()
    while (danglingRegex.containsMatchIn(clean)) {
        clean = clean.replace(danglingRegex, "").trim()
    }
    return clean.removeSuffix("-").removeSuffix("*").trim()
}

fun main() {
    var extractedTitle = "Comprar pan para #Casa"
    
    val explicitMatch = Regex("(#|\\[)([A-Za-z0-9ÁÉÍÓÚáéíóúÑñ]+)(\\])?").find(extractedTitle)
    if (explicitMatch != null) {
        extractedTitle = extractedTitle.replace(explicitMatch.value, "").trim()
    }
    
    println("After explicit: '$extractedTitle'")
    println("After cleaning: '${cleanDanglingWords(extractedTitle)}'")
}
main()
