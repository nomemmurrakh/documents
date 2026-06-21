package io.github.nomemmurrakh.documents

import cocoapods.MMKV.MMKV
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.create
import platform.Foundation.timeIntervalSince1970
import platform.posix.memcpy
import kotlin.test.Test
import kotlin.time.TimeSource

@Serializable
private data class Profile(
    val id: Long,
    val name: String,
    val email: String,
    val age: Int,
    val active: Boolean,
)

private val sample = Profile(42L, "Ada Lovelace", "ada@example.com", 36, true)

private const val WARMUP = 2_000
private const val MEASURE = 20_000

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalSerializationApi::class)
class DocumentsBenchmark {

    private val cbor = Cbor { ignoreUnknownKeys = true }
    private val profileSerializer = serializer<Profile>()

    private fun store(): Documents {
        ensureInitialized()
        return Documents.create("bench-${NSDate().timeIntervalSince1970}")
    }

    private fun rawMmkv(): MMKV {
        ensureInitialized()
        return requireNotNull(MMKV.mmkvWithID("bench-raw-${NSDate().timeIntervalSince1970}"))
    }

    private fun encode(value: Profile): ByteArray = cbor.encodeToByteArray(profileSerializer, value)

    private fun report(name: String, block: () -> Unit) {
        repeat(WARMUP) { block() }
        val samples = LongArray(MEASURE)
        repeat(MEASURE) { i ->
            val mark = TimeSource.Monotonic.markNow()
            block()
            samples[i] = mark.elapsedNow().inWholeNanoseconds
        }
        samples.sort()
        val median = samples[MEASURE / 2]
        val p95 = samples[(MEASURE * 95) / 100]
        println("BENCH $name median=${median}ns p95=${p95}ns iterations=$MEASURE")
    }

    @Test
    fun documentsSetReplace() {
        val doc = store().document<Profile>("profile")
        report("documents.set(REPLACE)") { doc.set(sample) }
    }

    @Test
    fun rawMmkvSet() {
        val mmkv = rawMmkv()
        report("rawMmkv.set") {
            mmkv.setData(encode(sample).toNSData(), forKey = "profile")
        }
    }

    @Test
    fun documentsGet() {
        val doc = store().document<Profile>("profile")
        doc.set(sample)
        report("documents.get") { doc.get() }
    }

    @Test
    fun rawMmkvGet() {
        val mmkv = rawMmkv()
        mmkv.setData(encode(sample).toNSData(), forKey = "profile")
        report("rawMmkv.get") {
            val bytes = requireNotNull(mmkv.getDataForKey("profile")).toByteArray()
            cbor.decodeFromByteArray(profileSerializer, bytes)
        }
    }

    @Test
    fun documentsSetUpdateSingleField() {
        val doc = store().document<Profile>("profile")
        doc.set(sample)
        report("documents.set(UPDATE)") { doc.set(MergeStrategy.UPDATE) { copy(age = age + 1) } }
    }

    @Test
    fun documentsDelete() {
        val doc = store().document<Profile>("profile")
        report("documents.delete") {
            doc.set(sample)
            doc.delete()
        }
    }

    @Test
    fun documentsFieldDelegateWrite() {
        var age by store().document<Profile>("profile").field(Profile::age, default = 0)
        report("documents.field write") { age += 1 }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length)
        }
    }
    return bytes
}
