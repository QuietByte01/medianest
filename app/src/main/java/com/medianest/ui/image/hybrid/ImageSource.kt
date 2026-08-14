package com.medianest.ui.image.hybrid

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Encapsulates an image source to unify input stream reading across URIs, Files, Byte Arrays, and Resources.
 */
sealed class ImageSource {
    abstract fun openInputStream(context: Context): InputStream?
    abstract val key: String

    data class FromUri(val uri: Uri) : ImageSource() {
        override val key: String get() = uri.toString()
        override fun openInputStream(context: Context): InputStream? {
            return try {
                when (uri.scheme) {
                    ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).takeIf { f -> f.exists() }?.inputStream() }
                    else -> context.contentResolver.openInputStream(uri)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    data class FromFile(val file: File) : ImageSource() {
        override val key: String get() = file.absolutePath
        override fun openInputStream(context: Context): InputStream? {
            return try {
                if (file.exists()) FileInputStream(file) else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    data class FromByteArray(val bytes: ByteArray, val id: String = bytes.contentHashCode().toString()) : ImageSource() {
        override val key: String get() = "bytes_$id"
        override fun openInputStream(context: Context): InputStream = ByteArrayInputStream(bytes)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FromByteArray) return false
            return id == other.id && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + id.hashCode()
            return result
        }
    }

    companion object {
        fun from(uri: Uri): ImageSource = FromUri(uri)
        fun from(file: File): ImageSource = FromFile(file)
        fun from(bytes: ByteArray, id: String = bytes.contentHashCode().toString()): ImageSource = FromByteArray(bytes, id)
    }
}
