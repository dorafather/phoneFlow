import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// release 서명 설정은 저장소 바깥(PhoneFlow-release/keystore/keystore.properties)의
// 로컬 파일에서만 읽는다 - 키스토어/비밀번호를 git에 커밋하지 않기 위함.
// 이 파일이 없는 환경(저장소를 새로 클론한 경우 등)에서는 release 빌드에
// 서명 설정을 적용하지 않는다(디버그 서명으로 빌드는 되지만 배포용으로는
// 못 씀 - 키스토어를 직접 발급해 keystore.properties를 만들어야 한다).
val keystorePropsFile = rootProject.file("../keystore/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.dorafather.phoneflow"
    compileSdk = 34

    ndkVersion = "23.2.8568313"

    defaultConfig {
        applicationId = "com.dorafather.phoneflow"
        minSdk = 24
        targetSdk = 34
        versionCode = 19
        versionName = "1.9.0"

        // 실기기 대부분을 커버하는 arm64-v8a 하나만(빌드 시간 단축).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = keystorePropsFile.parentFile.resolve(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Kotlin 1.9.24(Compose Compiler 플러그인이 아직 Kotlin 빌드에 내장되지
    // 않은 K1 시절 버전)이라 composeOptions로 직접 Compose Compiler 확장
    // 버전을 지정해야 한다. 공식 호환표 기준 Kotlin 1.9.24 <-> Compose
    // Compiler 1.5.14 조합.
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 명령어 서랍(☰)의 메뉴 아이콘 - material3는 아이콘을 자체 포함하지
    // 않고 이 별도 아티팩트(core 아이콘셋)에 기대야 한다.
    implementation("androidx.compose.material:material-icons-core")
}
