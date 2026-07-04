package com.nomemmurrakh.documents

import android.content.Context
import androidx.startup.Initializer
import com.tencent.mmkv.MMKV

// Android init happens via androidx.startup (DocumentsInitializer below), not here.
@Suppress("EmptyFunctionBlock")
internal actual fun ensureInitialized() {
}

/**
 * Bootstraps MMKV at process start so consumers never call `MMKV.initialize` themselves.
 *
 * This is invoked by the `androidx.startup` framework via the merged manifest, not by user code.
 */
public class DocumentsInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        MMKV.initialize(context.applicationContext)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
