package io.github.nomemmurrakh.documents

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tencent.mmkv.MMKV
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
import org.junit.runner.RunWith
import kotlin.test.BeforeTest
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

@OptIn(ExperimentalSerializationApi::class)
@RunWith(AndroidJUnit4::class)
class DocumentsBenchmark {

    private val cbor = Cbor { ignoreUnknownKeys = true }
    private val profileSerializer = serializer<Profile>()

    @BeforeTest
    fun setUp() {
        MMKV.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun store(): Collection = Documents.collection("bench-${System.nanoTime()}")

    private fun rawMmkv(): MMKV = MMKV.mmkvWithID("bench-raw-${System.nanoTime()}")

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
        Log.i("BENCH", "$name median=${median}ns p95=${p95}ns iterations=$MEASURE")
    }

    @Test
    fun documentsSetReplace() {
        val doc = store().document<Profile>("profile")
        report("documents.set(REPLACE)") { doc.set(sample) }
    }

    @Test
    fun rawMmkvSet() {
        val mmkv = rawMmkv()
        report("rawMmkv.set") { mmkv.encode("profile", encode(sample)) }
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
        mmkv.encode("profile", encode(sample))
        report("rawMmkv.get") {
            cbor.decodeFromByteArray(profileSerializer, mmkv.decodeBytes("profile")!!)
        }
    }

    @Test
    fun documentsUpdateSingleField() {
        val doc = store().document<Profile>("profile")
        doc.set(sample)
        report("documents.update") { doc.update { current -> current.copy(age = current.age + 1) } }
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
