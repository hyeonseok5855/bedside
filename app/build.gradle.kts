import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 개인 위치 설정(집·회사)은 gitignore된 personal.properties에서만 읽는다.
// 커밋되는 코드에는 실제 주소/좌표가 들어가지 않는다(없으면 빈 값으로 빌드됨).
val personalProps = Properties().apply {
    // Properties.load(InputStream)은 ISO-8859-1로 읽어 한글이 깨진다. UTF-8 Reader로 읽는다.
    val f = rootProject.file("personal.properties")
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
}
fun personal(key: String, default: String = ""): String =
    personalProps.getProperty(key)?.trim() ?: default

android {
    namespace = "com.bedside"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bedside"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "HOME_LABEL", "\"${personal("home.label", "집")}\"")
        buildConfigField("String", "HOME_ADDRESS", "\"${personal("home.address")}\"")
        buildConfigField("String", "HOME_LAT", "\"${personal("home.lat")}\"")
        buildConfigField("String", "HOME_LNG", "\"${personal("home.lng")}\"")
        buildConfigField("String", "WORK_LABEL", "\"${personal("work.label", "회사")}\"")
        buildConfigField("String", "WORK_ADDRESS", "\"${personal("work.address")}\"")
        buildConfigField("String", "WORK_LAT", "\"${personal("work.lat")}\"")
        buildConfigField("String", "WORK_LNG", "\"${personal("work.lng")}\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // play-services-location이 더 최신 Kotlin 메타데이터로 배포돼, 버전 체크만
        // 건너뛴다(바이트코드는 호환). 라이브러리 업그레이드 시 제거 검토.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.health.connect.client)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.android)
}
