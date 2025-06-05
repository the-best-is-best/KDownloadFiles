plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.library)

}

kotlin {
    jvmToolchain(17)

    sourceSets.commonMain.dependencies {
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material)
    }

    sourceSets.androidMain.dependencies {
        implementation(libs.androidx.activityCompose)
    }

    androidTarget()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "CalfPermissions"
            isStatic = true

        }
    }

}

android {
    namespace = "com.u52ndsolution.syl_app.calf_permissions"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }
}

