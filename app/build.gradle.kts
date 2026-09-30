plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.dmgram"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "app.dmgram"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("versionCode").get().toInt()
        versionName = providers.gradleProperty("versionName").get()
        buildConfigField("String", "GITHUB_REPO", dmgramConfig("dmgram.githubRepo"))
        buildConfigField("String", "RULES_URL", dmgramConfig("dmgram.rulesUrl"))
        buildConfigField("String", "UPDATES_URL", dmgramConfig("dmgram.updatesUrl"))
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

}

val copyDefaultRules = tasks.register<Copy>("copyDefaultRules") {
    from(rootProject.file("rules/rules.json"))
    into(layout.buildDirectory.dir("generated/dmgramAssets"))
    rename { "rules.default.json" }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyDefaultRules, Copy::getDestinationDirectory)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
}

fun dmgramConfig(name: String): String {
    val value = providers.gradleProperty(name).orElse("").get()
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}
