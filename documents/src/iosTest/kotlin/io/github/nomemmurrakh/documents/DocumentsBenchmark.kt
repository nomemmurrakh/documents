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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
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

    private fun store(): Collection {
        ensureInitialized()
        return Documents.collection("bench-${NSDate().timeIntervalSince1970}")
    }

    private fun rawMmkv(): MMKV {
        ensureInitialized()
        return requireNotNull(MMKV.mmkvWithID("bench-raw-${NSDate().timeIntervalSince1970}"))
    }

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
    fun rawMmkvSetReplace() {
        val mmkv = rawMmkv()
        report("rawMmkv.set(REPLACE, 5-field)") {
            mmkv.rawFieldPrefixScanAndClear("profile::")
            mmkv.rawWriteAllFields(sample, cbor)
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
        mmkv.rawWriteAllFields(sample, cbor)
        report("rawMmkv.get(5-field)") {
            check(mmkv.rawFieldKeyExists("profile::"))
            mmkv.rawReadAllFields(cbor)
        }
    }

    @Test
    fun documentsUpdateSingleField() {
        val doc = store().document<Profile>("profile")
        doc.set(sample)
        report("documents.update") { doc.update { current -> current.copy(age = current.age + 1) } }
    }

    @Test
    fun rawMmkvSetUpdateSingleField() {
        val mmkv = rawMmkv()
        mmkv.rawWriteAllFields(sample, cbor)
        report("rawMmkv.set(update via full get+set, 5-field)") {
            check(mmkv.rawFieldKeyExists("profile::"))
            val current = mmkv.rawReadAllFields(cbor)
            val updated = current.copy(age = current.age + 1)
            mmkv.rawFieldPrefixScanAndClear("profile::")
            mmkv.rawWriteAllFields(updated, cbor)
        }
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
    fun rawMmkvDelete() {
        val mmkv = rawMmkv()
        report("rawMmkv.delete(5-field)") {
            mmkv.rawWriteAllFields(sample, cbor)
            mmkv.rawFieldPrefixScanAndClear("profile::")
        }
    }

    @Test
    fun documentsFieldDelegateWrite() {
        var age by store().document<Profile>("profile").field(Profile::age, default = 0)
        report("documents.field write") { age += 1 }
    }

    @Test
    fun rawMmkvFieldWrite() {
        val mmkv = rawMmkv()
        var age = 0
        report("rawMmkv.field write(1-field)") {
            age += 1
            mmkv.setData(cbor.encodeToByteArray(Int.serializer(), age).toNSData(), forKey = "profile::age")
        }
    }

    @Test
    fun documentsUpdateFieldDirect() {
        val doc = store().document<Profile>("profile")
        doc.set(sample)
        var age = 0
        report("documents.update(prop, value)") {
            age += 1
            doc.update(Profile::age, age)
        }
    }

    @Test
    fun rawMmkvUpdateFieldDirect() {
        val mmkv = rawMmkv()
        var age = 0
        report("rawMmkv.update(prop, value)(1-field)") {
            age += 1
            mmkv.setData(cbor.encodeToByteArray(Int.serializer(), age).toNSData(), forKey = "profile::age")
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun MMKV.rawFieldPrefixScanAndClear(prefix: String) {
    allKeys()
        .filterIsInstance<String>()
        .filter { it.startsWith(prefix) }
        .forEach { removeValueForKey(it) }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun MMKV.rawFieldKeyExists(prefix: String): Boolean =
    allKeys().filterIsInstance<String>().any { it.startsWith(prefix) }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalSerializationApi::class)
private fun MMKV.rawWriteAllFields(profile: Profile, cbor: Cbor) {
    setData(cbor.encodeToByteArray(Long.serializer(), profile.id).toNSData(), forKey = "profile::id")
    setData(cbor.encodeToByteArray(String.serializer(), profile.name).toNSData(), forKey = "profile::name")
    setData(cbor.encodeToByteArray(String.serializer(), profile.email).toNSData(), forKey = "profile::email")
    setData(cbor.encodeToByteArray(Int.serializer(), profile.age).toNSData(), forKey = "profile::age")
    setData(cbor.encodeToByteArray(Boolean.serializer(), profile.active).toNSData(), forKey = "profile::active")
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalSerializationApi::class)
private fun MMKV.rawReadAllFields(cbor: Cbor): Profile {
    val id = cbor.decodeFromByteArray(Long.serializer(), requireNotNull(getDataForKey("profile::id")).toByteArray())
    val name = cbor.decodeFromByteArray(String.serializer(), requireNotNull(getDataForKey("profile::name")).toByteArray())
    val email = cbor.decodeFromByteArray(String.serializer(), requireNotNull(getDataForKey("profile::email")).toByteArray())
    val age = cbor.decodeFromByteArray(Int.serializer(), requireNotNull(getDataForKey("profile::age")).toByteArray())
    val active = cbor.decodeFromByteArray(Boolean.serializer(), requireNotNull(getDataForKey("profile::active")).toByteArray())
    return Profile(id, name, email, age, active)
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
