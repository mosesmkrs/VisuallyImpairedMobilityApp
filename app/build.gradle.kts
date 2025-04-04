import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.gms.google.services)
    alias(libs.plugins.kotlin.kapt)


}

android {
    namespace = "com.example.newapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.newapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val properties = Properties().apply {
            load(project.rootProject.file("local.properties").inputStream())
        }

        buildConfigField("String", "API_KEY", "\"${properties.getProperty("API_KEY")}\"")
        buildConfigField("String", "FIREBASE_KEY", "\"${properties.getProperty("FIREBASE_KEY")}\"")
        buildConfigField("String", "GRASSHOPPER_API_KEY", "\"${properties.getProperty("GRASHOPPER_API_KEY")}\"")

    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.9"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
//    implementation(libs.firebase.database)
//    implementation(libs.play.services.vision.common)
//    implementation(libs.androidx.storage)
    implementation("com.google.firebase:firebase-database-ktx:20.2.1")
    implementation("com.google.android.gms:play-services-vision:20.1.3")
    implementation("androidx.security:security-crypto:1.0.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    //authentication
    //implementation ("com.firebaseui:firebase-ui-auth:7.2.0")
    implementation (libs.google.firebase.auth.ktx)
    implementation (libs.play.services.auth)

    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.converter.scalars)


    implementation(libs.coil.compose)
    implementation("org.osmdroid:osmdroid-android:6.1.10")
    implementation ("com.google.android.gms:play-services-location:21.0.1")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.location)

    implementation("org.osmdroid:osmdroid-android:6.1.10")
    implementation ("com.google.android.gms:play-services-location:21.0.1")


//    implementation(libs.pusher)
//    implementation(libs.google.maps)
//    implementation(libs.retrofit)
//    implementation(libs.retrofit.converter.scalars)

    // ArcGIS Maps for Kotlin - SDK dependency
    implementation(libs.arcgis.maps.kotlin)
    // Toolkit dependencies
    implementation(platform(libs.arcgis.maps.kotlin.toolkit.bom))
    implementation(libs.arcgis.maps.kotlin.toolkit.geoview.compose)
    // Additional modules from Toolkit, if needed, such as:
    // implementation(libs.arcgis.maps.kotlin.toolkit.authentication)
    implementation (libs.okhttp3.logging.interceptor)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    //Accessibility dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    //implementation("androidx.accessibility:accessibility:1.3.0")
    //implementation("androidx.speech.tts:tts:1.0.0")
    implementation ("androidx.compose.ui:ui:1.4.3")
    implementation ("androidx.compose.runtime:runtime:1.4.3")
    implementation ("androidx.compose.material:material-icons-extended:1.6.1")
    implementation ("androidx.room:room-runtime:2.5.0")
    kapt ("androidx.room:room-compiler:2.5.0")
    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.6.0")



}