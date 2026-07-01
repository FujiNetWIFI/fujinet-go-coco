plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties

val keystoreProperties = Properties().apply {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun readFujiNetRuntimeVersion(): String {
    val versionHeader = rootProject.file("tools/fujinet/work/fujinet-firmware/include/version.h")
    if (!versionHeader.isFile) {
        return "fujinet-runtime-v1"
    }
    val match = Regex("""#define\s+FN_VERSION_FULL\s+"([^"]+)"""")
        .find(versionHeader.readText())
    return match?.groupValues?.get(1) ?: "fujinet-runtime-v1"
}

val fujiNetRuntimeVersion = readFujiNetRuntimeVersion()

// XRoar core version is recorded by tools/xroar/build-xroar-core.sh in a
// .source-info stamp once the core has been staged. Until then, report the
// configured source label from the staging script, or "Unknown".
fun readXroarVersion(): String {
    val stamp = rootProject.file("app/src/main/cpp-generated/xroar/.source-info")
    if (stamp.isFile) {
        val text = stamp.readText()
        val version = Regex("""^source_version=(.+)$""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.trim()
        val commit = Regex("""^source_commit=(.+)$""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.trim()?.take(8)
        return when {
            !version.isNullOrBlank() && !commit.isNullOrBlank() -> "$version ($commit)"
            !version.isNullOrBlank() -> version
            !commit.isNullOrBlank() -> commit
            else -> "Unknown"
        }
    }
    return "Unknown"
}

val xroarVersion = readXroarVersion()

// Stages the XRoar core source tree (and the CoCo ROM assets) from the local
// checkout into app/src/main/cpp-generated/xroar (see tools/xroar/build-xroar-core.sh).
val prepareXroarCore by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Stages the XRoar core source tree and CoCo ROM assets from the local checkout."
    workingDir = rootProject.projectDir
    commandLine("bash", rootProject.file("tools/xroar/build-xroar-core.sh").absolutePath)
    inputs.file(rootProject.file("tools/xroar/build-xroar-core.sh"))
    outputs.dir(project.file("src/main/cpp-generated/xroar"))
    outputs.dir(project.file("src/main/assets-generated/xroar"))
}

tasks.named("preBuild").configure {
    dependsOn(prepareXroarCore)
}

tasks.matching { task ->
    task.name.startsWith("configureCMake") || task.name.startsWith("buildCMake")
}.configureEach {
    dependsOn(prepareXroarCore)
}

// Optional dev override: -PcocoAbi=arm64-v8a builds a single ABI for fast
// iteration. Unset => all four packaged ABIs (release/default).
val cocoAbi: String? = (project.findProperty("cocoAbi") as String?)?.takeIf { it.isNotBlank() }
val fujinetAbiArgs: List<String> =
    if (cocoAbi != null) listOf("--abi", cocoAbi) else listOf("--all-abis")

// Builds the FujiNet CoCo Android runtime (libfujinet.so per ABI + runtime
// assets) from the local fujinet-pc-coco checkout. Up-to-date checked on the
// script/support inputs and the generated output dirs, so it only re-runs when
// the build inputs change.
val prepareFujiNetRuntime by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Builds the FujiNet CoCo Android runtime for the packaged ABIs."
    workingDir = rootProject.projectDir
    commandLine(listOf("bash", rootProject.file("tools/fujinet/build-fujinet.sh").absolutePath) + fujinetAbiArgs)
    inputs.file(rootProject.file("tools/fujinet/build-fujinet.sh"))
    inputs.dir(rootProject.file("tools/fujinet/support"))
    outputs.dir(project.file("src/main/assets-generated/fujinet"))
    outputs.dir(project.file("src/main/jniLibs-generated"))
}

tasks.configureEach {
    if (name.contains("Release") || name == "preBuild") {
        dependsOn(prepareFujiNetRuntime)
    }
}

tasks.matching { task ->
    task.name.startsWith("merge") && (
        task.name.endsWith("Assets")
            || task.name.endsWith("JniLibFolders")
            || task.name.endsWith("NativeLibs")
    )
}.configureEach {
    dependsOn(prepareFujiNetRuntime)
}

android {
    namespace = "online.fujinet.go.coco"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "online.fujinet.go.coco"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.8.1"
        buildConfigField("String", "XROAR_VERSION", "\"${xroarVersion}\"")
        buildConfigField("String", "FUJINET_RUNTIME_VERSION", "\"${fujiNetRuntimeVersion}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
                cFlags += listOf("-std=gnu11")
            }
        }
        ndk {
            if (cocoAbi != null) {
                abiFilters += cocoAbi
            } else {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
        }
    }

    buildTypes {
        debug {
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
        buildConfig = true
        compose = true
    }
    sourceSets {
        getByName("main") {
            // assets-generated/{fujinet,xroar} and jniLibs-generated/<abi>/libfujinet.so
            // are produced by tools/fujinet/build-fujinet.sh and tools/xroar/build-xroar-core.sh.
            assets.srcDir("src/main/assets-generated")
            jniLibs.srcDir("src/main/jniLibs-generated")
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    testImplementation(libs.junit)
    testImplementation(libs.androidx.lifecycle.runtime.testing)
    testImplementation(libs.androidx.lifecycle.viewmodel.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
