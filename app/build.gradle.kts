import com.android.build.api.dsl.Packaging


// In app/build.gradle.kts, direkt unter dem `plugins` Block

configurations.all {
    resolutionStrategy {
        dependencySubstitution {
            substitute(module("com.intellij:annotations")).using(module("org.jetbrains:annotations:23.0.0"))
        }
    }
}


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.sl5.aura"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.sl5.aura"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    // Exclude duplicate files from LanguageTool to prevent build errors
    packaging {
        resources {
            // Diese drei Zeilen lösen alle Duplikat-Probleme.
            excludes.add("META-INF/**")
            excludes.add("com/sun/xml/bind/**")
            excludes.add("com/sun/istack/**")
        }
        jniLibs.pickFirsts.add("**/libvosk.so")
        jniLibs.pickFirsts.add("**/libvosk_jni.so")
        pickFirst("com/sun/jna/**")
        pickFirst("javax/activation/**")
        pickFirst("org/intellij/lang/annotations/**")
        pickFirst("org/jetbrains/annotations/**")
    }
}

dependencies {
    // Android & Compose Dependencies )
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // App Logic Dependencies
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.vosk.android)
    implementation(libs.languagetool.core) {
        exclude(group = "net.java.dev.jna", module = "jna")
        exclude(group = "com.sun.xml.bind")
        exclude(group = "com.sun.istack")
        exclude(group = "org.glassfish.jaxb")
        exclude(group = "javax.activation")
    }
    implementation(libs.languagetool.german)
    implementation(libs.xerces.impl)

    // Test Dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

