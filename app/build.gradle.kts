plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val blindboxApplicationId = "com.jenn.blindboxworld"

android {
    androidResources.noCompress += listOf("glb", "spz", "wav")
    namespace = "com.jenn.blindboxworld"
    compileSdk = 35

    defaultConfig {
        applicationId = blindboxApplicationId
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters.add("arm64-v8a") }
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

androidComponents.onVariants { variant ->
    // Side-loaded verification copies can be opened by component on the test device.
    val verificationCopy = variant.applicationId.map { id ->
        id.startsWith("$blindboxApplicationId.") && id.endsWith("check")
    }
    variant.manifestPlaceholders.put("launcherCategory", verificationCopy.map { testCopy ->
        if (testCopy) "$blindboxApplicationId.category.VERIFICATION" else "android.intent.category.LAUNCHER"
    })
    variant.manifestPlaceholders.put("excludeFromRecents", verificationCopy.map { it.toString() })
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.spatial.bom))
    implementation(libs.spatial.core)
    implementation(libs.spatial.ui.platform)
    implementation(libs.spatial.ui.foundation)
    implementation(libs.spatial.ui.design)
    implementation(libs.spatial.ui.sense)
    implementation(libs.spatial.ui.tracking)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    testImplementation("com.google.code.gson:gson:2.11.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling.preview)
}

configurations.all {
    resolutionStrategy {
        exclude("androidx.compose.ui", "ui")
        exclude("androidx.compose.ui", "ui-graphics")
        exclude("androidx.compose.ui", "ui-text")
        exclude("androidx.compose.foundation", "foundation")
    }
}
