import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.13.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.modules.json")

        // LSP4IJ for LSP client support
        plugin("com.redhat.devtools.lsp4ij:0.7.0")

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
    }

    pluginVerification {
        ides {
            create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2025.2")
        }
        // Kotlin generates synthetic overrides for all ToolWindowFactory interface methods,
        // including ones marked @Internal in newer SDK versions. These are bridge methods,
        // not actual overrides in our code. Exclude INTERNAL_API_USAGES from failure.
        failureLevel = org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension.PluginVerification.FailureLevel.entries.filter {
            it != org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension.PluginVerification.FailureLevel.INTERNAL_API_USAGES
        }
    }
}

tasks.register<Exec>("npmInstall") {
    workingDir = file("webview-src")
    commandLine("npm", "ci")
}

tasks.register<Exec>("buildWebview") {
    workingDir = file("webview-src")
    commandLine("npx", "vite", "build", "--outDir", "../src/main/resources/editor")
    dependsOn("npmInstall")
}

tasks.named("processResources") {
    dependsOn("buildWebview")
}
