import sys

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix isLandscape logic
old_islandscape = "val isLandscape = configuration.screenWidthDp >= 600"
new_islandscape = "val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 840"
content = content.replace(old_islandscape, new_islandscape)

# Add statusBarsPadding import
if "import androidx.compose.foundation.layout.statusBarsPadding" not in content:
    content = content.replace("import androidx.compose.foundation.layout.padding", "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.statusBarsPadding")

# Fix baseTasks completed filtering
old_base_tasks = """        val baseTasks = when (activeContext) {
            is ViewContext.Trash -> deletedTasks
            is ViewContext.General -> activeTasks
            is ViewContext.Category -> activeTasks.filter { it.categoryId == (activeContext as ViewContext.Category).categoryId }
            is ViewContext.Completed -> activeTasks.filter { it.isCompleted }
            is ViewContext.Important -> activeTasks.filter { it.isImportant && !it.isCompleted }
            is ViewContext.Today -> activeTasks.filter { 
                it.dueDate != null && android.text.format.DateUtils.isToday(it.dueDate) 
            }
            is ViewContext.Tomorrow -> activeTasks.filter {
                it.dueDate != null && android.text.format.DateUtils.isToday(it.dueDate - 86400000)
            }
            is ViewContext.Postponed -> activeTasks.filter {
                it.dueDate != null && it.dueDate < System.currentTimeMillis() && !android.text.format.DateUtils.isToday(it.dueDate)
            }
        }"""
new_base_tasks = """        val baseTasks = when (activeContext) {
            is ViewContext.Trash -> deletedTasks
            is ViewContext.General -> activeTasks.filter { !it.isCompleted }
            is ViewContext.Category -> activeTasks.filter { !it.isCompleted && it.categoryId == (activeContext as ViewContext.Category).categoryId }
            is ViewContext.Completed -> activeTasks.filter { it.isCompleted }
            is ViewContext.Important -> activeTasks.filter { it.isImportant && !it.isCompleted }
            is ViewContext.Today -> activeTasks.filter { 
                !it.isCompleted && it.dueDate != null && android.text.format.DateUtils.isToday(it.dueDate) 
            }
            is ViewContext.Tomorrow -> activeTasks.filter {
                !it.isCompleted && it.dueDate != null && android.text.format.DateUtils.isToday(it.dueDate - 86400000)
            }
            is ViewContext.Postponed -> activeTasks.filter {
                !it.isCompleted && it.dueDate != null && it.dueDate < System.currentTimeMillis() && !android.text.format.DateUtils.isToday(it.dueDate)
            }
        }"""
content = content.replace(old_base_tasks, new_base_tasks)

# Replace the LeftLandscapePanel completely and the landscape Row backgrounds
# We need to find the `LeftLandscapePanel` definition at the bottom and replace it.
left_panel_idx = content.find('@Composable\nfun LeftLandscapePanel(')
if left_panel_idx != -1:
    content = content[:left_panel_idx]

new_left_panel = """
@Composable
fun MenuButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(text)
    }
}

@Composable
fun LeftLandscapePanel(
    activeContext: ViewContext,
    filterState: com.antakih.taskpen.ui.viewmodel.FilterState,
    allCategories: List<CategoryEntity>,
    onContextSelected: (ViewContext) -> Unit,
    onSearchChanged: (String) -> Unit,
    onFilterClick: () -> Unit,
    onTagsClick: () -> Unit,
    onToggleImportantFilter: () -> Unit,
    onSettingsClick: () -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.statusBarsPadding().padding(16.dp)) {
        // Top Row: Search + Filter + Star
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = onSearchChanged,
                modifier = Modifier.weight(1f).height(50.dp),
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onFilterClick) {
                Icon(Icons.Default.FilterList, contentDescription = "Filtrar")
            }
            IconButton(onClick = onToggleImportantFilter) {
                Icon(
                    imageVector = if (filterState.showOnlyImportant) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Importante"
                )
            }
            IconButton(onClick = onTagsClick) {
                Icon(Icons.Default.Label, contentDescription = "Etiquetas")
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // 3x2 Grid for buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuButton("General", activeContext is ViewContext.General, { onContextSelected(ViewContext.General) }, Modifier.weight(1f))
            MenuButton("Hoy", activeContext is ViewContext.Today, { onContextSelected(ViewContext.Today) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuButton("Mañana", activeContext is ViewContext.Tomorrow, { onContextSelected(ViewContext.Tomorrow) }, Modifier.weight(1f))
            MenuButton("Pospuestas", activeContext is ViewContext.Postponed, { onContextSelected(ViewContext.Postponed) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuButton("Completadas", activeContext is ViewContext.Completed, { onContextSelected(ViewContext.Completed) }, Modifier.weight(1f))
            MenuButton("Papelera", activeContext is ViewContext.Trash, { onContextSelected(ViewContext.Trash) }, Modifier.weight(1f))
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Categories Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Categorías", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Ajustes")
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Categories list
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(allCategories, key = { it.id }) { cat ->
                val isSelected = (activeContext as? ViewContext.Category)?.categoryId == cat.id
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onCategoryClick(cat.id) },
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.name, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
"""

content += new_left_panel

# Replace Row backgrounds in the landscape switch
content = content.replace(
    'Row(modifier = Modifier.fillMaxSize().background(Color(0xFF280B45))) {',
    'Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {'
)
content = content.replace(
    'Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF5D5270))) {',
    'Box(modifier = Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.background)) {'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixes applied.")
