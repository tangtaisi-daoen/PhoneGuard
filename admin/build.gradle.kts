import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// release 签名：读取本地 keystore.properties（gitignore），缺失时跳过
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasSigning = keystoreProps.getProperty("storeFile") != null

// CloudBase 环境 ID：自托管模式——构建时注入（-PcloudbaseEnvId=xxx 或环境变量 CLOUDBASE_ENV_ID），
// 未注入时使用占位符（开箱不可用，README 指引自建环境）。
val cloudbaseEnvId: String = (project.findProperty("cloudbaseEnvId") as? String)
    ?: System.getenv("CLOUDBASE_ENV_ID")
    ?: "YOUR_ENV_ID"

android {
    namespace = "com.familyguard.admin"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.familyguard.admin"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "0.1.8"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    defaultConfig {
        // CloudBase 环境 ID（自托管：构建时 -PcloudbaseEnvId 注入；占位符需用户自建环境替换）
        buildConfigField("String", "CLOUDBASE_ENV_ID", "\"$cloudbaseEnvId\"")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
}
