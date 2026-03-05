plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")

}

android {
    namespace = "com.albadr.printer"
    compileSdk = 35
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.albadr.printer"
        minSdk = 24
        targetSdk = 35
        versionCode = 10
        versionName = "10.0.0"

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
//    implementation("printer-lib-3.1.6', ext: 'aar")
//    implementation("printer-lib-3.1.6', ext: 'aar")
//    implementation("io.github.jeremyliao:live-event-bus-x:1.8.0")
     implementation ("com.github.neo-turak:LiveEventBus:1.8.1")
    implementation("io.reactivex.rxjava3:rxjava:3.1.5")

    implementation("com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0")


    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))
    implementation("com.google.firebase:firebase-analytics")

    implementation("com.google.firebase:firebase-config")

    // Add the dependencies for the Crashlytics and Analytics libraries
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
// albadr.envoy@gmail.com



}