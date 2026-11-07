plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(21)
    sourceSets {
        main {
            // the value-type parser is shared with the plugin; it has no platform dependencies
            kotlin.srcDir(rootProject.file("src/main/kotlin/dev/dybug/routeros/schema/shared"))
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.gson)
    testImplementation(libs.junit4)
}

application {
    mainClass = "dev.dybug.routeros.extractor.MainKt"
    applicationDefaultJvmArgs = listOf("-Xmx512m")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    // use the same proxy as Gradle itself (systemProp.https.proxyHost in ~/.gradle/gradle.properties)
    for (key in listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "http.nonProxyHosts")) {
        providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
    }
}

tasks.test {
    maxHeapSize = "512m"
}
