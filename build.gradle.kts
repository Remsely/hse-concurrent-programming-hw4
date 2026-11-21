plugins {
    id("java")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

group = "edu.hse.concurrency.cache"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("loadProbe") {
    dependsOn(tasks.testClasses)
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "edu.hse.concurrency.cache.CacheLoadProbe"
    args(providers.gradleProperty("workers").orElse("8").get())
}
