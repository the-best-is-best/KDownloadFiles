import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.kotlin.multiplatform.library)
//    alias(libs.plugins.hotReload)
}

kotlin {
    jvmToolchain(17)

//    jvm()

//    js {
//        browser()
//        binaries.executable()
//    }
//
//    wasmJs {
//        browser()
//        binaries.executable()
//    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material3)
            implementation(libs.components.resources)
            implementation(libs.ui.tooling.preview)

            implementation(libs.the.best.is1.best.kpermissions.core)
            implementation(libs.kpermissions.notification)

            implementation(project(":kDownloadFile"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ui.test)
        }


        androidMain.dependencies {
            implementation(libs.ui.tooling)
            implementation(libs.androidx.activityCompose)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }

        jsMain.dependencies {
            implementation(libs.html.core)
        }

    }


    android {
        namespace = "org.company.app"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = 21

        withHostTest {
            isIncludeAndroidResources = true
        }


    }

    sourceSets.getByName("androidHostTest").dependencies {
        implementation(libs.junit)
        implementation(libs.robolectric)
        implementation(libs.androidx.compose.ui.test.manifest)
    }
}


compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Demo App"
            packageVersion = "1.0.0"

            linux {
                iconFile.set(project.file("desktopAppIcons/LinuxIcon.png"))
            }
            windows {
                iconFile.set(project.file("desktopAppIcons/WindowsIcon.ico"))
            }
            macOS {
                iconFile.set(project.file("desktopAppIcons/MacosIcon.icns"))
                bundleID = "org.company.app.desktopApp"
            }
        }
    }
}

