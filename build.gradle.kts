import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.shadow)
    application
    `maven-publish`
    signing
}

group = "app.morphe"

// ============================================================================
// JVM / Kotlin Configuration
// ============================================================================
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// ============================================================================
// Application Entry Point
// ============================================================================
// Shadow JAR reads this for Main-Class manifest attribute.
//
//   No args / double-click  →  GUI (Compose Desktop)
//   With args (terminal)    →  CLI (PicoCLI)
application {
    mainClass.set("app.morphe.MorpheLauncherKt")
}

// ============================================================================
// Repositories
// ============================================================================
repositories {
    mavenLocal()
    mavenCentral()
    google()
    maven {
        // A repository must be specified for some reason. "registry" is a dummy.
        url = uri("https://maven.pkg.github.com/MorpheApp/registry")
        credentials {
            username = project.findProperty("gpr.user") as String?
                ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String?
                ?: System.getenv("GITHUB_TOKEN")
        }
    }
    // Obtain baksmali/smali from source builds - https://github.com/iBotPeaches/smali
    // Remove when official smali releases come out again.
    maven { url = uri("https://jitpack.io") }
}

// ============================================================================
// Dependencies
// ============================================================================
val apkEditorLib by configurations.creating

val strippedApkEditorLib by tasks.registering(org.gradle.jvm.tasks.Jar::class) {
    archiveFileName.set("APKEditor-cli.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    doFirst {
        from(apkEditorLib.resolve().map { zipTree(it) })
    }
    exclude(
        "org/xmlpull/**",
        "antlr/**",
        "org/antlr/**",
        "com/beust/jcommander/**",
        "javax/annotation/**",
        "smali.properties",
        "baksmali.properties",
        "/prebuilt/android/**"
    )
}

dependencies {
    api(libs.morphe.patcher)
    implementation(libs.morphe.library)
    implementation(libs.picocli)
    apkEditorLib(files("$rootDir/libs/APKEditor-1.4.7.jar"))
    implementation(files(strippedApkEditorLib))

    // -- Compose Desktop ---------------------------------------------------
    // Platform-independent: single JAR runs on all supported OSes.
    // Skiko auto-detects the OS at runtime and loads the correct native library.
    implementation(compose.desktop.macos_arm64)
    implementation(compose.desktop.macos_x64)
    implementation(compose.desktop.linux_x64)
    implementation(compose.desktop.linux_arm64)
    implementation(compose.desktop.windows_x64)
    implementation(compose.components.resources)
    @Suppress("DEPRECATION")
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // -- Async / Serialization ---------------------------------------------
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
//    testImplementation(libs.kotlin.test)
//}

    // -- Networking (GUI) --------------------------------------------------
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // -- DI / Navigation (GUI) ---------------------------------------------
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose)

    implementation(libs.voyager.navigator)
    implementation(libs.voyager.screenmodel)
    implementation(libs.voyager.koin)
    implementation(libs.voyager.transitions)

    // -- APK Parsing (GUI) -------------------------------------------------
    implementation(libs.apk.parser)

    // -- Testing -----------------------------------------------------------
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.params)
    testImplementation(libs.mockk)
}

// ============================================================================
// Tasks
// ============================================================================
tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("PASSED", "SKIPPED", "FAILED")
        }
    }

    processResources {
        // Only expand properties files, not binary files like PNG/ICO
        filesMatching("**/*.properties") {
            expand("projectVersion" to project.version)
        }
    }

    // -------------------------------------------------------------------------
    // Shadow JAR — the only distribution artifact
    // -------------------------------------------------------------------------
    shadowJar {
        exclude(
            "/prebuilt/linux/aapt",
            "/prebuilt/windows/aapt.exe",
            "/prebuilt/*/aapt_*",
        )
        minimize {
            exclude(dependency("org.bouncycastle:.*"))
            exclude(dependency("app.morphe:morphe-patcher"))
            // Compose / Skiko / Swing — cannot be minimized (reflection, native libs)
            exclude(dependency("org.jetbrains.compose.*:.*"))
            exclude(dependency("org.jetbrains.skiko:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-swing:.*"))
            // Ktor uses ServiceLoader
            exclude(dependency("io.ktor:.*"))
            // Koin uses reflection
            exclude(dependency("io.insert-koin:.*"))
        }

        mergeServiceFiles()
    }

    distTar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    distZip {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    publish {
        dependsOn(shadowJar)
    }
}

// ============================================================================
// Publishing / Signing
// ============================================================================
// Needed by gradle-semantic-release-plugin.
// Tracking: https://github.com/KengoTODA/gradle-semantic-release-plugin/issues/435

// The maven-publish is also necessary to make the signing plugin work.
publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("morphe-cli-publication") {
            from(components["java"])
        }
    }
}

signing {
    useGpgCmd()

    sign(publishing.publications["morphe-cli-publication"])
}
