plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Le numéro de version vient de gradle.properties (un seul endroit à modifier).
val fernVersionName = providers.gradleProperty("fernVersionName").get()
val fernVersionCode = providers.gradleProperty("fernVersionCode").get().toInt()

// Clé de signature : fournie par la CI GitHub (secrets). Sans elle, on signe avec
// la clé de debug, ce qui donne un APK installable mais non « officiel ».
val keystorePath: String? = System.getenv("FERN_KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("FERN_KEYSTORE_PASSWORD")

android {
    namespace = "com.atelierjlg.fern"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.atelierjlg.fern"
        minSdk = 29
        targetSdk = 35
        versionCode = fernVersionCode
        versionName = fernVersionName
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = "fern"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            // Permet d'installer une version de test à côté de la vraie.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
}

// Nom du fichier produit : fern-launcher-v0.1.0-release.apk
base {
    archivesName.set("fern-launcher-v$fernVersionName")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
