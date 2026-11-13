plugins {
    alias(libs.plugins.android.application)
}

import java.util.Properties

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

val allAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
// Release ships ARM only: the head unit is ARM, and x86_64 added ~9 MB of
// native libraries that can never be loaded on the car.
val releaseAbis = listOf("arm64-v8a", "armeabi-v7a")

android {
    namespace = "io.github.jqssun.airplay"
    compileSdk = 37
    ndkVersion = "27.0.12077973"

    if (localProps.containsKey("storeFile")) {
        signingConfigs {
            create("release") {
                storeFile = file(localProps.getProperty("storeFile"))
                storePassword = localProps.getProperty("storePassword")
                keyAlias = localProps.getProperty("keyAlias")
                keyPassword = localProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.rockjesus.icarplay"
        minSdk = 23
        targetSdk = 37
        versionCode = 37
        versionName = "0.7.2"

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        debug {
            ndk { abiFilters += allAbis }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            ndk { abiFilters += releaseAbis }
        }
        // debuggable build with HWASan (arm64) + UBSan in native code
        create("sanitize") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
            ndk {
                abiFilters.clear()
                abiFilters += "arm64-v8a"
            }
            externalNativeBuild {
                cmake { arguments += "-DSANITIZE=ON" }
            }
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        prefab = true
    }
}

tasks.register("applyUxplayPatches") {
    doLast {
        fun git(vararg args: String): String {
            val proc = ProcessBuilder("git", "-C", "$projectDir/src/main/cpp/third_party/UxPlay", *args)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            check(proc.waitFor() == 0) { "git ${args.joinToString(" ")} failed:\n$out" }
            return out
        }
        val patches = file("src/main/cpp/patches/UxPlay").listFiles { f -> f.extension == "patch" }!!.sorted()
        val touched = patches.flatMap { git("apply", "--numstat", it.path).trim().lines() }
            .map { it.substringAfterLast("\t") }.distinct()
        git("checkout", "--", *touched.toTypedArray())
        patches.forEach { git("apply", "--unidiff-zero", it.path) }
    }
}

tasks.register("patchOpensslMirror") {
    // The upstream openssl-cmake helper points at mirror.viaduck.org, which can
    // return a truncated tarball from some networks. Use the canonical source.
    doLast {
        val f = file("src/main/cpp/third_party/openssl-cmake/cmake/BuildOpenSSL.cmake")
        val old = "https://mirror.viaduck.org/openssl/openssl-\${OPENSSL_BUILD_VERSION}.tar.gz"
        val neu = "https://www.openssl.org/source/openssl-\${OPENSSL_BUILD_VERSION}.tar.gz"
        val text = f.readText()
        if (text.contains(old)) f.writeText(text.replace(old, neu))
    }
}

tasks.configureEach {
    if (name.startsWith("configureCMake")) {
        dependsOn("applyUxplayPatches", "patchOpensslMirror")
    }
}

dependencies {
    // 仅保留运行所需依赖；界面全部为原生 View，不使用 Compose / Hilt / DataStore。
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.core:core-ktx:1.16.0")

    // AirPlayService 仍基于 LifecycleService + 协程状态流。
    implementation(libs.androidx.lifecycle.service)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation(libs.kotlinx.coroutines)

    // 后台媒体通知需要 MediaSessionCompat。
    implementation(libs.androidx.media)

    // AirPlay 视频播放与本地转存（ExoPlayer / Transformer）。
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.transformer)

    // Android Auto AAP protobuf（生成代码已提交在 java/info/anodsplace/...）
    implementation("com.google.protobuf:protobuf-lite:3.0.1")

    // UxPlay 音频输出，CMake 通过 prefab 引入
    implementation(libs.oboe)
}
