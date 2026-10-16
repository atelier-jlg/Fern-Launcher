// Fern Clavier : le clavier de l'univers Fern (même dépôt, même clé, même thème).
// Pas de permission Internet : ce que tu tapes ne peut jamais sortir du téléphone.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Numéro de version propre à cette appli (gradle.properties : keyboardVersionName / keyboardVersionCode).
val appVersionName = providers.gradleProperty("keyboardVersionName").get()
val appVersionCode = providers.gradleProperty("keyboardVersionCode").get().toInt()

// Même clé que Fern Launcher (secrets de la CI) : indispensable pour lire son thème.
val keystorePath: String? = System.getenv("FERN_KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("FERN_KEYSTORE_PASSWORD")

android {
    namespace = "com.atelierjlg.fern.keyboard"
    compileSdk = 35

    defaultConfig {
        // Identifiant Android : ne doit plus jamais changer.
        applicationId = "com.atelierjlg.fern.keyboard"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        // Dictée Whisper : code natif compilé pour les téléphones 64 bits (le Fairphone 6).
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake { arguments += listOf("-DANDROID_STL=c++_static") }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
            // Whisper doit être compilé optimisé, sinon la dictée est 10 fois plus lente.
            externalNativeBuild { cmake { arguments += listOf("-DCMAKE_BUILD_TYPE=Release") } }
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

// Nom du fichier produit : fern-clavier-v0.1.0-release.apk
base {
    val suffix = if (keystorePath != null) "" else "-NON-SIGNE"
    archivesName.set("fern-clavier-v$appVersionName$suffix")
}

dependencies {
    implementation(project(":theme"))
    // Suggestions des gestionnaires de mots de passe (Proton Pass…) dans la barre du clavier.
    implementation(libs.androidx.autofill)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
