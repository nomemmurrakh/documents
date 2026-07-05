package com.nomemmurrakh.documents.sample.reactive

import kotlinx.serialization.Serializable

@Serializable
data class DownloadState(
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val isComplete: Boolean = false,
)
