# AGENTS.md

This file provides guidance to Qoder (qoder.com) when working with code in this repository.

## Project Overview

**AI Podcast Workshop (AI 播客工坊)** is an AI podcast generation platform built on top of iFlyTek's AstronAgent. It converts text content into podcast-style scripts using DeepSeek LLM and generates high-quality audio using iFlyTek's ultra-realistic voice synthesis technology through a visual workflow orchestration system.

## Build & Test Commands

### Frontend (React + TypeScript + Vite)
```bash
cd console/frontend
npm install              # Install dependencies
npm run dev             # Start dev server (development mode)
npm run test            # Start dev server on localhost (test mode)
npm run build           # Build for production
npm run build:dev       # Build for development
npm run build:test      # Build for test environment
npm run format          # Format code with Prettier
npm run format:check    # Check formatting
npm run lint            # Lint with ESLint
npm run lint:fix        # Auto-fix linting issues
npm run type-check      # TypeScript type checking
npm run quality         # Run all quality checks (format:check + lint + type-check)
```

### Backend (Java 21 + Spring Boot + Maven)
```bash
cd console/backend
mvn clean install       # Build all modules
mvn spring-boot:run -pl hub  # Run hub service
mvn test                # Run tests
mvn spotless:apply      # Format code
mvn spotless:check      # Check code formatting
mvn checkstyle:check    # Check code style
mvn spotbugs:check      # Run static analysis
mvn pmd:check           # Run PMD checks
```

### Python Core Services
```bash
# Workflow Engine
cd core/workflow
pytest                  # Run tests with pytest
pytest --cov           # Run tests with coverage
python main.py         # Start workflow service (FastAPI + uvicorn)

# Link Plugin (Tool Integration)
cd core/plugin/link
pytest                  # Run tests

# AI Tools Plugin (Voice Synthesis)
cd core/plugin/aitools
pytest                  # Run tests

# RPA Plugin
cd core/plugin/rpa
pytest                  # Run tests

# Common Utilities
cd core/common
pytest                  # Run tests
```

### Go Tenant Service
```bash
cd core/tenant
go test ./...           # Run tests
go build               # Build binary
```

### Multi-Language Makefile (Recommended)
```bash
make setup             # One-time environment setup (tools + hooks + branch strategy)
make format            # Format all code (Go, Java, Python, TypeScript)
make check             # Quality checks for all languages
make test              # Run all tests
make build             # Build all projects
make push              # Safe push with pre-checks
make clean             # Clean build artifacts
make status            # Show project status
make info              # Show tool versions
make ci                # Complete CI pipeline (format + check + test + build)
```

For local development with specific modules, create `.localci.toml` from `makefiles/localci.toml` and set `enabled = true` only for modules you're working on.

## Architecture Overview

### Service Architecture
The project follows a microservices architecture with multiple language-specific services:

1. **Console (Java + TypeScript)**
   - `console/backend/hub` - Main API service (Spring Boot, port 8080)
   - `console/backend/commons` - Shared DTOs and utilities
   - `console/backend/toolkit` - Additional tools and MCP server management
   - `console/frontend` - React web UI (Vite dev server on port 1881, Nginx on port 80)

2. **Core Workflow (Python)**
   - `core/workflow` - FastAPI-based workflow orchestration engine (port 7880)
   - Executes visual workflows with AI nodes and voice synthesis nodes
   - Uses SQLAlchemy + Redis for state management

3. **Core Plugins (Python)**
   - `core/plugin/link` - Tool integration and MCP server connector
   - `core/plugin/aitools` - AI capabilities including iFlyTek voice synthesis (port 18668)
   - `core/plugin/rpa` - Robotic Process Automation capabilities

4. **Core Services (Python)**
   - `core/agent` - Agent orchestration and management
   - `core/common` - Shared utilities (OpenTelemetry tracing, logging, config)
   - `core/knowledge` - Knowledge base and RAG capabilities
   - `core/memory` - Memory and database abstractions

5. **Tenant Service (Go)**
   - `core/tenant` - Multi-tenant management service using Gin framework

6. **Core Workflow Java**
   - `core-workflow-java` - Java-based workflow engine implementation

### Key Technologies
- **Frontend**: React 18, TypeScript 5.9, Vite 5.4, Ant Design 5.19, Recoil/Zustand, React Flow
- **Backend**: Spring Boot 3.5.4, Java 21, MyBatis Plus 3.5.7, Spring Security OAuth2
- **Workflow**: FastAPI 0.111, Python 3.11+, Pydantic 2.9, uvicorn
- **Database**: PostgreSQL (workflow data), MySQL (tool metadata), Redis (caching)
- **Storage**: MinIO (object storage)
- **AI Integration**: DeepSeek (LLM), iFlyTek Spark (voice synthesis, SDK 2.1.5), OpenAI SDK
- **Observability**: OpenTelemetry (tracing), Loguru (logging)

### Data Flow - Podcast Generation
```
User Input (Text) 
  → Frontend (React) 
  → Console Hub (Spring Boot) 
  → Workflow Engine (FastAPI) 
  → DeepSeek LLM (script rewriting) 
  → AI Tools Plugin (iFlyTek voice synthesis) 
  → MinIO (audio storage) 
  → Frontend (audio playback)
```

### Database Schema
- **PostgreSQL**: Stores workflow definitions, execution states, user configurations
- **MySQL**: 
  - `astron_console.tool_box` - Tool registry
  - `spark-link.tools_schema` - Tool schema definitions (must have version='V1.0', correct app_id)

## Development Patterns

### Adding a New Workflow Node
1. Define node type in `core/workflow/domain/` 
2. Implement node executor in `core/workflow/engine/`
3. Register node in workflow engine configuration
4. Add UI component in `console/frontend/src/components/`
5. Update workflow builder in frontend

### Frontend Conventions
- Use React hooks and functional components
- State management: Recoil for global state, Zustand for specific stores
- Routing: React Router v6 with route-based code splitting
- API calls: Centralized in `src/services/`
- i18n: Use react-i18next, add keys to `src/locales/`
- Styling: Tailwind CSS + Ant Design components

### Backend Conventions
- Follow Spring Boot best practices with layered architecture (Controller → Service → Repository)
- Use Lombok for boilerplate reduction
- MapStruct for object mapping
- Code quality enforced by Spotless, Checkstyle, SpotBugs, PMD
- API documentation with SpringDoc OpenAPI

### Python Service Conventions
- FastAPI for async web services
- Pydantic for data validation and settings
- Use Loguru for logging, not print()
- Type hints required (checked by mypy)
- Format with black + isort, lint with flake8 + pylint
- Test with pytest + pytest-asyncio

## Docker Deployment

### Quick Start
```bash
cd docker/astronAgent
cp .env.example .env
docker compose up -d
docker compose ps        # Check service status
docker compose logs -f   # View logs
```

### Access Points
- Frontend: http://localhost (Nginx)
- Default credentials: admin / 123
- MinIO Console: http://localhost:18998

### Service Ports
- console-hub: 8080
- console-frontend: 1881 (internal)
- core-workflow: 7880
- core-aitools: 18668
- nginx: 80
- postgres: 5432
- mysql: 3306
- redis: 6379
- minio: 18998 (console), 18999 (API)

### Common Issues

**Workflow execution fails:**
1. Check tool version in MySQL: `tools_schema.version` must be 'V1.0'
2. Verify service address: should be `http://core-aitools:18668` (not https, not 18669)
3. Ensure `app_id` matches between tool and workflow

Fix command:
```bash
docker compose exec mysql mysql -uroot -proot123 spark-link -e "
UPDATE tools_schema 
SET version='V1.0', 
    app_id='680ab54f',
    open_api_schema = REPLACE(open_api_schema, 'https://core-aitools:18669', 'http://core-aitools:18668')
WHERE tool_id='tool@8b2262bef821000';"

docker compose restart core-link core-workflow
```

**502 Bad Gateway after container restart:**
```bash
docker compose restart nginx
```

## Code Quality Standards

### Required Before Commit
Run quality checks before committing:
```bash
# Frontend
npm run quality          # Must pass

# Backend
mvn spotless:apply       # Auto-format first
mvn verify              # Must pass

# Python services
black . && isort .      # Format
pytest                  # Tests must pass
flake8 && mypy .       # Linting must pass

# Or use unified Makefile
make format && make check && make test
```

### Git Workflow
- Use feature branches: `feature/description`, `bugfix/issue-number`
- Commit messages: Follow conventional commits format
- Pre-commit hooks available via `make hooks-install`
- Safe push: `make push` (runs format + check before push)

## Important Notes

- **Python Version**: Core services require Python 3.11+ (workflow engine, plugins)
- **Java Version**: Backend requires Java 21 (managed via jenv)
- **Node Version**: Frontend requires Node.js 18+
- **Go Version**: Tenant service requires Go 1.23+

- **DO NOT** hardcode service URLs - use environment variables from `config.env` files
- **DO NOT** commit `.env` files with secrets
- **DO NOT** modify `tools_schema` version manually - use provided SQL script
- **ALWAYS** use the Makefile for multi-language operations to ensure consistency

## Workflow Dual-Version Architecture (Java Development)

### 🎯 Overview

The project supports **two independent Workflow implementations** running side-by-side:
- **Python Version** (Port 7880) - Stable production baseline, always kept functional
- **Java Version** (Port 7881) - Development version, can be modified freely

### 🔧 Quick Commands

```bash
# Restart Java Workflow after code changes (auto-compile, build, restart)
./scripts/restart-java-workflow.sh

# Switch to Python version (stable reference)
./scripts/switch-to-python.sh

# Switch to Java version (development)
./scripts/switch-to-java.sh

# Compare outputs between versions
./scripts/compare-workflows.sh 184736
```

### 📋 Development Workflow

1. **Develop**: Modify Java code in `core-workflow-java/`
2. **Test**: Run `./scripts/restart-java-workflow.sh`
3. **Debug**: If errors occur, run `./scripts/switch-to-python.sh` to reference Python implementation
4. **Compare**: Use `./scripts/compare-workflows.sh` to validate consistency

### 🗄️ Database Isolation

- **Python DB**: `workflow_python` (read-only, stable baseline)
- **Java DB**: `workflow_java` (development, can be modified)

### 🔄 Version Switching

**Switch to Python (when Java has issues):**
```bash
./scripts/switch-to-python.sh
# Python version now handles all requests
# Review Python logs: docker logs -f astron-agent-core-workflow-python
```

**Switch back to Java:**
```bash
./scripts/restart-java-workflow.sh
# Compiles, builds, and restarts Java version
```

### ⚠️ Critical Rules

1. **NEVER modify Python version code** - It's the reference baseline
2. **ALWAYS use restart script** after Java code changes
3. **IMMEDIATELY switch to Python** if Java version fails
4. **Python version must remain functional** at all times

### 📊 Comparison Testing

```bash
# Test same workflow on both versions
./scripts/compare-workflows.sh 184736

# View results
cat /tmp/python-workflow-response.json
cat /tmp/java-workflow-response.json
```

### 🚨 Emergency Rollback

If Java version completely breaks:
```bash
./scripts/switch-to-python.sh  # Instant rollback to stable version
```

### 📁 Java Project Structure

```
core-workflow-java/
├── src/main/java/com/iflytek/astron/workflow/
│   ├── WorkflowApplication.java    # Main entry
│   ├── controller/                 # REST APIs
│   ├── service/                    # Business logic
│   ├── engine/                     # Workflow engine
│   ├── nodes/                      # Node implementations
│   │   ├── StartNode.java
│   │   ├── EndNode.java
│   │   ├── LLMNode.java
│   │   └── PluginNode.java
│   └── domain/                     # Domain models
├── src/main/resources/
│   └── application.yml
├── pom.xml
├── Dockerfile
└── README.md
```

### 🔍 Debugging Tips

**View Java logs:**
```bash
docker logs -f astron-agent-core-workflow-java
```

**View Python logs (for reference):**
```bash
docker logs -f astron-agent-core-workflow-python
```

**Health checks:**
```bash
curl http://localhost:7881/actuator/health  # Java
curl http://localhost:7880/health           # Python
```

### 📚 References

- **Python Implementation**: `core/workflow/` - Reference for Java development
- **Java Implementation**: `core-workflow-java/` - Development version
- **Scripts Guide**: `scripts/README.md` - Detailed script usage
- **Java README**: `core-workflow-java/README.md` - Java-specific guide
