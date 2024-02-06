plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.albadr.printer"
    compileSdk = 34
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.albadr.printer"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}


dependencies {

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(files("libs/btsdk.jar"))
    implementation(files("libs/PdfViewer.jar"))

    implementation("com.google.firebase:firebase-appindexing:20.0.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.squareup.picasso:picasso:2.71828")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))
    implementation("com.google.firebase:firebase-analytics")

    implementation("com.google.firebase:firebase-config")



}