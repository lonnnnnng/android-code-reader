import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lonnnnnng.codereader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lonnnnnng.codereader"
        minSdk = 24
        targetSdk = 36
        versionCode = 29
        versionName = "0.1.28"

        // 真机与 Apple Silicon 模拟器均为 ARM，剔除 x86/x86_64 的 Oniguruma so 以减小体积。 @author long
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }

        // 自定义 runner：修复 Android 16 测试 APK 类加载器隔离后 Compose 用例的 coroutines-test ServiceLoader 检查失败。 @author long
        testInstrumentationRunner = "com.lonnnnnng.codereader.ServiceLoaderBridgeRunner"
    }

    signingConfigs {
        create("release") {
            // 签名信息走用户级 gradle.properties（codeReaderReleaseStoreFile/StorePassword/KeyAlias/KeyPassword），
            // 仓库里不落任何密钥；未配置时保持无签名，克隆者可自行提供。 @author long
            val storeFile = project.findProperty("codeReaderReleaseStoreFile") as? String
            if (storeFile != null) {
                this.storeFile = rootProject.file(storeFile)
                storePassword = project.property("codeReaderReleaseStorePassword") as String
                keyAlias = project.property("codeReaderReleaseKeyAlias") as String
                keyPassword = project.property("codeReaderReleaseKeyPassword") as String
            }
        }
    }

    buildTypes {
        debug {
            // 调试包与正式包并存，UI 验收无需卸载正式包或清空用户的最近项目与显示偏好。 @author long
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (project.hasProperty("codeReaderReleaseStoreFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // 界面文案为中文硬编码，仅保留中英文语言资源，剔除依赖库里的其余 80 余种翻译。 @author long
        localeFilters += setOf("zh", "en")
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                // JGit 传递引入 commons-codec，但 Beider-Morse 语音匹配的数据表完全用不到。 @author long
                "org/apache/commons/codec/language/**",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation(platform("io.github.rosemoe:editor-bom:0.24.6"))
    implementation("io.github.rosemoe:editor")
    implementation("io.github.rosemoe:language-textmate")
    implementation("io.github.rosemoe:oniguruma-native")

    implementation("org.eclipse.jgit:org.eclipse.jgit:7.7.0.202606012155-r")
    implementation("org.slf4j:slf4j-nop:2.0.18")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
