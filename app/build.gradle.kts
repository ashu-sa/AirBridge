plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vibebridge.fitbit"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vibebridge.fitbit"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "1.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
            // Public releases must be signed with a private release key, never the debug key.
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
            pickFirsts.add("**/libgg.so")
            pickFirsts.add("**/libxp.so")
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    val composeBom = platform("androidx.compose:compose-bom:2024.02.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Golden Gate & BitGatt Dependencies
    implementation("com.github.fitbit:bitgatt:v0.9.11")
    implementation("io.reactivex.rxjava2:rxjava:2.2.12")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("com.jakewharton.timber:timber:4.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
