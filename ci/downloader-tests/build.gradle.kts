import java.time.Duration

plugins {
    kotlin("jvm") version "2.3.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    sourceSets {
        named("main") {
            kotlin.setSrcDirs(listOf("../../src/main/kotlin"))
            kotlin.include("com/gocodalone/workflow/ide/BinaryFileDownloader.kt")
        }
        named("test") {
            kotlin.setSrcDirs(listOf("../../src/test/kotlin"))
            kotlin.include("com/gocodalone/workflow/ide/BinaryFileDownloaderTest.kt")
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    filter {
        includeTestsMatching("com.gocodalone.workflow.ide.BinaryFileDownloaderTest")
        isFailOnNoMatchingTests = true
    }
    failOnNoDiscoveredTests.set(true)
    maxParallelForks = 1
    maxHeapSize = "128m"
    timeout.set(Duration.ofMinutes(2))
    testLogging {
        events("passed", "skipped", "failed")
    }
}
