package com.gocodalone.workflow.ide

import java.nio.file.Path
import kotlin.io.path.readText
import junit.framework.TestCase

class CompatibilityMetadataTest : TestCase() {

    fun testReadmeMatchesSinceBuildCompatibility() {
        val repoRoot = Path.of("").toAbsolutePath()
        val buildGradle = repoRoot.resolve("build.gradle.kts").readText()
        val readme = repoRoot.resolve("README.md").readText()

        assertTrue("build.gradle.kts should declare sinceBuild 252", buildGradle.contains("sinceBuild = \"252\""))
        assertTrue("README should document 2025.2+ compatibility", readme.contains("Requires IDE version **2025.2 or later**."))
    }
}
