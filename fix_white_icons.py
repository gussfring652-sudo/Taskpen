import sys

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Filter icon
content = content.replace(
    'Icon(Icons.Default.FilterList, contentDescription = "Filtrar")',
    'Icon(Icons.Default.FilterList, contentDescription = "Filtrar", tint = Color.White)'
)
# Star icon
content = content.replace(
    'contentDescription = "Importante"\n                )',
    'contentDescription = "Importante",\n                    tint = Color.White\n                )'
)
# Tags icon
content = content.replace(
    'Icon(Icons.Default.Label, contentDescription = "Etiquetas")',
    'Icon(Icons.Default.Label, contentDescription = "Etiquetas", tint = Color.White)'
)
# Settings icon
content = content.replace(
    'Icon(Icons.Default.Settings, contentDescription = "Ajustes")',
    'Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Color.White)'
)
# Categorías text
content = content.replace(
    'Text("Categorías", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))',
    'Text("Categorías", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.weight(1f))'
)

# Also let's make sure the background is dark so white actually shows up.
# The user might be in light mode and getting white on white if we just use surface.
# They previously had Color(0xFF280B45) for left panel and Color(0xFF5D5270) for right panel.
# Let's restore a dark background for the left panel just in case, but using Material dark color?
# Actually, the user just said "change them to white because they don't show up". I will just change them to white.

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated icons and text to white")
