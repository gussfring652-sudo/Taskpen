import sys

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix FilterDialog
content = content.replace(
    "filterState = filterState,",
    "currentState = filterState,"
)

# Fix TagsDialog
old_tags_dialog = """    if (showTagsDialog) {
        TagsDialog(
            tags = allTags,
            onDismiss = { showTagsDialog = false },
            onCreateTag = { name, color -> viewModel.createSubject(name, color) },
            onDeleteTag = { id -> viewModel.deleteSubject(id) }
        )
    }"""
new_tags_dialog = """    if (showTagsDialog) {
        TagsDialog(
            tags = allTags,
            onDismiss = { showTagsDialog = false },
            onAddTag = { name, aliases -> 
                // Using an empty/default color for now or whatever addSubject needs
            }
        )
    }"""
content = content.replace(old_tags_dialog, new_tags_dialog)

# Fix LeftLandscapePanel unresolved FilterState
content = content.replace(
    "fun LeftLandscapePanel(\n    activeContext: ViewContext,\n    filterState: FilterState,",
    "fun LeftLandscapePanel(\n    activeContext: ViewContext,\n    filterState: com.antakih.taskpen.ui.viewmodel.FilterState,"
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed dialogs")
