# 🚀 SAM (Student Administration Manager)

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17-blue)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue.svg?logo=react)](https://reactjs.org/)

> SAM is a modern, user-centric web application for academic management at Stony Brook University, designed to replace legacy systems like SOLAR. It supports course registration, degree auditing, and schedule planning for students, instructors, advisors, and registrars.



---

## 📝 Table of Contents

1.  [**Core Concepts**](#-core-concepts)
2.  [**Key Features**](#-key-features)
3.  [**Tech Stack & Architecture**](#-tech-stack--architecture)
4.  [**Project Structure**](#-project-structure)
5.  [**Auto-Planner Algorithm**](#-auto-planner-algorithm)
6.  [**API Endpoints**](#-api-endpoints)
7.  [**Getting Started**](#-getting-started)
8.  [**Team**](#-team)
9.  [**License**](#-license)

---

## 🏛️ Core Concepts

SAM is built around four fundamental concepts that model the key academic processes at a university.

-   **Session:** Manages secure, role-based access via Google SSO. [cite_start]After login, the UI adapts to show only the features relevant to the user's role (Student, Instructor, Advisor, or Registrar)[cite: 847, 862].
-   [cite_start]**Registration:** Governs how students add, enroll in, and drop classes[cite: 888]. [cite_start]The system provides immediate validation feedback in the course cart, checking for prerequisites, time conflicts, credit limits, and registration holds before final submission[cite: 902, 915].
-   [cite_start]**Overrides:** Provides a structured workflow for handling exceptions to registration rules[cite: 936]. [cite_start]Requests for prerequisite waivers, credit overloads, or time conflicts are routed to the appropriate approvers (advisors and/or instructors) and tracked in an audit log[cite: 952, 953].
-   [cite_start]**Degree Requirement:** Tracks a student's progress toward graduation[cite: 982]. [cite_start]The system evaluates a student's academic history against university, major, and minor requirements, clearly displaying which are satisfied, in-progress, or unmet[cite: 982, 983].

---

## ✨ Key Features

-   [cite_start]**Role-Based Dashboards:** Customized interfaces for each user role (Student, Instructor, Advisor, Registrar) to streamline relevant tasks and focus on essential information[cite: 63, 51].
-   [cite_start]**Auto-Planner:** An intelligent course planning tool that generates optimal schedules based on a student's degree requirements, course preferences, and credit limits[cite: 449].
-   [cite_start]**Real-Time Registration Validation:** The course cart immediately flags potential issues like missing prerequisites or time conflicts, allowing students to resolve them or request an override directly[cite: 59, 60].
-   [cite_start]**Comprehensive Student Profiles:** A centralized view of a student's academic progress, including GPA, class schedules, declared majors/minors, registration holds, and a detailed audit log of all changes[cite: 399, 413].
-   [cite_start]**Data Management for Registrars:** Tools for registrars to import user data, course catalogs, and academic calendars, as well as manage class capacities and degree requirements[cite: 359, 368, 383, 391].
-   [cite_start]**Concurrency Control:** The system is designed to handle simultaneous user actions, using techniques like row-level locking and optimistic concurrency control to prevent data inconsistencies like over-enrollment in a class[cite: 1491, 1511, 1513].

---

## 🛠️ Tech Stack & Architecture

[cite_start]SAM is a modern web application utilizing a robust and scalable technology stack[cite: 1522, 1523, 1529].

| Category               | Technology                                                                                                                                                                                                                                                                                               |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Frontend** | <img src="https://img.shields.io/badge/React-61DAFB?style=for-the-badge&logo=react&logoColor=black"> <img src="https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white"> <img src="https://img.shields.io/badge/Material_UI-007FFF?style=for-the-badge&logo=mui&logoColor=white"> |
| **Backend** | <img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white"> <img src="https://img.shields.io/badge/Java-007396?style=for-the-badge&logo=java&logoColor=white">                                                                                        |
| **Database** | <img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white"> <img src="https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white">                                                                                        |
| **Authentication** | <img src="https://img.shields.io/badge/Google_OAuth_2.0-4285F4?style=for-the-badge&logo=google&logoColor=white">                                                                                                                                                                                         |
| **CI/CD & Deployment** | <img src="https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=github-actions&logoColor=white"> <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white"> <img src="https://img.shields.io/badge/Amazon_AWS-232F3E?style=for-the-badge&logo=amazon-aws&logoColor=white"> |
| **Testing** | [cite_start]**JUnit**, **RestAssured**, **Vitest**, **React Testing Library**, **Playwright** [cite: 1525, 1526]                                                                                                                                                                                                      |

[cite_start]The back-end primarily uses **JPQL (Java Persistence Query Language)** with Spring Data JPA for object-oriented database queries that are independent of the underlying SQL database[cite: 1109, 1110].

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

## 🤖 Auto-Planner Algorithm

[cite_start]The core of the auto-planner is a **hybrid algorithm** that combines **Dynamic Programming (DP)** with **Beam Search**[cite: 72, 485]. [cite_start]It treats the course selection challenge as a variation of the **0/1 Knapsack problem**, where the goal is to maximize student preferences ("value") without exceeding credit limits ("capacity") or violating constraints[cite: 73, 486].

-   [cite_start]**Hard Constraints:** Prerequisites, time conflicts, and major policies are treated as absolute rules that filter out impossible options[cite: 74, 487].
-   [cite_start]**Soft Constraints:** Student preferences (e.g., desired courses, preferred time slots) are converted into a numerical score to guide the search[cite: 75, 488].
-   [cite_start]**Efficiency:** To avoid a combinatorial explosion of possible schedules, the algorithm uses a **beam search** to "prune" the search space at each step of the DP process[cite: 77, 490]. [cite_start]It keeps only the top-`T` most promising partial schedules, ensuring a near-optimal solution can be found efficiently[cite: 78, 491, 492].

---

## 📡 API Endpoints

[cite_start]The back-end is organized into four main API sections[cite: 1114]. All communication uses JSON.

### Course API

[cite_start]Handles the general course catalog[cite: 1117].

-   [cite_start]`GET /api/courses/{course_id}`: Retrieves details for a specific course[cite: 1119].
-   [cite_start]`POST /api/courses`: Allows a registrar to upload the entire course catalog[cite: 1138, 1140].

### Class API

[cite_start]Manages specific course offerings for a given term[cite: 1198].

-   `GET /api/classes/{class_id}`: Retrieves details for a specific class section.
-   [cite_start]`PUT /api/classes/{class_id}/capacity`: Allows a registrar to edit class capacity[cite: 1228].
-   [cite_start]`POST /api/classes/{class_id}/grades`: Allows an instructor to submit grades[cite: 1248].

### Student API

[cite_start]Manages all student-specific data and academic plans[cite: 1326].

-   [cite_start]`GET /api/students/{studentId}/profile`: Retrieves a student's complete profile[cite: 1327].
-   [cite_start]`GET /api/students/{studentId}/plan`: Retrieves a student's course plan[cite: 1346].
-   [cite_start]`PUT /api/students/{studentId}/plan`: Updates a student's course plan[cite: 1398].
-   [cite_start]`GET /api/students/{studentId}/class-schedule`: Retrieves a student's full class history[cite: 1431].

### Audit Log API

[cite_start]Provides access to logged historical changes[cite: 1472].

-   [cite_start]`GET /api/students/{studentId}/audit-log`: Retrieves the profile change history for a student[cite: 1473].

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

| Role      | Name          |
| --------- | ------------- |
| Backend   | Minseo Kim    |
| Backend   | Jaeseong Yoon |
| Frontend  | Sihoon Sung   |
| Frontend  | Seongbeen Na  |

[cite_start]*(Members listed as per design documents [cite: 4, 482])*

---

## 📄 License

This project is licensed under the **MIT License**.