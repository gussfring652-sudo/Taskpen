import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix the baseTasks.filter logic
old_block = """        baseTasks.filter { task ->
            val matchesSearch = if (filterState.searchQuery.isNotBlank()) {
                task.title.contains(filterState.searchQuery, ignoreCase = true) ||
                task.description?.contains(filterState.searchQuery, ignoreCase = true) == true
            } else true
            
            val matchesImportant = if (filterState.showOnlyImportant) task.isImportant else true
            val matchesCategory = if (filterState.selectedCategories.isNotEmpty()) filterState.selectedCategories.contains(task.categoryId) else true
            val matchesTags = if (filterState.selectedTags.isNotEmpty()) filterState.selectedTags.contains(task.subcategoryId) else true
            
            matchesSearch && matchesImportant && matchesCategory && matchesTags
        }
        
        if (filterState.sortByDueDate) {
            filtered.sortedWith(compareBy(nullsLast()) { it.dueDate })
        } else {
            filtered.sortedByDescending { it.createdAt }
        }
    }"""

new_block = """        val filtered = baseTasks.filter { task ->
            val matchesSearch = if (filterState.searchQuery.isNotBlank()) {
                task.title.contains(filterState.searchQuery, ignoreCase = true) ||
                task.description?.contains(filterState.searchQuery, ignoreCase = true) == true
            } else true
            
            val matchesImportant = if (filterState.showOnlyImportant) task.isImportant else true
            val matchesCategory = if (filterState.selectedCategories.isNotEmpty()) filterState.selectedCategories.contains(task.categoryId) else true
            val matchesTags = if (filterState.selectedTags.isNotEmpty()) filterState.selectedTags.contains(task.subcategoryId) else true
            
            matchesSearch && matchesImportant && matchesCategory && matchesTags
        }
        
        if (filterState.sortByDueDate) {
            filtered.sortedWith(compareBy(nullsLast()) { it.dueDate })
        } else {
            filtered.sortedByDescending { it.createdAt }
        }
    }"""

content = content.replace(old_block, new_block)

# Fix FilterState initialization in FilterDialog:
content = content.replace(
    """onApply(
                            currentState.copy(
                                sortByDueDate = sortByDueDate,
                                selectedTags = selectedTagIds,
                                showOnlyImportant = showOnlyImportant
                            )
                        )""",
    """onApply(
                            currentState.copy(
                                sortByDueDate = sortByDueDate,
                                selectedTags = selectedTagIds,
                                showOnlyImportant = showOnlyImportant
                            )
                        )"""
)

# And fix line 805 No parameter with name 'selectedTagIds' found.
# Ah, I replaced the FilterState instantiation but there was another one!
# Let me look for selectedTagIds in FilterState creation
content = re.sub(r'selectedTagIds = selectedTagIds', r'selectedTags = selectedTagIds', content)
content = content.replace('selectedTags = selectedTags', 'selectedTags = selectedTagIds') # in case of double replacement

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed DashboardScreen compilation issues")
