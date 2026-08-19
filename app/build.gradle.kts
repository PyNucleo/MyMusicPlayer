plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

val releaseStorePath = providers.environmentVariable("MY_MUSIC_PLAYER_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("MY_MUSIC_PLAYER_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("MY_MUSIC_PLAYER_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("MY_MUSIC_PLAYER_KEY_PASSWORD").orNull
val releaseSigningAvailable = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.admin.mymusicplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.admin.mymusicplayer"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "GIT_COMMIT", "\"${gitCommit()}\"")
        buildConfigField("String", "NEWPIPE_VERSION", "\"0.26.2\"")
        buildConfigField("String", "MEDIA3_VERSION", "\"1.10.1\"")
        buildConfigField("int", "DATABASE_VERSION", "1")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

val robolectricSdk = configurations.create("robolectricSdk")
val robolectricDependencies = rootProject.layout.projectDirectory.dir(".tools/robolectric")
val robolectricSdkJar = robolectricDependencies.file(
    "android-all-instrumented-15-robolectric-13954326-i7.jar",
)
val prepareRobolectricDependencies = tasks.register("prepareRobolectricDependencies") {
    doLast {
        val target = robolectricSdkJar.asFile
        if (!target.isFile) {
            target.parentFile.mkdirs()
            copy {
                from(robolectricSdk)
                into(target.parentFile)
            }
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    dependsOn(prepareRobolectricDependencies)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricDependencies.asFile.absolutePath)
    systemProperty("liveSourceTests", providers.systemProperty("liveSourceTests").getOrElse("false"))
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add(robolectricSdk.name, "org.robolectric:android-all-instrumented:15-robolectric-13954326-i7")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.media3:media3-common:1.10.1")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:5.2.1")

    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.2")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

fun gitCommit(): String = providers.exec {
    commandLine("git", "rev-parse", "--short=12", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().ifBlank { "uncommitted" }
