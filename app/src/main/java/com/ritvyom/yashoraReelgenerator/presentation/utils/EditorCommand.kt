package com.ritvyom.yashoraReelgenerator.presentation.utils

import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Stack

/**
 * Command interface defining an executable, reversible action in the Video Editor.
 */
interface EditorCommand {
    val actionName: String
    fun execute()
    fun undo()
}

/**
 * State-based snapshot command recording before and after states of video timeline clips and settings.
 */
class SceneStateCommand(
    override val actionName: String,
    private val previousState: List<Scene>,
    private val nextState: List<Scene>,
    private val applyState: (List<Scene>) -> Unit
) : EditorCommand {

    override fun execute() {
        applyState(nextState.map { it.copy() })
    }

    override fun undo() {
        applyState(previousState.map { it.copy() })
    }
}

/**
 * State-based Command Pattern Manager for the Video Editor.
 * Maintains undo/redo stacks, memory limits, and reactive state flows.
 */
class EditorCommandManager(
    private val maxHistorySize: Int = 50
) {
    private val undoStack = Stack<List<Scene>>()
    private val redoStack = Stack<List<Scene>>()

    val canUndo = MutableStateFlow(false)
    val canRedo = MutableStateFlow(false)
    val lastActionName = MutableStateFlow<String?>("Ready")

    fun recordState(scenes: List<Scene>, actionName: String = "Edit Timeline") {
        undoStack.push(scenes.map { it.copy() })
        if (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
        lastActionName.value = actionName
        updateState()
    }

    fun undo(currentScenes: List<Scene>, onApply: (List<Scene>) -> Unit): Boolean {
        if (undoStack.isNotEmpty()) {
            redoStack.push(currentScenes.map { it.copy() })
            val previous = undoStack.pop()
            onApply(previous.map { it.copy() })
            lastActionName.value = "Undid change"
            updateState()
            return true
        }
        return false
    }

    fun redo(currentScenes: List<Scene>, onApply: (List<Scene>) -> Unit): Boolean {
        if (redoStack.isNotEmpty()) {
            undoStack.push(currentScenes.map { it.copy() })
            val next = redoStack.pop()
            onApply(next.map { it.copy() })
            lastActionName.value = "Redid change"
            updateState()
            return true
        }
        return false
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastActionName.value = null
        updateState()
    }

    private fun updateState() {
        canUndo.value = undoStack.isNotEmpty()
        canRedo.value = redoStack.isNotEmpty()
    }
}
