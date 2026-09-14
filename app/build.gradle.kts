import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The app knows which revision of itself it is running. Every pull request it opens names it, and
// stage 4 compares it with the revision CI built. CI passes the sha in explicitly (on a pull
// request HEAD is a merge commit that exists nowhere else), local builds ask git.
val gitSha: Provider<String> = providers.environmentVariable("SELF_PR_GIT_SHA").orElse(
    providers.exec {
        commandLine("git", "rev-parse", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText
).map { it.trim().ifEmpty { "unknown" } }

// Keys come from local.properties (git-ignored) and reach the app through BuildConfig. Empty values
// are allowed so CI can build without them; the Heal tab then explains what is missing.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String): String = localProps.getProperty(name) ?: System.getenv(name) ?: ""

// Stage 4: the app installs builds that CI made. Android only lets a package update itself when
// both APKs carry the same signature, so the local build and the CI build share one key. It lives
// outside the repository (path in local.properties here, a base64 secret decoded to a file in CI);
// without it the build falls back to the default debug key and the Update tab says so.
val updateKeystore: File? = secret("SELF_PR_KEYSTORE").takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }

// The knowledge base: the app's own sources, tests, build file and architecture notes, zipped at
// build time and bundled as an asset. This is what the app reads when it diagnoses itself.
val bundleSource by tasks.registering(Zip::class) {
    archiveFileName.set("source.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/kb"))
    from(rootProject.projectDir) {
        include("app/src/main/java/**", "app/src/test/java/**", "app/src/main/AndroidManifest.xml",
            "app/build.gradle.kts", "docs/ARCHITECTURE.md", "gradle/libs.versions.toml")
    }
}

android {
    namespace = "com.ioscastaway.selfpr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ioscastaway.selfpr"
        // 30 = Android 11: ApplicationExitInfo (crash/ANR history) starts here.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "GIT_SHA", "\"${gitSha.get()}\"")
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"${secret("ANTHROPIC_API_KEY")}\"")
        buildConfigField("String", "GITHUB_TOKEN", "\"${secret("GITHUB_TOKEN")}\"")
        buildConfigField("String", "CLAUDE_MODEL", "\"claude-opus-5\"")
        buildConfigField("String", "SELF_REPO", "\"ioscastaway/self-pr\"")
        buildConfigField("String", "SELF_BASE_BRANCH", "\"main\"")
        buildConfigField("String", "CI_WORKFLOW", "\"ci.yml\"")
        buildConfigField("String", "CI_ARTIFACT", "\"app-debug\"")
        buildConfigField("boolean", "SIGNED_FOR_UPDATE", "${updateKeystore != null}")
    }

    signingConfigs {
        if (updateKeystore != null) {
            create("update") {
                storeFile = updateKeystore
                storePassword = secret("SELF_PR_KEYSTORE_PASSWORD")
                keyAlias = secret("SELF_PR_KEY_ALIAS").ifBlank { "self-pr" }
                keyPassword = secret("SELF_PR_KEY_PASSWORD").ifBlank { secret("SELF_PR_KEYSTORE_PASSWORD") }
            }
        }
    }

    buildTypes {
        debug {
            if (updateKeystore != null) signingConfig = signingConfigs.getByName("update")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties", "META-INF/versions/9/module-info.class",
                "META-INF/*.kotlin_module",
            )
        }
    }

    sourceSets {
        getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/kb"))
        getByName("main").assets.srcDir(rootProject.file("docs"))
    }
}

tasks.named("preBuild") { dependsOn(bundleSource) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.anthropic.java)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
