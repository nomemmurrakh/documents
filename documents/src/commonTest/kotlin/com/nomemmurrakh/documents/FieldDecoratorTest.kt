package com.nomemmurrakh.documents

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

private class TaggingDecorator(private val tag: Byte) : FieldDecorator {
    override fun wrap(fieldName: String, bytes: ByteArray): ByteArray = bytes + tag

    override fun unwrap(fieldName: String, bytes: ByteArray): ByteArray {
        check(bytes.isNotEmpty() && bytes.last() == tag) {
            "Expected trailing tag $tag on field '$fieldName', found ${bytes.lastOrNull()}"
        }
        return bytes.copyOfRange(0, bytes.size - 1)
    }
}

class FieldDecoratorTest {

    @Test
    fun applyWrapWithNoDecoratorsReturnsBytesUnchanged() {
        val bytes = byteArrayOf(1, 2, 3)
        assertContentEquals(bytes, applyWrap(emptyList(), "field", bytes))
    }

    @Test
    fun applyUnwrapWithNoDecoratorsReturnsBytesUnchanged() {
        val bytes = byteArrayOf(1, 2, 3)
        assertContentEquals(bytes, applyUnwrap(emptyList(), "field", bytes))
    }

    @Test
    fun singleDecoratorRoundTrips() {
        val decorators = listOf(TaggingDecorator(tag = 9))
        val original = byteArrayOf(10, 20, 30)

        val wrapped = applyWrap(decorators, "field", original)
        assertContentEquals(byteArrayOf(10, 20, 30, 9), wrapped)

        val unwrapped = applyUnwrap(decorators, "field", wrapped)
        assertContentEquals(original, unwrapped)
    }

    @Test
    fun multipleDecoratorsWrapLeftToRightAndUnwrapRightToLeft() {
        val decorators = listOf(TaggingDecorator(tag = 1), TaggingDecorator(tag = 2))
        val original = byteArrayOf(42)

        val wrapped = applyWrap(decorators, "field", original)
        assertContentEquals(byteArrayOf(42, 1, 2), wrapped)

        val unwrapped = applyUnwrap(decorators, "field", wrapped)
        assertContentEquals(original, unwrapped)
    }

    @Test
    fun unwrapInWrapOrderFailsForNonCommutingDecorators() {
        val decorators = listOf(TaggingDecorator(tag = 1), TaggingDecorator(tag = 2))
        val wrapped = applyWrap(decorators, "field", byteArrayOf(42))

        assertFailsWith<IllegalStateException> {
            decorators.fold(wrapped) { acc, decorator -> decorator.unwrap("field", acc) }
        }
    }
}
