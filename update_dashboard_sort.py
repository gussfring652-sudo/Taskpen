import re

file_path = 'app/src/main/java/com/antakih/taskpen/ui/screens/DashboardScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update displayedTasks computation
old_filter = '''            val matchesTags = if (filterState.selectedTags.isNotEmpty()) filterState.selectedTags.contains(task.subcategoryId) else true
            
            matchesSearch && matchesImportant && matchesCategory && matchesTags
        }
    }'''

new_filter = '''            val matchesTags = if (filterState.selectedTags.isNotEmpty()) filterState.selectedTags.contains(task.subcategoryId) else true
            
            matchesSearch && matchesImportant && matchesCategory && matchesTags
        }
        
        if (filterState.sortByDueDate) {
            filtered.sortedWith(compareBy(nullsLast()) { it.dueDate })
        } else {
            filtered.sortedByDescending { it.createdAt }
        }
    }'''
content = content.replace(old_filter, new_filter)

# 2. Update LazyColumn
old_lazy = '''                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                        items(displayedTasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                tags = allTags,
                                isTrashContext = activeContext is ViewContext.Trash,
                                isCompletedContext = activeContext is ViewContext.Completed,
                                onComplete = { viewModel.completeTask(task.id) },
                                onUncomplete = { viewModel.uncompleteTask(task.id) },
                                onToggleImportant = { viewModel.toggleTaskImportance(task.id, !task.isImportant) },
                                onMoveToTrash = { viewModel.moveToTrash(task.id) },
                                onRestore = { viewModel.restoreTask(task.id) },
                                onDeletePermanently = { viewModel.permanentlyDeleteTask(task.id) },
                                onClick = { onTaskClick(task) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }'''

new_lazy = '''                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                        if (filterState.sortByDueDate) {
                            val grouped = displayedTasks.groupBy { task ->
                                if (task.dueDate == null) "Sin Fecha"
                                else if (android.text.format.DateUtils.isToday(task.dueDate)) "Hoy"
                                else if (android.text.format.DateUtils.isToday(task.dueDate - 86400000)) "Mañana"
                                else android.text.format.DateFormat.format("dd MMM yyyy", task.dueDate).toString()
                            }
                            grouped.forEach { (header, tasksInGroup) ->
                                item {
                                    Text(
                                        text = header,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(tasksInGroup, key = { it.id }) { task ->
                                    TaskCard(
                                        task = task,
                                        tags = allTags,
                                        isTrashContext = activeContext is ViewContext.Trash,
                                        isCompletedContext = activeContext is ViewContext.Completed,
                                        onComplete = { viewModel.completeTask(task.id) },
                                        onUncomplete = { viewModel.uncompleteTask(task.id) },
                                        onToggleImportant = { viewModel.toggleTaskImportance(task.id, !task.isImportant) },
                                        onMoveToTrash = { viewModel.moveToTrash(task.id) },
                                        onRestore = { viewModel.restoreTask(task.id) },
                                        onDeletePermanently = { viewModel.permanentlyDeleteTask(task.id) },
                                        onClick = { onTaskClick(task) }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        } else {
                            items(displayedTasks, key = { it.id }) { task ->
                                TaskCard(
                                    task = task,
                                    tags = allTags,
                                    isTrashContext = activeContext is ViewContext.Trash,
                                    isCompletedContext = activeContext is ViewContext.Completed,
                                    onComplete = { viewModel.completeTask(task.id) },
                                    onUncomplete = { viewModel.uncompleteTask(task.id) },
                                    onToggleImportant = { viewModel.toggleTaskImportance(task.id, !task.isImportant) },
                                    onMoveToTrash = { viewModel.moveToTrash(task.id) },
                                    onRestore = { viewModel.restoreTask(task.id) },
                                    onDeletePermanently = { viewModel.permanentlyDeleteTask(task.id) },
                                    onClick = { onTaskClick(task) }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }'''
content = content.replace(old_lazy, new_lazy)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated DashboardScreen sorting and separators")
