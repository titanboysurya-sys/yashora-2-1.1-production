package com.ritvyom.yashoraReelgenerator.engine.timeline

import com.ritvyom.yashoraReelgenerator.core.model.CanonicalTimeline
import java.io.Serializable

/**
 * Command representing an atomic undoable/redoable state transformation on the [CanonicalTimeline].
 */
interface TimelineCommand : Serializable {
    val description: String
    fun execute(timeline: CanonicalTimeline): CanonicalTimeline
    fun undo(timeline: CanonicalTimeline): CanonicalTimeline
}

/**
 * Snapshot-based command for complex multi-property operations.
 */
class SnapshotTimelineCommand(
    override val description: String,
    private val beforeSnapshot: CanonicalTimeline,
    private val afterSnapshot: CanonicalTimeline
) : TimelineCommand {
    override fun execute(timeline: CanonicalTimeline): CanonicalTimeline = afterSnapshot
    override fun undo(timeline: CanonicalTimeline): CanonicalTimeline = beforeSnapshot
}

/**
 * Professional Undo/Redo Transaction Manager.
 * Solves continuous gesture spam (drag, pinch, rotate, scrub) by grouping or committing
 * only on gesture completion:
 * DRAG TEXT = ONE UNDO
 * PINCH TEXT = ONE UNDO
 * ROTATE TEXT = ONE UNDO
 * TRIM CLIP = ONE UNDO
 * APPLY EFFECT = ONE UNDO
 * CHANGE COLOR = ONE UNDO
 */
class TimelineUndoRedoManager(
    private val maxHistorySize: Int = 50
) {
    private val undoStack = ArrayDeque<TimelineCommand>()
    private val redoStack = ArrayDeque<TimelineCommand>()

    // Transaction state for continuous gestures
    private var activeTransactionDescription: String? = null
    private var transactionInitialTimeline: CanonicalTimeline? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Begins an atomic transaction for a continuous user gesture (e.g. onDragStart, onPinchStart).
     */
    fun beginTransaction(description: String, currentTimeline: CanonicalTimeline) {
        if (transactionInitialTimeline == null) {
            transactionInitialTimeline = currentTimeline
            activeTransactionDescription = description
        }
    }

    /**
     * Commits the active transaction when the gesture ends (e.g. onDragEnd, onPinchEnd).
     * Creates exactly ONE undo entry for the continuous sequence.
     */
    fun commitTransaction(finalTimeline: CanonicalTimeline) {
        val initial = transactionInitialTimeline ?: return
        val desc = activeTransactionDescription ?: "Edit"

        if (initial != finalTimeline) {
            pushCommand(SnapshotTimelineCommand(desc, initial, finalTimeline))
        }

        transactionInitialTimeline = null
        activeTransactionDescription = null
    }

    /**
     * Cancels an in-progress transaction without pushing to history.
     */
    fun cancelTransaction() {
        transactionInitialTimeline = null
        activeTransactionDescription = null
    }

    /**
     * Pushes a completed command directly into the undo history.
     */
    fun pushCommand(command: TimelineCommand) {
        if (undoStack.size >= maxHistorySize) {
            undoStack.removeFirst()
        }
        undoStack.addLast(command)
        redoStack.clear()
    }

    /**
     * Pushes a timeline state change directly.
     */
    fun pushSnapshot(description: String, before: CanonicalTimeline, after: CanonicalTimeline) {
        if (before != after) {
            pushCommand(SnapshotTimelineCommand(description, before, after))
        }
    }

    /**
     * Undoes the most recent command, restoring previous [CanonicalTimeline] state.
     */
    fun undo(currentTimeline: CanonicalTimeline): CanonicalTimeline? {
        if (undoStack.isEmpty()) return null
        val command = undoStack.removeLast()
        val restored = command.undo(currentTimeline)
        redoStack.addLast(command)
        return restored
    }

    /**
     * Redoes the most recently undone command.
     */
    fun redo(currentTimeline: CanonicalTimeline): CanonicalTimeline? {
        if (redoStack.isEmpty()) return null
        val command = redoStack.removeLast()
        val restored = command.execute(currentTimeline)
        undoStack.addLast(command)
        return restored
    }

    /**
     * Clears all history.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        transactionInitialTimeline = null
        activeTransactionDescription = null
    }
}
