plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    signing
}

android {
    namespace = "com.taskgate.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
}

android {
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "co.taskgate"
                artifactId = "sdk"
                version = "1.0.16"

                from(components["release"])

                // POM metadata required by Maven Central
                pom {
                    name.set("TaskGate SDK")
                    description.set("Official Android SDK for TaskGate")
                    url.set("https://github.com/task-gate/taskgate-sdk-android")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("taskgate")
                            name.set("TaskGate Team")
                            email.set("dev@taskgate.co")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/task-gate/taskgate-sdk-android.git")
                        developerConnection.set("scm:git:ssh://github.com/task-gate/taskgate-sdk-android.git")
                        url.set("https://github.com/task-gate/taskgate-sdk-android")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "mavenCentral"
                url = uri(layout.buildDirectory.dir("repo"))
            }
        }
    }

    // Signing configuration - use GPG command (gpg-agent will handle passphrase)
    signing {
        useGpgCmd()
        sign(publishing.publications["release"])
    }
}
