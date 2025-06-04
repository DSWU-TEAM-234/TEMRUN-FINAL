plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id ("kotlin-parcelize")
    id ("kotlin-kapt")
}

android {
    namespace = "com.temrun_finalprojects"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.temrun_finalprojects"
        minSdk = 24
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
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    implementation ("org.tensorflow:tensorflow-lite:2.11.0") // 또는 최신 버전
    implementation ("org.tensorflow:tensorflow-lite-gpu:2.11.0") // GPU 가속 (선택 사항)
//    implementation ("org.tensorflow:tensorflow-lite-support:2.11.0")
    implementation ("org.tensorflow:tensorflow-lite-select-tf-ops:2.11.0") // 중요

    implementation ("com.github.bumptech.glide:glide:4.16.0")
    kapt ("com.github.bumptech.glide:compiler:4.16.0")

    //기록(캘린더) 작성을 위한 MaterialCalendarView 관련된 의존성 추가
    implementation("com.prolificinteractive:material-calendarview:1.4.3")


}