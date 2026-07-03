package com.nomemmurrakh.documents

import com.tencent.mmkv.MMKV

internal actual fun platformStorage(name: String): Storage {
    val mmkv = requireNotNull(MMKV.mmkvWithID(name, MMKV.SINGLE_PROCESS_MODE)) {
        "MMKV.mmkvWithID returned null for '$name'; ensure MMKV.initialize(context) was called"
    }
    return MmkvStorage(mmkv)
}
