// 최상위 build.gradle.kts - 모듈에서 공통으로 쓰는 플러그인 버전만 선언한다.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
