import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 签名凭据从本地 lumen-signing.properties 读取（不进源码、不进版本库）
val signingProps = Properties().apply {
    val f = rootProject.file("lumen-signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingProp(key: String): String? = signingProps.getProperty(key)
    ?: System.getenv(key.uppercase().replace('.', '_'))

android {
    namespace = "com.lumen.gallery"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lumen.gallery"
        minSdk = 26
        targetSdk = 35
        versionCode = 61
        versionName = "61.0.0.1"
    }

    signingConfigs {
        create("release") {
            val store = signingProp("lumen.store.file")?.let { rootProject.file(it) }
            val spass = signingProp("lumen.store.password")
            val kalias = signingProp("lumen.key.alias")
            val kpass = signingProp("lumen.key.password")
            if (store != null && store.exists() && spass != null && kalias != null && kpass != null) {
                storeFile = store
                storePassword = spass
                keyAlias = kalias
                keyPassword = kpass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val cfg = signingConfigs.getByName("release")
            if (cfg.storeFile?.exists() == true) {
                signingConfig = cfg
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.exifinterface)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}
