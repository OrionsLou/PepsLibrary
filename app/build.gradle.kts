import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Signing config lives outside the repo (and outside OneDrive). Point PEPSLIBRARY_KEYSTORE_PROPERTIES
// at a different file to override; the default is ~/.pepslibrary/keystore.properties. See README.md.
val keystorePropsFile = System.getenv("PEPSLIBRARY_KEYSTORE_PROPERTIES")
    ?.let { file(it) }
    ?: File(System.getProperty("user.home"), ".pepslibrary/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// The one place to change the version: bump appVersion and versionCode follows (major * 10000 + minor * 100 +
// patch, so 0.2.0 is 200). Android refuses to install a lower versionCode over a higher one, so this also guards
// against putting an older build on the phone by mistake. Bump it for each feature or step that reaches the phone.
val appVersion = "0.2.3"
fun versionCodeOf(name: String): Int {
    val parts = name.split(".").map { it.toIntOrNull() }
    require(parts.size == 3 && parts.all { it != null && it >= 0 } && parts[1]!! < 100 && parts[2]!! < 100) {
        "appVersion must be major.minor.patch with minor and patch under 100, not \"$name\""
    }
    return parts[0]!! * 10000 + parts[1]!! * 100 + parts[2]!!
}

android {
    namespace = "app.pepslibrary"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.pepslibrary"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 stays off until Readium is in; add keep rules then.
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required by the Readium toolkit.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG keeps browsing details (visited URLs, cookie names) out of release logs.
        buildConfig = true
    }
    lint {
        // Readium pulls in androidx.lifecycle 2.9, whose bundled lint check was built for a newer Kotlin analysis
        // API than this AGP's lint has, and crashes the release lint run ("Found class KaCallableMemberCall, but
        // interface was expected"). The check is about LiveData, which this app does not use. Remove this once AGP
        // is upgraded.
        disable += "NullSafeMutableLiveData"
    }
}

// Room writes each schema version here. Commit the files: migrations are checked against them.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.androidx.fragment.ktx)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
}
