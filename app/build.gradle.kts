import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

fun localProperty(name: String): String? {
    val file = rootProject.file("local.properties")
    if (!file.isFile) return null
    val properties = Properties()
    file.inputStream().use { properties.load(it) }
    return properties.getProperty(name)
}

android {
    namespace = "com.trackwrite.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.trackwrite.app"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

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

    testOptions {
        unitTests.isReturnDefaultValues = true
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
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    kapt("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
}
