plugins {
    kotlin("jvm") version "1.8.22"
    id("priv.seventeen.artist.blink") version "1.3.12"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    `maven-publish`
}

blink {
    name.set("Overture")
    version.set("1.0.0")
    description.set("Next-generation item library plugin")
    authors.set(listOf("17Artist"))
    packageName.set("priv.seventeen.artist.overture")
    apiVersion.set("1.20")
    logPrefix.set("§b♪ §dOverture")
    depend.set(listOf())
    softDepend.set(listOf("ArcartX"))
    foliaSupported.set(false)
    enableAsteroid.set(true)
    enableAria.set(true)
}

repositories {
    maven("https://repo.arcartx.com/repository/maven-public/")
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    implementation("priv.seventeen.artist.blink:blink-common:1.3.12")
    compileOnly("org.spigotmc:spigot-api:1.18.2-R0.1-SNAPSHOT")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("priv.seventeen.artist.arcartx:ArcartX:2.2.0.5")
}

kotlin {
    jvmToolchain(17)
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.named("build") {
    dependsOn("shadowJar")
}

val repoPassword: String = System.getenv("repo") ?: ""

publishing {
    publications {
        create<MavenPublication>("shadow") {
            artifact(tasks.shadowJar.get().archiveFile) {
                classifier = null
            }
            artifactId = rootProject.name.lowercase()
            val buildNumber = System.getenv("BUILD_NUMBER")
            version = if (buildNumber != null) "${project.version}.$buildNumber" else project.version.toString()
        }
    }
    repositories {
        maven {
            url = uri(project.findProperty("mavenRepoUrl") as? String ?: "")
            isAllowInsecureProtocol = true
            credentials {
                username = project.findProperty("mavenRepoUser") as? String ?: ""
                password = repoPassword
            }
        }
    }
}

tasks.register("deploy") {
    group = "publishing"
    description = "Publish shadow jar to Maven repository"
    dependsOn("publish")
}
