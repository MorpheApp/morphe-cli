import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.about.libraries)
    // Shadow plugin is provided by buildSrc to enable the custom NoticeMergeTransformer.
    // Applied without a version here; the version is pinned in buildSrc/build.gradle.kts.
    id("com.gradleup.shadow")
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
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
    sourceSets.main {
        kotlin.srcDir(layout.buildDirectory.dir("generated/source/bootstrap/main"))
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
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    maven {
        // A repository must be specified for some reason. "registry" is a dummy.
        url = uri("https://maven.pkg.github.com/MorpheApp/registry")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
    // Obtain baksmali/smali from source builds - https://github.com/iBotPeaches/smali
    // Remove when official smali releases come out again.
    maven { url = uri("https://jitpack.io") }
}

// ============================================================================
// Bootstrap (GUI) Dependency Code Generation
// ============================================================================
val bootstrapDependencies = configurations.create("bootstrapDependencies") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    api(libs.morphe.patcher)
    implementation(libs.arsclib)
    implementation(libs.morphe.library)
    implementation(libs.jadb) {
        exclude(group = "org.mockito")
    }
    implementation(libs.picocli)

    // -- Bootstrap (Code Generation) ---------------------------------------
    bootstrapDependencies("org.jetbrains.compose.material:material-icons-extended-desktop:${libs.versions.materialIcons.get()}")
    bootstrapDependencies("net.java.dev.jna:jna:${libs.versions.jna.get()}")
    bootstrapDependencies("net.java.dev.jna:jna-platform:${libs.versions.jna.get()}")
    bootstrapDependencies("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:${libs.versions.skiko.get()}")
    bootstrapDependencies("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:${libs.versions.skiko.get()}")
    bootstrapDependencies("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:${libs.versions.skiko.get()}")
    bootstrapDependencies("org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:${libs.versions.skiko.get()}")
    bootstrapDependencies("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:${libs.versions.skiko.get()}")

    // -- Compose Desktop ---------------------------------------------------
    // Platform-independent: single JAR runs on all supported OSes.
    // Skiko auto-detects the OS at runtime and loads the correct native library.
    implementation("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:${libs.versions.compose.get()}")
    implementation("org.jetbrains.compose.desktop:desktop-jvm-macos-x64:${libs.versions.compose.get()}")
    implementation("org.jetbrains.compose.desktop:desktop-jvm-linux-x64:${libs.versions.compose.get()}")
    implementation("org.jetbrains.compose.desktop:desktop-jvm-linux-arm64:${libs.versions.compose.get()}")
    implementation("org.jetbrains.compose.desktop:desktop-jvm-windows-x64:${libs.versions.compose.get()}")
    implementation("org.jetbrains.compose.components:components-resources:${libs.versions.compose.get()}")
    @Suppress("DEPRECATION")
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-extended-desktop:1.7.3")

    // -- Async / Serialization ---------------------------------------------
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    // -- Networking (GUI) --------------------------------------------------
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.slf4j.nop)

    // -- DI / Navigation (GUI) ---------------------------------------------
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose)

    implementation(libs.voyager.navigator)
    implementation(libs.voyager.screenmodel)
    implementation(libs.voyager.koin)
    implementation(libs.voyager.transitions)

    // -- JNA (Windows DWM title bar tinting) -------------------------------
    implementation(libs.jna)
    implementation(libs.jna.platform)

    // -- License attribution UI (About / Licenses screen) -----------------
    implementation(libs.about.libraries.core)
    implementation(libs.about.libraries.m3)

    // -- Testing -----------------------------------------------------------
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.params)
    testImplementation(libs.mockk)
}

aboutLibraries {
    collect {
        configPath = file("aboutlibraries")
    }
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.EXACT
    }
}

// ============================================================================
// Tasks
// ============================================================================
tasks {
    val generateBootstrapConstants = register("generateBootstrapConstants") {
        val artifactFiles = bootstrapDependencies.incoming.files
        inputs.files(artifactFiles)
        
        val skikoVersion = libs.versions.skiko.get()
        val materialIconsVersion = libs.versions.materialIcons.get()
        val jnaVersion = libs.versions.jna.get()
        
        inputs.property("skikoVersion", skikoVersion)
        inputs.property("materialIconsVersion", materialIconsVersion)
        inputs.property("jnaVersion", jnaVersion)
        
        val outputDir = layout.buildDirectory.dir("generated/source/bootstrap/main/app/morphe/engine")
        outputs.dir(outputDir)
        
        doLast {
            val outDir = outputDir.get().asFile
            outDir.mkdirs()
            val outFile = File(outDir, "BootstrapConstants.kt")
            
            fun getHash(artifactName: String, version: String): String {
                val exactName = "$artifactName-$version.jar"
                val file = artifactFiles.find { it.name == exactName }
                    ?: error("Could not find artifact exactly matching $exactName in ${artifactFiles.map { it.name }}")
                val digest = MessageDigest.getInstance("SHA-256")
                return digest.digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            }
            
            val content = """
                package app.morphe.engine

                internal object BootstrapConstants {
                    const val SKIKO_VERSION = "$skikoVersion"
                    const val MATERIAL_ICONS_VERSION = "$materialIconsVersion"
                    const val JNA_VERSION = "$jnaVersion"
                    
                    const val MATERIAL_ICONS_HASH = "${getHash("material-icons-extended-desktop", materialIconsVersion)}"
                    const val JNA_HASH = "${getHash("jna", jnaVersion)}"
                    const val JNA_PLATFORM_HASH = "${getHash("jna-platform", jnaVersion)}"
                    
                    val SKIKO_HASHES = mapOf(
                        "macos-x64" to "${getHash("skiko-awt-runtime-macos-x64", skikoVersion)}",
                        "macos-arm64" to "${getHash("skiko-awt-runtime-macos-arm64", skikoVersion)}",
                        "linux-x64" to "${getHash("skiko-awt-runtime-linux-x64", skikoVersion)}",
                        "linux-arm64" to "${getHash("skiko-awt-runtime-linux-arm64", skikoVersion)}",
                        "windows-x64" to "${getHash("skiko-awt-runtime-windows-x64", skikoVersion)}"
                    )
                }
            """.trimIndent()
            
            outFile.writeText(content)
        }
    }

    named("compileKotlin") {
        dependsOn(generateBootstrapConstants)
    }

    jar {
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        }
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("PASSED", "SKIPPED", "FAILED")
        }
    }

    processResources {
        // Make sure the licenses are generated before the resources are processed
        dependsOn("exportLibraryDefinitions")
        from(layout.buildDirectory.file("generated/aboutLibraries/aboutlibraries.json"))

        // Only expand properties files, not binary files like PNG/ICO
        filesMatching("**/*.properties") {
            expand("projectVersion" to project.version)
        }
        // Bundle the project's NOTICE (GPL 7b/7c) and LICENSE into META-INF so they
        // land in the main JAR before the Shadow merge. Source of truth stays at the
        // repo root — these are copied at build time, not duplicated in source control.
        from(arrayOf(rootProject.file("NOTICE"), rootProject.file("LICENSE"))) {
            into("META-INF")
        }
    }

    // -------------------------------------------------------------------------
    // Shadow JAR — the only distribution artifact
    // -------------------------------------------------------------------------
    shadowJar {
        dependencies {
            exclude(dependency("org.jetbrains.skiko:skiko-awt-runtime-.*:.*"))
            exclude(dependency("org.jetbrains.compose.material:material-icons-extended-desktop:.*"))
            exclude(dependency("net.java.dev.jna:jna:.*"))
            exclude(dependency("net.java.dev.jna:jna-platform:.*"))
        }

        exclude(
            "/prebuilt/linux/aapt",
            "/prebuilt/windows/aapt.exe",
            "/prebuilt/*/aapt_*",
        )

        // Exclude unused JNA native platforms to save ~5MB.
        // We only support Mac (x64/arm64), Windows (x64), and Linux (x64/arm64).
        exclude("com/sun/jna/aix-ppc/**")
        exclude("com/sun/jna/aix-ppc64/**")
        exclude("com/sun/jna/freebsd-x86/**")
        exclude("com/sun/jna/freebsd-x86-64/**")
        exclude("com/sun/jna/freebsd-aarch64/**")
        exclude("com/sun/jna/dragonflybsd-x86-64/**")
        exclude("com/sun/jna/openbsd-x86-64/**")
        exclude("com/sun/jna/sunos-sparc/**")
        exclude("com/sun/jna/sunos-sparcv9/**")
        exclude("com/sun/jna/sunos-x86/**")
        exclude("com/sun/jna/sunos-x86-64/**")
        exclude("com/sun/jna/linux-arm/**")
        exclude("com/sun/jna/linux-armel/**")
        exclude("com/sun/jna/linux-mips64el/**")
        exclude("com/sun/jna/linux-ppc/**")
        exclude("com/sun/jna/linux-ppc64le/**")
        exclude("com/sun/jna/linux-riscv64/**")
        exclude("com/sun/jna/linux-s390x/**")
        exclude("com/sun/jna/linux-loongarch64/**")
        exclude("com/sun/jna/win32-x86/**")

        // NOTICE/LICENSE handling:
        //   * Global strategy is EXCLUDE (first-wins) so duplicates at non-transformed
        //     paths — including native libs like libskiko-*.dylib — are deduplicated.
        //     INCLUDE globally would double-pack every colliding resource and bloat the
        //     JAR by tens of MB.
        //   * For META-INF/NOTICE* paths specifically, strategy is flipped to INCLUDE
        //     via filesMatching below so all dep NOTICEs reach NoticeMergeTransformer
        //     (Shadow drops duplicates before transformers run under EXCLUDE — see
        //     ShadowJar.kt Kdoc).
        //   * Root /NOTICE and /LICENSE — our project's files, added below via from().
        //     With EXCLUDE, the first occurrence wins. Dep JARs with root-level NOTICE/
        //     LICENSE lose because our from() block is declared before Shadow processes
        //     dependency configurations.
        //   * META-INF/LICENSE — our GPL LICENSE, placed via processResources so it
        //     lands in the main JAR ahead of dep copies. Dep LICENSE files at unique
        //     paths (META-INF/androidx/**/LICENSE.txt, etc.) are preserved untouched.
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        filesMatching(listOf(
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
            "META-INF/NOTICE.md",
            "META-INF/*.kotlin_module",
            "META-INF/services/*",
        )) {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        from(rootProject.file("NOTICE"), rootProject.file("LICENSE"))
        minimize {
            exclude(dependency("org.bouncycastle:.*"))
            exclude(dependency("com.github.REAndroid:ARSCLib"))
            exclude(dependency("app.morphe:morphe-patcher"))
            // Ktor uses ServiceLoader
            exclude(dependency("io.ktor:.*"))
            exclude(dependency("org.slf4j:.*"))
            // Koin uses reflection
            exclude(dependency("io.insert-koin:.*"))
            // Coroutines Swing provides Dispatchers.Main via ServiceLoader
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-swing"))
            // JNA uses reflection + native loading for DWM title bar tinting
            exclude(dependency("net.java.dev.jna:.*"))
            // Skiko uses ServiceLoader for native registration. Same class of problem as Ktor / Koin / JNA above.
            exclude(dependency("org.jetbrains.skiko:.*"))
        }

        mergeServiceFiles()

        // Concatenate every META-INF/NOTICE (and .txt/.md variants) from all dep JARs
        // plus our own into a single merged file. Satisfies Apache 2.0 §4(d) which
        // requires preserving attribution NOTICEs of Apache-licensed dependencies.
        //
        // Shadow's built-in ApacheNoticeResourceTransformer hardcodes ASF-branded
        // copyright text that cannot be fully disabled, which would falsely attribute
        // this GPL project to the Apache Software Foundation. NoticeMergeTransformer
        // (in buildSrc) is a minimal verbatim concatenator with no boilerplate.
        transform(NoticeMergeTransformer::class.java)
    }

    distTar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    distZip {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    publish {
        dependsOn("overwriteShadowJar")
    }

    assemble {
        dependsOn("overwriteShadowJar")
    }

    val minifyShadowJar = register<proguard.gradle.ProGuardTask>("minifyShadowJar") {
        // Only run when explicitly requested via assemble or direct call
        enabled = gradle.startParameter.taskNames.any { it.contains("assemble") || it.contains("minify") || it.contains("publish") }
        dependsOn(shadowJar)

        // Use the JAR produced by shadowJar as input
        val shadowJarFile = shadowJar.get().archiveFile.get().asFile
        // Use a temporary file for the ProGuard output
        val tempJarFile = file(shadowJarFile.absolutePath.replace(".jar", ".tmp.jar"))

        injars(shadowJarFile)
        outjars(tempJarFile)

        // Load the Proguard configuration
        configuration("proguard-rules.pro")

        // Include the Java runtime classes
        val javaHome = System.getProperty("java.home")
        libraryjars("$javaHome/jmods")

        // Report on what was removed
        printusage(layout.buildDirectory.file("proguard/usage.txt").get().asFile)
    }

    val overwriteShadowJar = register("overwriteShadowJar") {
        dependsOn(minifyShadowJar)
        val shadowJarFile = shadowJar.get().archiveFile.get().asFile
        val tempJarFile = file(shadowJarFile.absolutePath.replace(".jar", ".tmp.jar"))
        
        // Only run if minification actually happened
        onlyIf { minifyShadowJar.get().enabled && tempJarFile.exists() }
        
        doLast {
            tempJarFile.copyTo(shadowJarFile, overwrite = true)
            tempJarFile.delete()
        }
    }

    // Ensure all shadow-related tasks wait for minification to finish before using the JAR.
    // This prevents "zip END header not found" errors caused by concurrent access.
    named("shadowDistZip") { dependsOn(overwriteShadowJar) }
    named("shadowDistTar") { dependsOn(overwriteShadowJar) }
    named("startShadowScripts") { dependsOn(overwriteShadowJar) }
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
        create<MavenPublication>("morphe-desktop-publication") {
            from(components["java"])
        }
    }
}

signing {
    useGpgCmd()

    sign(publishing.publications["morphe-desktop-publication"])
}
