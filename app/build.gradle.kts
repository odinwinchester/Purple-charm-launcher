
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
        versionCode = 7
        versionName = "0.7.0"
    }

    // Apenas o APK debug desta branch recebe um pacote isolado.
    // A versão release mantém o identificador original e os seus dados.
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            resValue("string", "launcher_display_name", "Purple Charm TEST")
        }
        getByName("release") {
            resValue("string", "launcher_display_name", "Purple Charm Launcher")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
