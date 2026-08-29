/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.gui.data.model.SupportedApp
import kotlin.test.Test

/**
 * A version name does not identify a build. Apps like Instagram ship many builds
 * under one version, and the patches target only some of them, so matching on the
 * name alone reports an APK as supported that will fail at patch time.
 */
class BuildCodeSupportTest {

    private fun app(
        stable: List<String> = listOf("380.0.0.29.109"),
        experimental: List<String> = emptyList(),
        buildCodes: Map<String, Set<Int>> = emptyMap(),
    ) = SupportedApp(
        packageName = "com.instagram.android",
        displayName = "Instagram",
        supportedVersions = stable,
        experimentalVersions = experimental,
        recommendedVersion = stable.firstOrNull(),
        versionBuildCodes = buildCodes,
    )

    @Test
    fun noConstraintAcceptsAnyBuild() {
        val subject = app()
        check(subject.buildCodeSupported("380.0.0.29.109", 123)) { "unconstrained version rejected a build" }
        check(subject.buildCodeSupported("380.0.0.29.109", null)) { "unconstrained version rejected a null build" }
    }

    @Test
    fun constrainedVersionAcceptsOnlyItsBuilds() {
        val subject = app(buildCodes = mapOf("380.0.0.29.109" to setOf(700, 701)))
        check(subject.buildCodeSupported("380.0.0.29.109", 700)) { "a targeted build was rejected" }
        check(!subject.buildCodeSupported("380.0.0.29.109", 999)) { "an untargeted build was accepted" }
    }

    @Test
    fun unreadableBuildPasses() {
        // Refusing an APK because its build could not be read would be worse than
        // letting the patch itself decide.
        val subject = app(buildCodes = mapOf("380.0.0.29.109" to setOf(700)))
        check(subject.buildCodeSupported("380.0.0.29.109", null)) { "a null build was rejected" }
    }

    @Test
    fun versionPrefixIsIgnoredWhenMatching() {
        val subject = app(buildCodes = mapOf("380.0.0.29.109" to setOf(700)))
        check(!subject.buildCodeSupported("v380.0.0.29.109", 999)) { "a v-prefixed version skipped the check" }
        check(subject.buildCodeSupported("v380.0.0.29.109", 700)) { "a v-prefixed version rejected a good build" }
    }

    @Test
    fun statusReportsBuildMismatchRatherThanClaimingSupport() {
        val subject = app(buildCodes = mapOf("380.0.0.29.109" to setOf(700)))
        // The version is the latest stable, so name-only matching says LATEST_STABLE.
        val wrongBuild = resolveVersionStatus("380.0.0.29.109", subject, versionCode = 999)
        check(wrongBuild.status == VersionStatus.BUILD_UNSUPPORTED) {
            "expected BUILD_UNSUPPORTED, got ${wrongBuild.status}"
        }
        val rightBuild = resolveVersionStatus("380.0.0.29.109", subject, versionCode = 700)
        check(rightBuild.status == VersionStatus.LATEST_STABLE) {
            "a targeted build should still read as supported, got ${rightBuild.status}"
        }
    }

    @Test
    fun buildCheckAppliesToExperimentalVersionsToo() {
        val subject = app(
            stable = listOf("379.0.0.0.0"),
            experimental = listOf("380.0.0.29.109"),
            buildCodes = mapOf("380.0.0.29.109" to setOf(700)),
        )
        val resolved = resolveVersionStatus("380.0.0.29.109", subject, versionCode = 999)
        check(resolved.status == VersionStatus.BUILD_UNSUPPORTED) {
            "experimental versions skipped the build check, got ${resolved.status}"
        }
    }

    @Test
    fun oneUnconstrainedPatchOpensTheWholeVersion() {
        // Mirrors morphe-manager's patchableAt: it passes when ANY target for the
        // version names no build codes. An empty set carries that, so a patch that
        // constrains the version cannot narrow it on another patch's behalf.
        val subject = app(buildCodes = mapOf("380.0.0.29.109" to emptySet()))
        check(subject.buildCodeSupported("380.0.0.29.109", 999)) {
            "an unconstrained target should accept any build"
        }
        val resolved = resolveVersionStatus("380.0.0.29.109", subject, versionCode = 999)
        check(resolved.status == VersionStatus.LATEST_STABLE) {
            "unconstrained version reported as a build mismatch: ${resolved.status}"
        }
    }

    @Test
    fun unknownVersionIsUnaffectedByBuildCodes() {
        // A version nobody targets is placed by version comparison as before.
        val subject = app(buildCodes = mapOf("380.0.0.29.109" to setOf(700)))
        val resolved = resolveVersionStatus("999.0.0.0.0", subject, versionCode = 1)
        check(resolved.status == VersionStatus.TOO_NEW) { "expected TOO_NEW, got ${resolved.status}" }
    }
}
