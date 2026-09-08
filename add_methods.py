file_path = 'app/src/main/java/com/antakih/taskpen/ui/viewmodel/TaskViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

old = """    fun toggleTaskImportance(taskId: String, isImportant: Boolean) {
        viewModelScope.launch {
            taskDao.updateTaskImportance(taskId, isImportant)
        }
    }"""
new = """    fun toggleTaskImportance(taskId: String, isImportant: Boolean) {
        viewModelScope.launch {
            taskDao.updateTaskImportance(taskId, isImportant)
        }
    }

    fun uncompleteTask(taskId: String) {
        viewModelScope.launch {
            taskDao.unmarkTaskAsCompleted(taskId)
        }
    }

    fun moveToTrash(taskId: String) {
        viewModelScope.launch {
            taskDao.moveToTrash(taskId)
        }
    }

    fun restoreTask(taskId: String) {
        viewModelScope.launch {
            taskDao.restoreFromTrash(taskId)
        }
    }

    fun permanentlyDeleteTask(taskId: String) {
        viewModelScope.launch {
            taskDao.permanentlyDeleteTask(taskId)
        }
    }"""

content = content.replace(old, new)
with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Added methods")
