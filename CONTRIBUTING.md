# Contributing to Muhabbet

Thank you for considering contributing to Muhabbet! This document outlines the process and guidelines for contributing.

## Getting Started

### Prerequisites
- JDK 25
- Android SDK (API 35)
- Docker & Docker Compose (for backend services)
- Kotlin 2.3.10+ (managed by Gradle wrapper)

### Local Development Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/ahmetabdullahgultekin/Muhabbet.git
   cd Muhabbet
   ```

2. **Start backend services:**
   ```bash
   cd infra
   docker compose up -d
   ```

3. **Run the backend:**
   ```bash
   cd backend
   ../gradlew bootRun
   ```

4. **Run the mobile app:**
   ```bash
   cd mobile
   ../gradlew :composeApp:installDebug
   ```

## Development Guidelines

### Architecture
- **Backend:** Modular monolith with Hexagonal Architecture (Ports & Adapters)
- **Mobile:** Compose Multiplatform with Clean Architecture
- **Shared:** Kotlin Multiplatform module for DTOs, models, and protocol

### Code Standards
Please read `CLAUDE.md` for detailed coding standards. Key points:
- Follow SOLID principles strictly
- No hardcoded strings in UI — use `stringResource(Res.string.*)`
- No `!!` (non-null assertion) — handle nulls properly
- Domain layer must be framework-agnostic
- All REST responses use the envelope pattern

### Branching Strategy
- `main` — stable release branch
- `develop` — integration branch
- `feature/*` — feature branches
- `fix/*` — bug fix branches

### Commit Messages
Use conventional commits:
```
feat: add group voice calls
fix: resolve message ordering in chat
refactor: extract MessageMapper for DRY compliance
docs: update API contract for polls endpoint
```

### Pull Request Process
1. Create a feature branch from `develop`
2. Make your changes following the code standards
3. Ensure all tests pass
4. Submit a PR with a clear description
5. Wait for code review

## Testing

### Backend
```bash
cd backend
../gradlew test
```

### Mobile
```bash
cd mobile
../gradlew :composeApp:testDebugUnitTest
```

## Reporting Issues
- Use GitHub Issues for bug reports and feature requests
- Include reproduction steps for bugs
- For security vulnerabilities, see [SECURITY.md](SECURITY.md)

## License
By contributing, you agree that your contributions will be licensed under the MIT License.
