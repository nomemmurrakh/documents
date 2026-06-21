package io.github.nomemmurrakh.documents

/**
 * Controls the baseline a builder-style [Document.set] starts from.
 */
public enum class MergeStrategy {

    /**
     * Start from the type's default values, ignoring anything already persisted. The builder's
     * result fully replaces the document.
     */
    REPLACE,

    /**
     * Start from the currently persisted value, or the type's defaults when the document is
     * absent. The builder edits that baseline, leaving untouched fields intact.
     */
    UPDATE,
}
