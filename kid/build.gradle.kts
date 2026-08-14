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
    namespace = "com.familyguard.kid"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.familyguard.kid"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "0.1.14"
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
        debug {
            // 调试包与正式包并存，避免开发签名占用正式远程更新的包名。
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
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
        // 远程更新清单地址（自托管：构建时 -PupdateManifestUrl 注入；默认占位符）
        val updateManifestUrl: String = (project.findProperty("updateManifestUrl") as? String)
            ?: System.getenv("UPDATE_MANIFEST_URL")
            ?: "YOUR_UPDATE_MANIFEST_URL"
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
}
