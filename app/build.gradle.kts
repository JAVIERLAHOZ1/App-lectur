plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.javierlahoz.lectur"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.javierlahoz.lectur"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // Clave de firma fija. Sin esto cada compilacion firmaria con una clave
    // distinta y Android se negaria a actualizar la app encima de la anterior,
    // obligando a desinstalar (y perder la biblioteca) en cada version.
    signingConfigs {
        create("lectur") {
            storeFile = file("lectur.keystore")
            storePassword = "lecturlectur"
            keyAlias = "lectur"
            keyPassword = "lecturlectur"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lectur")
        }
        release {
            signingConfig = signingConfigs.getByName("lectur")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // PdfBox arrastra avisos de licencia duplicados.
            excludes += "/META-INF/{DEPENDENCIES,LICENSE,LICENSE.txt,NOTICE,NOTICE.txt}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Lee la estructura del PDF: indice (marcadores) y texto con posiciones,
    // que es lo que necesita el diccionario. PdfRenderer solo dibuja imagenes.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
