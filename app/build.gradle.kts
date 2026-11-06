plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.codefield.snapledger"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.codefield.snapledger"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8：删掉用不到的代码和资源。各个库自己带了保留规则，漏掉的补在 proguard-rules.pro。
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
            }
            // 正式版只打包手机用的 arm64：ML Kit 的识别程序每种 CPU 各有一份，四种都带上 APK 有 58MB。
            ndk {
                abiFilters += "arm64-v8a"
            }
            // 课程作业不上架商店，用调试签名，生成的 APK 可以直接装到组员的手机上演示。
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// Room 把数据库结构导出到 app/schemas，以后升级数据库时用来写迁移。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    // 截图识别：模型打包在 App 里，不联网、不需要 Google Play 服务下载模型。
    implementation(libs.mlkit.text.korean)
    implementation(libs.mlkit.text.chinese)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    // 消费地图：OpenStreetMap，不需要密钥。
    implementation(libs.osmdroid.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
