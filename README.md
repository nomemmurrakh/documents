# Documents

A document-oriented, typed, reactive Kotlin Multiplatform storage library backed by MMKV.

## Benchmarks

Documents decomposes a value into one `{doc}::{field}` key per field and runs a CBOR
encode/decode per field. These microbenchmarks measure that overhead against using raw MMKV
directly — encoding the same 5-field value once with CBOR and calling MMKV's byte API.

The numbers are captured on-device (Documents vs raw MMKV is not a JVM-host benchmark — MMKV is
mmap-backed and needs a real device/simulator). They are device- and OS-specific and are not run
in CI. See [ADR-0014](docs/0014-on-device-benchmarks.md).

Sample type:

```kotlin
@Serializable
data class Profile(
    val id: Long,
    val name: String,
    val email: String,
    val age: Int,
    val active: Boolean,
)
```

### Android

An instrumented test using the same `kotlin.time.TimeSource` warmup + measured-iterations harness
as iOS (median / p95), run with a device attached. Run only the benchmark class so the JVM-host
flow tests are not pulled onto the device:

```
./gradlew :documents:connectedAndroidDeviceTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.nomemmurrakh.documents.DocumentsBenchmark
```

Results print to logcat under the `BENCH` tag (`adb logcat -d | grep BENCH`).

| Operation               | Documents | Raw MMKV |
| ----------------------- | --------- | -------- |
| `set` (REPLACE, 5 field)| 56.3 µs   | 12.7 µs  |
| `get`                   | 60.0 µs   | 17.9 µs  |
| `set` (UPDATE, 1 field) | 122.4 µs  | —        |
| `delete` (incl. set)    | 90.5 µs   | —        |
| field delegate write    | 22.7 µs   | —        |

_Device: Samsung Galaxy A53 (SM-A536E) · Android 16 · debug build · median of 20k iterations. The
absolute numbers are inflated by the debuggable test build; read them relatively. The `delete` row
measures set + delete._

> **Note:** these numbers were measured with the previous JSON encoding and predate the switch to
> CBOR ([ADR-0015](docs/0015-cbor-internal-format.md)). They will be re-run on device and updated.

### iOS

A `kotlin.time.TimeSource`-based timing harness (warmup + measured iterations; median / p95),
run on the simulator or a device:

```
./gradlew :documents:iosSimulatorArm64Test
```

Results print to test output as `BENCH <name> median=<n>ns p95=<n>ns`.

| Operation               | Documents | Raw MMKV |
| ----------------------- | --------- | -------- |
| `set` (REPLACE, 5 field)| 23.9 µs   | 10.0 µs  |
| `get`                   | 21.3 µs   | 7.5 µs   |
| `set` (UPDATE, 1 field) | 46.9 µs   | —        |
| `delete` (incl. set)    | 25.3 µs   | —        |
| field delegate write    | 5.7 µs    | —        |

_Device: iPhone 17 Pro simulator · iOS 26.1 · median of 20k iterations. The `delete` row measures
set + delete. Absolute timings are not comparable to the Android table (different build flavor and
hardware); compare Documents vs raw MMKV within each platform._

> **Note:** these numbers were measured with the previous JSON encoding and predate the switch to
> CBOR ([ADR-0015](docs/0015-cbor-internal-format.md)). They will be re-run on device and updated.
