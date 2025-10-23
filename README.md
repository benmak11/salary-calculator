# Salary Calculator Microservice

A production-ready Spring Boot microservice for calculating net pay (take-home salary) with detailed tax breakdowns for multiple countries.

## 🌟 Features

- 🌍 **Multi-country support** (US, UK - easily extensible)
- 💰 **Multiple pay cadences** (annual, monthly, biweekly, weekly)
- 📊 **Detailed tax breakdown** by bands
- 📝 **Human-readable explanations** for each calculation
- 🔄 **Auto-discovery** of country calculators
- 🚀 **Fast development** - add new country in 15 minutes
- 📦 **Shared utilities** for code reuse
- 🐳 **Docker ready**
- 📈 **Production monitoring** (Prometheus, health checks)

## 🏗️ Architecture

### Unified Calculator Module
- All country calculators in one module
- Shared utilities for common logic
- Auto-discovery via Spring
- No manual registration needed

### Supported Countries
- 🇺🇸 **United States** (Federal + State taxes, FICA, Medicare)
- 🇬🇧 **United Kingdom** (Income Tax, National Insurance, Student Loans, Pension)

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Docker (optional)

### Option 1: Using Setup Script
```bash
chmod +x setup.sh
./setup.sh
```

### Option 2: Manual Setup
```bash
# Build
./gradlew clean build

# Run
./gradlew :modules:api:bootRun
```

### Option 3: Docker
```bash
docker-compose up -d
```

## 📡 API Endpoints

### Calculate Salary
```bash
POST /v1/calculate
```

### Health Check
```bash
GET /v1/health
```

### List Supported Countries
```bash
GET /v1/countries
```

### API Documentation