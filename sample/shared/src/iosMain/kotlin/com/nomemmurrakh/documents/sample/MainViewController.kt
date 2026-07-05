package com.nomemmurrakh.documents.sample

import androidx.compose.ui.window.ComposeUIViewController

@Suppress("FunctionNaming") // factory-function-named-after-its-type is the standard CMP/Swift-interop convention
fun MainViewController() = ComposeUIViewController { App() }
