package com.nomemmurrakh.documents.sample.queue

import com.nomemmurrakh.documents.Documents
import com.nomemmurrakh.documents.document
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PendingRequestQueue {
    private val doc = Documents.document<QueueState>("pending-requests")

    fun enqueue(item: OfflineQueueItem) {
        doc.update { current -> current.copy(items = current.items + item) }
    }

    fun clear() {
        doc.delete()
    }

    fun observe(): Flow<List<OfflineQueueItem>> =
        doc.flow().map { it?.items ?: emptyList() }
}
