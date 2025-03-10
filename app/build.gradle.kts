plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    //authentification
    //implementation ("com.firebaseui:firebase-ui-auth:7.2.0")
    implementation ("com.google.firebase:firebase-auth-ktx")
    implementation ("com.google.android.gms:play-services-auth:20.7.0")


    implementation(libs.androidx.navigation.compose)

    implementation("com.google.android.gms:play-services-location:21.3.0")

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

}