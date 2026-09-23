import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val amapApiKey: String = run {
    val local = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { local.load(it) }
    }
    listOf(local.getProperty("AMAP_API_KEY"), findProperty("AMAP_API_KEY") as? String)
        .firstOrNull { !it.isNullOrBlank() }
        ?: "YOUR_AMAP_KEY"
}

android {
    namespace = "com.cjz18.walkrun"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cjz18.walkrun"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey

        // 高德 10.x+ 只带 armeabi-v7a / arm64-v8a，没有 x86。
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES,LICENSE,LICENSE.txt,NOTICE,NOTICE.txt}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.amap)

    testImplementation(libs.junit)
}
