import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("org.jetbrains.kotlin.native.cocoapods")
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.dokka)
}

group = "com.nomemmurrakh"
version = "0.1.0"

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }

    androidLibrary {
        namespace = "io.github.nomemmurrakh.documents"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        version = project.version.toString()
        summary = "A document-oriented, typed, reactive Kotlin Multiplatform storage library backed by MMKV."
        homepage = "https://github.com/nomemmurrakh/documents"
        ios.deploymentTarget = "13.0"
        pod("MMKV") {
            version = libs.versions.mmkv.get()
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.cbor)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            implementation(libs.mmkv)
            implementation(libs.androidx.startup)
        }

        getByName("androidDeviceTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.rules)
            implementation(libs.androidx.test.ext.junit)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dokka {
    moduleName.set("Documents")

    dokkaSourceSets.commonMain {
        sourceLink {
            localDirectory.set(rootDir)
            remoteUrl("https://github.com/nomemmurrakh/documents/blob/master")
            remoteLineSuffix.set("#L")
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(group.toString(), "documents", version.toString())

    pom {
        name = "Documents"
        description = "A document-oriented, typed, reactive Kotlin Multiplatform storage library backed by MMKV."
        inceptionYear = "2026"
        url = "https://github.com/nomemmurrakh/documents"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "nomemmurrakh"
                name = "Khuram"
                url = "https://github.com/nomemmurrakh"
            }
        }
        scm {
            url = "https://github.com/nomemmurrakh/documents"
            connection = "scm:git:git://github.com/nomemmurrakh/documents.git"
            developerConnection = "scm:git:ssh://git@github.com/nomemmurrakh/documents.git"
        }
    }
}
