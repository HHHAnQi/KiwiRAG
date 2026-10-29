plugins {
    java
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":platform-common"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.3.2"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("io.minio:minio:8.5.12")
    implementation("io.milvus:milvus-sdk-java:2.5.15")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
