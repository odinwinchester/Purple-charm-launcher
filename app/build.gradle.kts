
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.purplecharm.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.purplecharm.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.environmentVariable("PURPLE_VERSION_CODE").orElse("9").get().toInt()
        versionName = "0.7.2"
    }

    val signingPath = providers.environmentVariable("PURPLE_KEYSTORE_PATH").orNull
    if (!signingPath.isNullOrBlank()) {
        signingConfigs.create("stable") {
            storeFile = file(signingPath)
            storePassword = providers.environmentVariable("PURPLE_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("PURPLE_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("PURPLE_KEY_PASSWORD").get()
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("stable")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "1g"
            it.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
        }
    }

    // Compilação única Purple Charm Crystal Class v0.7.0.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("org.json:json:20240303")
}
