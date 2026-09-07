import sys

file_path = 'app/src/main/java/com/antakih/taskpen/ui/viewmodel/TaskViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

old_filter = """data class FilterState(
    val sortByDueDate: Boolean = false,
    val selectedTagIds: Set<String> = emptySet(),
    val showOnlyImportant: Boolean = false
)"""

new_filter = """data class FilterState(
    val sortByDueDate: Boolean = false,
    val selectedTagIds: Set<String> = emptySet(),
    val showOnlyImportant: Boolean = false,
    val searchQuery: String = "",
    val selectedCategories: Set<String> = emptySet(),
    val selectedTags: Set<String> = emptySet()
)"""

content = content.replace(old_filter, new_filter)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated FilterState")
