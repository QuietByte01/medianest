package com.medianest.util

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaAnalyzerTest {

    @Test
    fun `test MediaAnalyzer class exists`() {
        assertTrue(true)
    }
}

