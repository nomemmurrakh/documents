package com.nomemmurrakh.documents.sample.reactive

import com.nomemmurrakh.documents.Document
import com.nomemmurrakh.documents.Documents
import com.nomemmurrakh.documents.document
import com.nomemmurrakh.documents.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOTAL_BYTES = 2_000_000L
private const val CHUNK_COUNT = 20
private const val CHUNK_DELAY_MS = 150L

class DownloadRepository {
    val download: Document<DownloadState> = Documents.document("active-download")

    fun start(scope: CoroutineScope): Job = scope.launch {
        if (download.get() == null) {
            download.set(DownloadState(bytesDownloaded = 0, totalBytes = TOTAL_BYTES, isComplete = false))
        }
        val chunkSize = TOTAL_BYTES / CHUNK_COUNT
        var bytesSoFar = download.get()?.bytesDownloaded ?: 0
        while (bytesSoFar < TOTAL_BYTES) {
            delay(CHUNK_DELAY_MS)
            bytesSoFar = (bytesSoFar + chunkSize).coerceAtMost(TOTAL_BYTES)
            download.update(DownloadState::bytesDownloaded, bytesSoFar)
        }
        download.update { current -> current.copy(isComplete = true) }
    }

    fun reset() {
        download.delete()
    }
}
