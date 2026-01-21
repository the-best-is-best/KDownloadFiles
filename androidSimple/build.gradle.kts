import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
}

android {
    namespace = "org.company.app.androidApp"
    compileSdk = 36

    packaging {
        resources {
            pickFirsts += "**/*.cvr"
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile =
                file("/Users/michelleraouf/Desktop/kmm/kotlin-openid/simple/androidSimple/key")
            storePassword = "key-pass"
            keyPassword = "key-pass"
            keyAlias = "key0"
        }
    }

    defaultConfig {
        minSdk = 24
        targetSdk = 36

        applicationId = "org.company.app.androidApp"
        versionCode = 1
        versionName = "1.0.0"
        multiDexEnabled = true

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}



kotlin {

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":sharedUI"))
    implementation(libs.androidx.activityCompose)
}
