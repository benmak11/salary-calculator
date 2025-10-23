#!/bin/bash

# Salary Calculator - Setup Script (Groovy Gradle)

set -e

echo "╔════════════════════════════════════════════════╗"
echo "║   Salary Calculator - Setup                   ║"
echo "╚════════════════════════════════════════════════╝"
echo ""

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Check Java
echo "📋 Checking Java..."
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java not installed${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo -e "${RED}❌ Java 21+ required. Current: $JAVA_VERSION${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Java $JAVA_VERSION detected${NC}"

# Create directories
echo ""
echo "📁 Creating structure..."
mkdir -p modules/{common,rules-registry,calculator,api,integration-tests}/src/{main,test}/java
mkdir -p modules/rules-registry/src/main/resources/rulepacks
mkdir -p modules/api/src/main/resources
echo -e "${GREEN}✅ Directories created${NC}"

# Create settings.gradle (ROOT)
echo ""
echo "📝 Creating settings.gradle..."
cat > settings.gradle << 'EOF'
rootProject.name = 'salary-calculator'

include 'modules:common'
include 'modules:rules-registry'
include 'modules:calculator'
include 'modules:api'
include 'modules:integration-tests'
EOF
echo -e "${GREEN}✅ settings.gradle created${NC}"

# Create build.gradle (ROOT)
echo "📝 Creating root build.gradle..."
cat > build.gradle << 'EOF'
plugins {
    id 'org.springframework.boot' version '3.3.3' apply false
    id 'io.spring.dependency-management' version '1.1.5'
    id 'java'
}

group = 'app.salary'
version = '1.0.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'io.spring.dependency-management'

    repositories {
        mavenCentral()
    }

    dependencies {
        testImplementation 'org.junit.jupiter:junit-jupiter:5.10.3'
        testImplementation 'org.mockito:mockito-core:5.7.0'
        testImplementation 'org.mockito:mockito-junit-jupiter:5.7.0'
    }

    tasks.withType(Test) {
        useJUnitPlatform()
    }
}
EOF
echo -e "${GREEN}✅ Root build.gradle created${NC}"

# Create modules/common/build.gradle
echo "📝 Creating common/build.gradle..."
cat > modules/common/build.gradle << 'EOF'
plugins {
    id 'java-library'
}

dependencies {
    api 'com.fasterxml.jackson.core:jackson-databind:2.17.1'
    api 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1'
    api 'org.slf4j:slf4j-api:2.0.13'
    implementation 'jakarta.validation:jakarta.validation-api:3.0.2'
}
EOF
echo -e "${GREEN}✅ common/build.gradle created${NC}"

# Create modules/rules-registry/build.gradle
echo "📝 Creating rules-registry/build.gradle..."
cat > modules/rules-registry/build.gradle << 'EOF'
plugins {
    id 'java-library'
}

dependencies {
    implementation project(':modules:common')
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.1'
    implementation 'com.github.ben-manes.caffeine:caffeine:3.1.8'
    implementation 'org.slf4j:slf4j-api:2.0.13'
}
EOF
echo -e "${GREEN}✅ rules-registry/build.gradle created${NC}"

# Create modules/calculator/build.gradle
echo "📝 Creating calculator/build.gradle..."
cat > modules/calculator/build.gradle << 'EOF'
plugins {
    id 'java-library'
}

dependencies {
    implementation project(':modules:common')
    implementation project(':modules:rules-registry')
    implementation 'org.slf4j:slf4j-api:2.0.13'
    implementation 'org.springframework:spring-context:6.1.10'
    implementation 'org.springframework:spring-beans:6.1.10'
}
EOF
echo -e "${GREEN}✅ calculator/build.gradle created${NC}"

# Create modules/api/build.gradle
echo "📝 Creating api/build.gradle..."
cat > modules/api/build.gradle << 'EOF'
plugins {
    id 'org.springframework.boot'
    id 'io.spring.dependency-management'
}

dependencies {
    implementation project(':modules:calculator')
    implementation project(':modules:common')
    implementation project(':modules:rules-registry')

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'
    implementation 'io.micrometer:micrometer-registry-prometheus'

    runtimeOnly 'ch.qos.logback:logback-classic'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

test {
    useJUnitPlatform()
}
EOF
echo -e "${GREEN}✅ api/build.gradle created${NC}"

# Create modules/integration-tests/build.gradle
echo "📝 Creating integration-tests/build.gradle..."
cat > modules/integration-tests/build.gradle << 'EOF'
plugins {
    id 'java'
}

dependencies {
    testImplementation project(':modules:common')
    testImplementation project(':modules:api')
    testImplementation 'org.springframework.boot:spring-boot-starter-test:3.3.3'
    testImplementation 'org.springframework.boot:spring-boot-starter-web:3.3.3'
    testImplementation 'com.fasterxml.jackson.core:jackson-databind:2.17.1'
}

test {
    useJUnitPlatform()
}
EOF
echo -e "${GREEN}✅ integration-tests/build.gradle created${NC}"

# Create US rule pack
echo ""
echo "📝 Creating US-2025.json..."
cat > modules/rules-registry/src/main/resources/rulepacks/US-2025.json << 'EOF'
{
  "metadata": {
    "country": "US",
    "taxYear": 2025,
    "version": "US-2025.10.0"
  },
  "federal": {
    "standardDeduction": {
      "SINGLE": 14600,
      "MARRIED": 29200
    },
    "brackets": [
      { "upTo": 11600, "rate": 0.10 },
      { "upTo": 47150, "rate": 0.12 },
      { "upTo": 100525, "rate": 0.22 },
      { "upTo": 191950, "rate": 0.24 },
      { "upTo": 243725, "rate": 0.32 },
      { "upTo": 609350, "rate": 0.35 },
      { "rate": 0.37 }
    ]
  },
  "fica": {
    "ssRate": 0.062,
    "ssWageBase": 168600,
    "medicareRate": 0.0145,
    "additionalMedicareThreshold": 200000,
    "additionalRate": 0.009
  },
  "states": {
    "MD": {
      "brackets": [
        { "upTo": 1000, "rate": 0.02 },
        { "upTo": 2000, "rate": 0.03 },
        { "upTo": 3000, "rate": 0.04 },
        { "upTo": 100000, "rate": 0.0475 },
        { "upTo": 125000, "rate": 0.05 },
        { "upTo": 150000, "rate": 0.0525 },
        { "upTo": 250000, "rate": 0.055 },
        { "rate": 0.0575 }
      ],
      "local": 0.032
    },
    "CA": {
      "brackets": [
        { "upTo": 10412, "rate": 0.01 },
        { "upTo": 24684, "rate": 0.02 },
        { "upTo": 38959, "rate": 0.04 },
        { "upTo": 54081, "rate": 0.06 },
        { "upTo": 68350, "rate": 0.08 },
        { "upTo": 349137, "rate": 0.093 },
        { "upTo": 418961, "rate": 0.103 },
        { "upTo": 698271, "rate": 0.113 },
        { "rate": 0.123 }
      ],
      "local": 0.0
    },
    "TX": {
      "brackets": [],
      "local": 0.0
    }
  }
}
EOF
echo -e "${GREEN}✅ US-2025.json created${NC}"

# Create UK rule pack
echo "📝 Creating UK-2025.json..."
cat > modules/rules-registry/src/main/resources/rulepacks/UK-2025.json << 'EOF'
{
  "metadata": {
    "country": "UK",
    "taxYear": 2025,
    "version": "UK-2025.4.0"
  },
  "incomeTax": {
    "personalAllowance": 12570,
    "taperStart": 100000,
    "taperRate": 0.5,
    "bands": [
      { "upTo": 37700, "rate": 0.20 },
      { "upTo": 125140, "rate": 0.40 },
      { "rate": 0.45 }
    ]
  },
  "ni": {
    "primaryThresholdAnnual": 12570,
    "upperEarningsLimit": 50270,
    "mainRate": 0.08,
    "upperRate": 0.02
  },
  "studentLoan": {
    "plan1": {
      "threshold": 24990,
      "rate": 0.09
    },
    "plan2": {
      "threshold": 27295,
      "rate": 0.09
    },
    "postgrad": {
      "threshold": 21000,
      "rate": 0.06
    }
  }
}
EOF
echo -e "${GREEN}✅ UK-2025.json created${NC}"

# Create application.yml
echo "📝 Creating application.yml..."
cat > modules/api/src/main/resources/application.yml << 'EOF'
spring:
  application:
    name: salary-calculator
  jackson:
    serialization:
      write-dates-as-timestamps: false
    default-property-inclusion: non_null

server:
  port: 8080
  compression:
    enabled: true

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: method

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true

logging:
  level:
    app.salary: INFO
    org.springframework.web: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
EOF
echo -e "${GREEN}✅ application.yml created${NC}"

# Build
echo ""
echo "🔨 Building project..."
./gradlew clean build --no-daemon

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Build successful!${NC}"
else
    echo -e "${RED}❌ Build failed${NC}"
    exit 1
fi

echo ""
echo "╔════════════════════════════════════════════════╗"
echo "║            Setup Complete! 🎉                 ║"
echo "╚════════════════════════════════════════════════╝"
echo ""
echo "Next steps:"
echo ""
echo "1. Run: ./gradlew :modules:api:bootRun"
echo "2. Visit: http://localhost:8080/swagger-ui.html"
echo "3. Test: curl http://localhost:8080/v1/health"
echo ""