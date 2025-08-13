plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.7.0"
}

group = "io.github.weihubeats"
version = "0.0.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2024.2.5")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

         bundledPlugin("com.intellij.java")
    }

    compileOnly("org.projectlombok:lombok:1.18.30") // 使用最新版本
    annotationProcessor("org.projectlombok:lombok:1.18.30")
}

intellijPlatform {
    pluginConfiguration {
//        ideaVersion {
//            sinceBuild = "242"
//        }

        changeNotes = """
      Initial version
    """.trimIndent()
    }
}



tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}
