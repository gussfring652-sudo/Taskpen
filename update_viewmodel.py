import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/viewmodel/TaskViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix FilterState
content = re.sub(
    r'val selectedTagIds: Set<String> = emptySet\(\),\s*val showOnlyImportant: Boolean = false,\s*val searchQuery: String = "",\s*val selectedCategories: Set<String> = emptySet\(\),\s*val selectedTags: Set<String> = emptySet\(\)',
    r'val showOnlyImportant: Boolean = false,\n    val searchQuery: String = "",\n    val selectedCategories: Set<String> = emptySet(),\n    val selectedTags: Set<String> = emptySet()',
    content
)

# Add SettingsManager import
content = content.replace(
    'import com.antakih.taskpen.data.local.dao.TaskDao',
    'import com.antakih.taskpen.data.local.SettingsManager\nimport com.antakih.taskpen.data.local.dao.TaskDao'
)

# Inject SettingsManager
content = content.replace(
    'private val digitalInkHelper: DigitalInkHelper',
    'private val digitalInkHelper: DigitalInkHelper,\n    private val settingsManager: SettingsManager'
)

# Add exposed setting
content = content.replace(
    'val filterState: StateFlow<FilterState> = _filterState.asStateFlow()',
    'val filterState: StateFlow<FilterState> = _filterState.asStateFlow()\n\n    val isCaseSensitiveTags = settingsManager.isCaseSensitiveTags\n\n    fun setCaseSensitiveTags(value: Boolean) {\n        settingsManager.setCaseSensitiveTags(value)\n    }'
)

# Update processText call
content = content.replace(
    '''val result = parseHandwrittenTextUseCase(
                    linesWithX = lines,
                    activeCategoryId = currentCategory,
                    existingTags = existingTags
                )''',
    '''val result = parseHandwrittenTextUseCase(
                    linesWithX = lines,
                    activeCategoryId = currentCategory,
                    existingTags = existingTags,
                    isCaseSensitive = isCaseSensitiveTags.value
                )'''
)

# Update processInks call
content = content.replace(
    '''val result = parseHandwrittenTextUseCase(
                        linesWithX = recognizedLines,
                        activeCategoryId = currentCategory,
                        existingTags = existingTags
                    )''',
    '''val result = parseHandwrittenTextUseCase(
                        linesWithX = recognizedLines,
                        activeCategoryId = currentCategory,
                        existingTags = existingTags,
                        isCaseSensitive = isCaseSensitiveTags.value
                    )'''
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated TaskViewModel")
