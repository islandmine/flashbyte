plugins {
    `java-library`
    id("flashbyte-checkstyle") apply false
    id("flashbyte-spotless") apply false
}

subprojects {
    apply<JavaLibraryPlugin>()

    apply(plugin = "flashbyte-checkstyle")
    apply(plugin = "flashbyte-spotless")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        testImplementation(rootProject.libs.junit)
    }

    testing.suites.named<JvmTestSuite>("test") {
        useJUnitJupiter()
        targets.all {
            testTask.configure {
                reports.junitXml.required = true
            }
        }
    }
}
