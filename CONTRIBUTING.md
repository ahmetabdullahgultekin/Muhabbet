# Contributing to Muhabbet

Thank you for considering contributing to Muhabbet! This document outlines the process and guidelines for contributing.

## Getting Started

### Prerequisites
- JDK 21 (the Gradle toolchain pins it — see `backend/build.gradle.kts`)
- Android SDK, `compileSdk` 36 / `minSdk` 26
- Docker & Docker Compose (for backend services **and for the backend test suite** — without them
  ten Testcontainers classes fail at start-up and are silently not executed)
- Kotlin 2.4.10 and Gradle 9.7.0, both supplied by the wrapper

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

Read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) before your first change — it explains the module
boundaries and the rules that the layout exists to enforce.

### Code Standards
Please read `CLAUDE.md` for detailed coding standards. Key points:
- Follow SOLID principles strictly
- No hardcoded strings in UI — use `stringResource(Res.string.*)`
- No `!!` (non-null assertion) — handle nulls properly
- Domain layer must be framework-agnostic
- All REST responses use the envelope pattern

### Branching Strategy
- `main` — stable release branch; batched from `dev` at release time
- `dev` — the working branch. **There is no `develop`** — the name appears in some workflow triggers
  and matches nothing, which is why no CI ran on PRs into `dev` at all until #487.
- `feat/*`, `fix/*`, `docs/*`, `ci/*`, `chore/*` — working branches, cut from `dev`

### Commit Messages
**Not conventional commits.** A commit message says what the *user* experienced, in a plain
sentence, with the issue number:

```
Deliver the messages that Redis was silently dropping (#412)
Stop an ampersand in a search from silently truncating it (#622)
```

not `fix(redis): channel parsing`. The prefix describes the code; the sentence describes the defect,
and the defect is the thing anyone reading the history later is looking for.

### Pull Request Process
1. Cut a branch from `dev`
2. Make your changes following the code standards
3. Run the gates — `./gradlew :backend:test :shared:jvmTest` with Docker up
4. Open the PR **against `dev`**, with a description of what a user would have noticed
5. Wait for review

Before calling anything user-visible done, check all three: does it **persist**, does anything
**read** it, and does a **mechanism** exist? The characteristic defect in this codebase is a control
that is drawn and wired to nothing, and it compiles perfectly. See
[`ROADMAP.md`](ROADMAP.md#definition-of-done).

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
