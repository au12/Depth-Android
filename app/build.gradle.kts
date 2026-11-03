import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing. Values come from (in order): environment variables (CI), then a local,
// git-ignored keystore.properties file. With neither present, release builds fall back to
// the debug key so `assembleRelease` still works on a fresh checkout.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)

// Version comes from the release tag in CI (v1.2.3 → "1.2.3"); versionCode is derived from
// it (1.2.3 → 10203) so every tagged build upgrades cleanly over the previous one.
// Untagged builds (and local builds) use the default below.
val depthVersion: String = System.getenv("DEPTH_VERSION_NAME")?.takeIf { it.isNotBlank() }?.removePrefix("v") ?: "0.1.1"
val depthVersionCode: Int = depthVersion.split(".", "-").take(3).map { it.toIntOrNull() ?: 0 }
    .let { (it + listOf(0, 0, 0)).take(3) }.let { (a, b, c) -> a * 10000 + b * 100 + c }
    .coerceAtLeast(1)

val releaseStoreFile = signingValue("DEPTH_KEYSTORE_PATH", "storeFile")
val hasReleaseKey = releaseStoreFile != null && rootProject.file(releaseStoreFile).exists()

android {
    namespace = "com.depth.breathtrainer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.depth.breathtrainer"
        minSdk = 26
        targetSdk = 34
        versionName = depthVersion
        versionCode = depthVersionCode
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = signingValue("DEPTH_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("DEPTH_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("DEPTH_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("depth.screenshotDir", rootProject.file("docs/screenshots").absolutePath)
                it.maxHeapSize = "2g"
                it.jvmArgs("--add-opens", "java.base/java.io=ALL-UNNAMED")
                // Robolectric fetches its Android runtime jars itself; point it at Google's
                // Maven Central mirror, which doesn't rate-limit shared build hosts.
                it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Android Auto: Depth is a media app. Android Auto discovers it through the
    // MediaBrowserService + MediaSession this library provides — the same path every
    // sideloaded music player uses.
    implementation("androidx.media:media:1.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
}

// CI reads the version from here so a push to main can publish v<version> without a tag.
tasks.register("printVersion") { doLast { println(depthVersion) } }
