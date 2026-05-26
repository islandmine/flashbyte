plugins {
    java
    `maven-publish`
}

extensions.configure<PublishingExtension> {
    repositories {
        maven {
            name = "reposiliteRepositoryPrivate"
            url = uri("http://repo.islandmine.net/private")
            isAllowInsecureProtocol = true
            credentials(PasswordCredentials::class.java)
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Flashbyte")
                description.set("The modern, next-generation Minecraft server proxy")
                url.set("https://github.com/flashbyte")
                scm {
                    url.set("https://github.com/flashbyte")
                    connection.set("scm:git:https://github.com/flashbyte.git")
                    developerConnection.set("scm:git:https://github.com/flashbyte.git")
                }
            }
        }
    }
}
