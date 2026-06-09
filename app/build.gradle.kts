import java.util.Properties
import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun localProperty(name: String): String? {
    val file = rootProject.file("local.properties")
    if (!file.isFile) return null
    val properties = Properties()
    file.inputStream().use { properties.load(it) }
    return properties.getProperty(name)
}

fun gradleProperty(name: String, defaultValue: String): String =
    providers.gradleProperty(name).orElse(defaultValue).get()

fun gradleOrLocalProperty(name: String): String? =
    providers.gradleProperty(name).orNull ?: localProperty(name)

val releaseStoreFilePath = gradleOrLocalProperty("TRACKWRITE_RELEASE_STORE_FILE")
val releaseStorePassword = gradleOrLocalProperty("TRACKWRITE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = gradleOrLocalProperty("TRACKWRITE_RELEASE_KEY_ALIAS")
val releaseKeyPassword = gradleOrLocalProperty("TRACKWRITE_RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.trackwrite.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.trackwrite.app"
        minSdk = 31
        targetSdk = 34
        versionCode = gradleProperty("trackwrite.versionCode", "20").toInt()
        versionName = gradleProperty("trackwrite.versionName", "v2.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["amapApiKey"] = providers
            .gradleProperty("TRACKWRITE_AMAP_API_KEY")
            .orElse(localProperty("TRACKWRITE_AMAP_API_KEY") ?: "")
            .get()
        manifestPlaceholders["amapWebKey"] = providers
            .gradleProperty("TRACKWRITE_AMAP_WEB_KEY")
            .orElse(localProperty("TRACKWRITE_AMAP_WEB_KEY") ?: "")
            .get()
        manifestPlaceholders["amapSecurityJsCode"] = providers
            .gradleProperty("TRACKWRITE_AMAP_SECURITY_JS_CODE")
            .orElse(localProperty("TRACKWRITE_AMAP_SECURITY_JS_CODE") ?: "")
            .get()
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))

            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin.compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    kapt("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (!hasReleaseSigningConfig) {
            throw GradleException(
                """
                Release signing is not configured. Add these values to local.properties
                or pass them as Gradle properties:

                TRACKWRITE_RELEASE_STORE_FILE=/absolute/path/to/trackwrite-release.jks
                TRACKWRITE_RELEASE_STORE_PASSWORD=...
                TRACKWRITE_RELEASE_KEY_ALIAS=...
                TRACKWRITE_RELEASE_KEY_PASSWORD=...
                """.trimIndent()
            )
        }
    }
}
