plugins {
    id("com.android.application")
    kotlin("android")
    // Kotlin 2.0에서 Compose 필수 플러그인
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.brightnesspulse"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.brightnesspulse"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures { compose = true }

    // Kotlin 2.0 + Compose 플러그인 사용 시 composeOptions 불필요
    kotlinOptions { jvmTarget = "17" }

    // 자바 소스가 있다면 17로 빌드
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.08.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.13.1")
}
