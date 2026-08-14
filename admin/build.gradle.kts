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
        // CloudBase 环境 ID（非敏感信息），真实环境配置见 specs/spec.md
        buildConfigField("String", "CLOUDBASE_ENV_ID", "\"YOUR_ENV_ID\"")
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
