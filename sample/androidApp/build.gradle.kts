import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":sample:shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.multiplatform.ui.tooling.preview)
    debugImplementation(libs.compose.multiplatform.ui.tooling)
}

android {
    namespace = "com.nomemmurrakh.documents.sample"
    //noinspection GradleDependency
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.nomemmurrakh.documents.sample"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        //noinspection OldTargetApi
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
