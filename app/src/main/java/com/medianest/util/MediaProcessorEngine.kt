package com.medianest.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MediaProcessorEngine — Delegated to Media Studio Add-On App (`com.medianest.studio`).
 */
object MediaProcessorEngine {

    sealed class ProcessingState {
        object Idle : ProcessingState()
        object Cancelled : ProcessingState()
    }

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    fun cancelCurrent() {
        _processingState.value = ProcessingState.Cancelled
    }

    fun resetState() {
        _processingState.value = ProcessingState.Idle
    }
}
