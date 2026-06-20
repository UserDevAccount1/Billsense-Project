import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
}


// Load local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties") // Get it from the root project
if (localPropertiesFile.exists()) {
    FileInputStream(localPropertiesFile).use { fis ->
        localProperties.load(fis)
    }
} else {
    // Optional: Log a warning or throw an error
    logger.warn("local.properties file not found. MAPS_API_KEY might be missing.")
}

android {
    namespace = "com.app.billsense"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.app.billsense"
        minSdk = 24
        targetSdk = 35
        versionCode = 28
        versionName = "1.5.13"

        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${project.properties["FIREBASE_PROJECT_ID"]}\"")
        buildConfigField("String", "ABACUS_DEPLOYMENT_TOKEN", "\"${localProperties.getProperty("ABACUS_DEPLOYMENT_TOKEN", "")}\"")
        buildConfigField("String", "ABACUS_DEPLOYMENT_ID", "\"${localProperties.getProperty("ABACUS_DEPLOYMENT_ID", "")}\"")
        buildConfigField("String", "EMAIL_SENDER_ADDRESS", "\"${localProperties.getProperty("EMAIL_SENDER_ADDRESS", "")}\"")
        buildConfigField("String", "EMAIL_SENDER_PASSWORD", "\"${localProperties.getProperty("EMAIL_SENDER_PASSWORD", "")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "app"

    productFlavors {
        create("user") {
            dimension = "app"
            applicationId = "com.app.billsense"
            versionCode = 28
            versionName = "1.5.13"
            resValue("string", "app_label", "BillSense")
            buildConfigField("String", "APP_VARIANT", "\"main\"")
        }
        create("admin") {
            dimension = "app"
            applicationId = "com.admin.billsense"
            versionCode = 3
            versionName = "1.1.1"
            resValue("string", "app_label", "BillSense Admin")
            buildConfigField("String", "APP_VARIANT", "\"admin\"")
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"https://billsense-api-340624938055.asia-southeast2.run.app\"")
            buildConfigField("String", "WS_BASE_URL", "\"wss://billsense-api-340624938055.asia-southeast2.run.app\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "API_BASE_URL", "\"https://billsense-api-340624938055.asia-southeast2.run.app\"")
            buildConfigField("String", "WS_BASE_URL", "\"wss://billsense-api-340624938055.asia-southeast2.run.app\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {

    implementation(project(":gmailbackgroundlibrary"))

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.database)
    implementation (libs.ccp)
    implementation (libs.circleimageview)
    implementation (libs.glide)
    implementation(libs.firebase.storage)
    implementation(libs.play.services.maps)
    implementation(libs.places)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.ml.modeldownloader)
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    annotationProcessor (libs.compiler)
    implementation (libs.imagepicker)
    implementation ("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")
    //implementation ("com.github.yesidlazaro:GmailBackground:1.2.0")
    implementation("com.wdullaer:materialdatetimepicker:4.2.3")

    implementation ("com.squareup.okhttp3:okhttp:4.12.0")
    implementation ("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
    // For Google Auth Library (to get OAuth2 token from service account)
    implementation ("com.google.auth:google-auth-library-oauth2-http:1.24.0")
    implementation ("com.google.auth:google-auth-library-credentials:1.24.0")
    implementation ("androidx.camera:camera-core:1.4.2")
    implementation ("androidx.camera:camera-camera2:1.4.2")
    implementation ("androidx.camera:camera-lifecycle:1.4.2")
    implementation ("androidx.camera:camera-view:1.4.2")
    implementation ("androidx.camera:camera-extensions:1.4.2")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
