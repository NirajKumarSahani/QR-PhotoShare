package com.qrphotoshare.api

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

class UriRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri
) : RequestBody() {

    override fun contentType(): MediaType? {
        val type = contentResolver.getType(uri) ?: "application/octet-stream"
        return type.toMediaTypeOrNull()
    }

    override fun contentLength(): Long {
        return -1L
    }

    override fun writeTo(sink: BufferedSink) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            sink.writeAll(inputStream.source())
        }
    }
}
