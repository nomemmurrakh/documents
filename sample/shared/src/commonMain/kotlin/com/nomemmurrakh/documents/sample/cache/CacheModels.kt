package com.nomemmurrakh.documents.sample.cache

import kotlinx.serialization.Serializable

@Serializable
data class SyncState(
    val lastSyncedAtEpochMs: Long = 0,
    val lastSyncedEtag: String = "",
)

@Serializable
data class DraftPost(
    val title: String = "",
    val body: String = "",
)
