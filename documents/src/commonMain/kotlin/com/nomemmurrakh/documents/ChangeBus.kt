package com.nomemmurrakh.documents

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class ChangeBus {

    private val changes = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val keys: SharedFlow<String> = changes.asSharedFlow()

    fun emit(key: String) {
        changes.tryEmit(key)
    }
}
