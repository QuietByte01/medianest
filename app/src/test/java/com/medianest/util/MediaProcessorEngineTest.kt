package com.medianest.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests state management logic in MediaProcessorEngine.
 */
class MediaProcessorEngineTest {

    @Test
    fun `test state management and cancel`() {
        MediaProcessorEngine.resetState()
        assertEquals(MediaProcessorEngine.ProcessingState.Idle, MediaProcessorEngine.processingState.value)

        MediaProcessorEngine.cancelCurrent()
        assertEquals(MediaProcessorEngine.ProcessingState.Cancelled, MediaProcessorEngine.processingState.value)
    }
}

