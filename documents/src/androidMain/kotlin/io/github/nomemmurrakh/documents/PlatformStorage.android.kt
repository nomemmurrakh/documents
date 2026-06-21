package io.github.nomemmurrakh.documents

import com.tencent.mmkv.MMKV

internal actual fun platformStorage(name: String, multiProcess: Boolean): Storage {
    val mode = if (multiProcess) MMKV.MULTI_PROCESS_MODE else MMKV.SINGLE_PROCESS_MODE
    val mmkv = requireNotNull(MMKV.mmkvWithID(name, mode)) {
        "MMKV.mmkvWithID returned null for '$name'; ensure MMKV.initialize(context) was called"
    }
    return MmkvStorage(mmkv)
}
