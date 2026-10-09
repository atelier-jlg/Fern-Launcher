// Fern Contact : appli sœur de Fern Launcher (même dépôt, même clé, même thème).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Numéro de version propre à cette appli (gradle.properties : contactVersionName / contactVersionCode).
val appVersionName = providers.gradleProperty("contactVersionName").get()
val appVersionCode = providers.gradleProperty("contactVersionCode").get().toInt()

// Même clé que Fern Launcher (secrets de la CI) : indispensable pour lire son thème.
val keystorePath: String? = System.getenv("FERN_KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("FERN_KEYSTORE_PASSWORD")

android {
    namespace = "com.atelierjlg.fern.contact"
    compileSdk = 35

    defaultConfig {
        // Identifiant Android : ne doit plus jamais changer.
        applicationId = "com.atelierjlg.fern.contact"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
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

// Nom du fichier produit : fern-contact-v0.1.0-release.apk
base {
    val suffix = if (keystorePath != null) "" else "-NON-SIGNE"
    archivesName.set("fern-contact-v$appVersionName$suffix")
}

dependencies {
    implementation(project(":theme"))
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
