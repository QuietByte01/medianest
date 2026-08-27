package com.medianest.util

import android.content.Context
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.VideoFrameDecoder
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class SemaphoreVideoFrameDecoder(
    private val result: SourceResult,
    private val options: Options,
    private val context: Context
) : Decoder {

    private val delegate = VideoFrameDecoder(result.source, options)

    override suspend fun decode(): DecodeResult? {
        return semaphore.withPermit {
            delegate.decode()
        }
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val delegateFactory = VideoFrameDecoder.Factory()
            if (delegateFactory.create(result, options, imageLoader) != null) {
                return SemaphoreVideoFrameDecoder(result, options, options.context)
            }
            return null
        }
    }

    companion object {
        private val semaphore = Semaphore(1)
    }
}
