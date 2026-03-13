# Muhabbet

Privacy-first messaging platform built for Turkey, with KVKK compliance and Kotlin across backend, mobile, and shared modules.

## Readme Languages

- English: [`README.en.md`](README.en.md)
- Türkçe: [`README.tr.md`](README.tr.md)

## Quick Links

- Product roadmap: [`ROADMAP.md`](ROADMAP.md)
- Changelog: [`CHANGELOG.md`](CHANGELOG.md)
- API contract: [`docs/api-contract.md`](docs/api-contract.md)
- QA docs: [`docs/qa/`](docs/qa/)

## Repository Structure

```text
muhabbet/
├── backend/   # Spring Boot + Kotlin modular monolith
├── shared/    # Kotlin Multiplatform shared contracts/models
├── mobile/    # Compose Multiplatform client (Android + iOS)
├── infra/     # Docker Compose, nginx, deploy scripts
└── docs/      # API, architecture, decisions, QA
```
