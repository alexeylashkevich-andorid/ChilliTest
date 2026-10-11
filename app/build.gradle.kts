import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localConfig = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val apiKey = (providers.environmentVariable("GIPHY_API_KEY").orNull
    ?: localConfig.getProperty("GIPHY_API_KEY"))
    ?.takeIf { it.isNotBlank() }
    ?: throw GradleException("Add GIPHY_API_KEY=your_api_key to local.properties or set the GIPHY_API_KEY environment variable.")
val apiBaseUrl = localConfig.getProperty("API_BASE_URL", "https://api.giphy.com/v1/")

android {
    namespace = "com.lashla.chillitest"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.lashla.chillitest"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.lashla.chillitest.HiltTestRunner"

        buildConfigField("String", "API_KEY", "\"${apiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:navigation"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)

    implementation(libs.coil.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    androidTestImplementation(project(":core:domain"))
    androidTestImplementation(libs.hilt.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
