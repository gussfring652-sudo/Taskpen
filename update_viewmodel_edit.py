import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/viewmodel/TaskViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# I will add updateTaskDetails right before getSubtasks
new_func = """    fun updateTaskDetails(id: String, title: String, description: String?, dueDate: Long?, categoryId: String?, subcategoryId: String?) {
        viewModelScope.launch {
            import kotlinx.coroutines.flow.first
            val allTasks = taskDao.getAllActiveTasks().first()
            val task = allTasks.find { it.id == id }
            if (task != null) {
                taskDao.insertTask(task.copy(title = title, description = description, dueDate = dueDate, categoryId = categoryId, subcategoryId = subcategoryId))
            }
        }
    }

    fun getSubtasks(parentTaskId: String) = taskDao.getSubtasks(parentTaskId)"""
    
content = content.replace("    fun getSubtasks(parentTaskId: String) = taskDao.getSubtasks(parentTaskId)", new_func)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated TaskViewModel with edit")
