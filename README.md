# SAM Academic Management System

SAM is a team academic management and student administration system built for a CSE 416 course project. It models role-based academic workflows for students, instructors, advisors, and registrars, including course registration, degree planning, degree requirement review, schedule planning, and administrative data management.

## Core Workflows

- **Role-based access:** Google OAuth integration and role-aware UI/backend flows for student, instructor, advisor, and registrar workflows.
- **Course registration:** Course search, cart behavior, prerequisite/time-conflict checks, registration holds, and waitlist-related services.
- **Degree planning:** Degree requirement import, degree audit/status views, planner logic, and major/minor declaration workflows.
- **Override workflows:** Prerequisite, corequisite, time-conflict, and degree-requirement waiver flows with advisor/instructor/registrar-oriented handling.
- **Administration:** Course catalog, class roster, registration schedule, academic calendar, room, profile, and user-management features.

## Tech Stack

| Area | Evidence-backed technologies |
| --- | --- |
| Frontend | React, TypeScript, Vite, React Router |
| Backend | Java, Spring Boot, Spring Web, Spring Security |
| Persistence | Spring Data JPA, MySQL runtime configuration, H2 for tests |
| Authentication | Google OAuth2 client integration |
| Data/import tooling | YAML parsing, PDFBox, jsoup |
| Testing | JUnit/Spring Boot tests, Jest/React Testing Library, Playwright API tests |

## Project Structure

```txt
backend/
├── pom.xml
├── package.json
├── playwright.config.ts
└── src/
    ├── main/java/edu/sbu/cse416/sam_system/
    │   ├── config/
    │   ├── controller/
    │   ├── dto/
    │   ├── entity/
    │   ├── repository/
    │   ├── security/
    │   ├── service/
    │   └── util/
    └── test/

frontend/
├── package.json
├── vite.config.ts
└── src/
    ├── components/
    ├── pages/
    ├── types/
    └── utils/
```

## Backend

The backend is a Spring Boot application with controllers, services, repositories, JPA entities, DTOs, security configuration, OAuth handling, YAML/PDF parsing support, and focused service tests.

Common local commands:

```bash
cd backend
./mvnw test
./mvnw spring-boot:run
```

The backend expects local database and OAuth settings in `backend/src/main/resources/application.properties`. Do not commit real credentials.

## Frontend

The frontend is a React/Vite application with TypeScript pages for dashboards, login, course search, course registration, degree audit, planner, profile, roster, grades, data import, and administration workflows.

Common local commands:

```bash
cd frontend
npm install
npm run dev
npm run test
npm run build
```

## Testing

The repository includes:

- Spring Boot/JUnit tests under `backend/src/test`
- Playwright API tests under `backend/tests`
- Jest/React Testing Library setup and frontend tests under `frontend/src`

## Known Limitations

- This is an academic team project, not a production deployment.
- The repository does not include a supported Docker Compose deployment path.
- AWS deployment, Flyway migrations, and Material UI are not claimed here because they are not supported by the current repository evidence.
- Local setup requires configuring MySQL and Google OAuth values for the backend environment.

## Team

| Role | Name |
| --- | --- |
| Member | Minseo Kim |
| Member | Jaeseong Yoon |
| Member | Sihoon Sung |
| Member | Seongbeen Na |
