This is a Kotlin Multiplatform sample app targeting Android and iOS, demonstrating the
`Documents` library's use cases.

* [/iosApp](./iosApp/iosApp) contains the iOS app — the entry point that hosts the shared
  Compose Multiplatform UI. Add any SwiftUI-specific code here.

* [/shared](./shared/src) holds the code shared across both platforms:
  - [commonMain](./shared/src/commonMain/kotlin) is the shared UI and business logic for all
    5 use-case screens (settings, session/encryption, caches & drafts, reactive UI, shared KMP
    persistence).
  - [iosMain](./shared/src/iosMain/kotlin) has the iOS-specific entry point
    (`MainViewController`) that hosts the shared Compose UI.

* [/androidApp](./androidApp) is a thin Android application module wrapping `/shared`.

This sample is part of the root `documents` Gradle build (not a standalone project) — build and
run it from the repo root.

### Running the apps

- Android app: `./gradlew :sample:androidApp:assembleDebug` (or use the run configurations in
  Android Studio).
- iOS app: open [/iosApp](./iosApp) in Xcode and run it from there.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
