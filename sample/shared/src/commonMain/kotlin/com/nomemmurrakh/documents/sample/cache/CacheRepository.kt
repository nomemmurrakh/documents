package com.nomemmurrakh.documents.sample.cache

import com.nomemmurrakh.documents.Document
import com.nomemmurrakh.documents.Documents
import com.nomemmurrakh.documents.document
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CacheRepository {
    private val cache = Documents.collection("cache")

    val syncState: Document<SyncState> = cache.document("sync-state")
    val draft: Document<DraftPost> = cache.document("compose-draft")

    @OptIn(ExperimentalTime::class)
    fun simulateSync() {
        syncState.update { current ->
            current.copy(
                lastSyncedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                lastSyncedEtag = "etag-${Random.nextInt(from = 100000, until = 999999)}",
            )
        }
    }

    fun clearCache() {
        syncState.delete()
        draft.delete()
    }
}
