plugins {
    java
    id("org.springframework.boot") version "3.2.4"
    id("io.spring.dependency-management") version "1.1.7"
}
val springCloudVersion by extra("2023.0.1")

group = "com.winworld"
version = "0.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

/**
Dependencies Versions
 */

val mapstructVersion = "1.5.3.Final"
val jjwtVersion = "0.12.6"
val lombokVersion = "1.18.26"
val hypersistenceUtilsHibernate = "3.9.5"
val stripeJava = "28.4.0"
val flywayVersion = "11.5.0"
val commonsCodecVersion = "1.16.0"
val springDocWebMvcUiVersion = "2.1.0"
val jacksonNullableVersion = "0.2.6"
val wireMockVersion = "3.12.1"
val testContainersRedisVersion = "2.2.2"
val logbackEncoderVersion = 7.4
dependencies {
    /**
    Spring starters
     */
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    /**
    Security
     */
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    testImplementation("org.testcontainers:junit-jupiter")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")
    /**
    Database
     */
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    runtimeOnly("org.postgresql:postgresql")
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:$hypersistenceUtilsHibernate")

    /**
    Utils
     */
    implementation("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    implementation ("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
    implementation("commons-codec:commons-codec:$commonsCodecVersion")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springDocWebMvcUiVersion")
    implementation("org.openapitools:jackson-databind-nullable:$jacksonNullableVersion")

    /**
    External
     */
    implementation("com.stripe:stripe-java:$stripeJava")

    /**
    Observability
     */
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    /**
    Test
     */
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
