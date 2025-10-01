# 🚀 SAM (Student Administration Manager)

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17-blue)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue.svg?logo=react)](https://reactjs.org/)

> SAM is a modern, user-centric web application for academic management at Stony Brook University, designed to replace legacy systems like SOLAR. It supports course registration, degree auditing, and schedule planning for students, instructors, advisors, and registrars.



---

##  Table of Contents

1.  [**Core Concepts**](#-core-concepts)
2.  [**Key Features**](#-key-features)
3.  [**Tech Stack & Architecture**](#-tech-stack--architecture)
4.  [**Project Structure**](#-project-structure)
5.  [**Getting Started**](#-getting-started)
6.  [**Team**](#-team)


---

##  Core Concepts

SAM is built around four fundamental concepts that model the key academic processes at a university.

-   **Session:** Manages secure, role-based access via Google SSO. After login, the UI adapts to show only the features relevant to the user's role (Student, Instructor, Advisor, or Registrar).
-   **Registration:** Governs how students add, enroll in, and drop classes. The system provides immediate validation feedback in the course cart, checking for prerequisites, time conflicts, credit limits, and registration holds before final submission.
-   **Overrides:** Provides a structured workflow for handling exceptions to registration rules. Requests for prerequisite waivers, credit overloads, or time conflicts are routed to the appropriate approvers (advisors and/or instructors) and tracked in an audit log.
-   **Degree Requirement:** Tracks a student's progress toward graduation. The system evaluates a student's academic history against university, major, and minor requirements, clearly displaying which are satisfied, in-progress, or unmet.

---

## ✨ Key Features

-   **Role-Based Dashboards:** Customized interfaces for each user role (Student, Instructor, Advisor, Registrar) to streamline relevant tasks and focus on essential information.
-   **Auto-Planner:** An intelligent course planning tool that generates optimal schedules based on a student's degree requirements, course preferences, and credit limits.
-   **Real-Time Registration Validation:** The course cart immediately flags potential issues like missing prerequisites or time conflicts, allowing students to resolve them or request an override directly.
-   **Comprehensive Student Profiles:** A centralized view of a student's academic progress, including GPA, class schedules, declared majors/minors, registration holds, and a detailed audit log of all changes.
-   **Data Management for Registrars:** Tools for registrars to import user data, course catalogs, and academic calendars, as well as manage class capacities and degree requirements.
-   **Concurrency Control:** The system is designed to handle simultaneous user actions, using techniques like row-level locking and optimistic concurrency control to prevent data inconsistencies like over-enrollment in a class.

---

##  Tech Stack & Architecture

SAM is a modern web application utilizing a robust and scalable technology stack.

| Category               | Technology                                                                                                                                                                                                                                                                                               |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Frontend** | <img src="https://img.shields.io/badge/React-61DAFB?style=for-the-badge&logo=react&logoColor=black"> <img src="https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white"> <img src="https://img.shields.io/badge/Material_UI-007FFF?style=for-the-badge&logo=mui&logoColor=white"> |
| **Backend** | <img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white"> <img src="https://img.shields.io/badge/Java-007396?style=for-the-badge&logo=java&logoColor=white">                                                                                        |
| **Database** | <img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white"> <img src="https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white">                                                                                        |
| **Authentication** | <img src="https://img.shields.io/badge/Google_OAuth_2.0-4285F4?style=for-the-badge&logo=google&logoColor=white">                                                                                                                                                                                         |
| **CI/CD & Deployment** | <img src="https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=github-actions&logoColor=white"> <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white"> <img src="https://img.shields.io/badge/Amazon_AWS-232F3E?style=for-the-badge&logo=amazon-aws&logoColor=white"> |
| **Testing** | **JUnit**, **RestAssured**, **Vitest**, **React Testing Library**, **Playwright** |

The back-end primarily uses **JPQL (Java Persistence Query Language)** with Spring Data JPA for object-oriented database queries that are independent of the underlying SQL database.

---

## 📁 Project Structure

The project is organized into a monorepo with distinct `backend` and `frontend` directories.

### Backend (Spring Boot)

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/sbu/sam/
│   │   │   ├── controller/    # REST API endpoints
│   │   │   ├── service/       # Business logic layer
│   │   │   ├── repository/    # Data access layer (JPA repositories)
│   │   │   ├── domain/        # JPA entities (data models)
│   │   │   ├── dto/           # Data Transfer Objects
│   │   │   ├── config/        # Spring Security, CORS, etc.
│   │   │   └── SamApplication.java # Main application entry point
│   │   └── resources/
│   │       ├── application.yml   # Main application configuration
│   │       └── db/migration/     # Database schema migrations (Flyway)
│   └── test/
│       └── java/com/sbu/sam/    # Unit and integration tests
├── .gitignore
├── pom.xml                     # Maven project configuration
└── README.md
```

### Frontend (React)

```
frontend/
├── public/
│   ├── index.html              # HTML template
│   └── favicon.ico
├── src/
│   ├── assets/                 # Static assets (images, fonts, global CSS)
│   ├── components/             # Reusable UI components (e.g., Button, Modal)
│   │   └── common/
│   ├── hooks/                  # Custom React hooks (e.g., useAuth)
│   ├── layouts/                # Main layout components (e.g., DashboardLayout)
│   ├── pages/                  # Top-level page components (e.g., LoginPage, RegisterPage)
│   ├── services/               # API client functions (e.g., courseApi.ts)
│   ├── store/                  # State management (e.g., Redux, Zustand)
│   ├── types/                  # TypeScript type definitions
│   ├── utils/                  # Utility functions
│   ├── App.tsx                 # Root application component and router setup
│   └── index.tsx               # Application entry point
├── .gitignore
├── package.json
├── tsconfig.json               # TypeScript configuration
└── vite.config.ts              # Vite build tool configuration
```

---

## ⚙️ Getting Started

### Prerequisites

-   Java 17+
-   Node.js & npm
-   MySQL
-   Docker

### Installation & Setup

1.  **Clone the repository:**
    ```sh
    git clone [https://github.com/](https://github.com/)[Your-GitHub-Username]/sam-project.git
    cd sam-project
    ```
2.  **Configure Backend:**
    Update `backend/src/main/resources/application.yml` with your local MySQL database credentials.
3.  **Run the application (using Docker Compose):**
    ```sh
    docker-compose up --build
    ```
4.  Access the application at `http://localhost:3000`.

---

## 👨‍👩‍👧‍👦 Team (404 found)

| Position  | Name          |
| --------- | ------------- |
| Member    | Minseo Kim    |
| Member    | Jaeseong Yoon |
| Member    | Sihoon Sung   |
| Member    | Seongbeen Na  |



---

