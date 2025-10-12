# SBU Academics Management - User Management System

## 개요 (Overview)

성시훈이 담당하는 사용자 관리 및 인증 시스템입니다. 이 시스템은 4가지 사용자 역할(학생, 강사, 어드바이저, 교무처장)을 지원하며, Google OAuth2를 통한 안전한 인증을 제공합니다.

## 주요 기능 (Key Features)

### 1. 사용자 역할 정의 (User Role Definition)
- **STUDENT**: 학생
- **INSTRUCTOR**: 강사
- **ADVISOR**: 어드바이저
- **REGISTRAR**: 교무처장

### 2. Google 계정 인증 (Google Account Authentication)
- SBU Google 계정을 통한 로그인/로그아웃
- 테스트 목적으로 @stonybrook.edu 이메일 제한 해제
- OAuth2 기반 안전한 인증

### 3. 사용자 데이터 가져오기 (User Data Import)
- 교무처장이 YAML 파일을 통한 사용자 데이터 일괄 등록
- 샘플 YAML 파일 제공 (`sample_users.yaml`)

### 4. 사용자 검색 기능 (User Search Function)
- 이름, 역할, 이메일, 전공/학과별 검색
- 교무처장 전용 사용자 관리 페이지

## 기술 스택 (Technology Stack)

### Backend
- **Spring Boot 3.5.6**
- **Spring Security** with OAuth2 Client
- **Spring Data JPA**
- **MySQL** Database
- **SnakeYAML** for YAML processing

### Frontend
- **React 19.1.1**
- **TypeScript**
- **React Router DOM**
- **Vite**

## 설정 방법 (Setup Instructions)

### 1. Google OAuth2 설정

1. [Google Cloud Console](https://console.cloud.google.com/)에서 프로젝트 생성
2. OAuth2 클라이언트 ID 생성
3. 승인된 리디렉션 URI 추가: `http://localhost:8080/login/oauth2/code/google`
4. 환경 변수 설정:

```bash
export GOOGLE_CLIENT_ID="your-google-client-id"
export GOOGLE_CLIENT_SECRET="your-google-client-secret"
```

또는 `application.properties`에서 직접 설정:

```properties
spring.security.oauth2.client.registration.google.client-id=your-google-client-id
spring.security.oauth2.client.registration.google.client-secret=your-google-client-secret
```

### 2. 데이터베이스 설정

MySQL 데이터베이스가 실행 중인지 확인하고, `application.properties`에서 연결 정보를 확인하세요:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/sam
spring.datasource.username=root
spring.datasource.password=your-password
```

### 3. 백엔드 실행

```bash
cd backend
./mvnw spring-boot:run
```

### 4. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

## API 엔드포인트 (API Endpoints)

### 인증 (Authentication)
- `GET /oauth2/authorization/google` - Google 로그인 시작
- `GET /api/auth/success` - 로그인 성공 처리
- `GET /api/auth/failure` - 로그인 실패 처리
- `GET /api/auth/me` - 현재 사용자 정보
- `POST /api/auth/logout` - 로그아웃
- `GET /api/auth/status` - 인증 상태 확인

### 사용자 관리 (User Management) - 교무처장 전용
- `GET /api/users` - 모든 사용자 조회
- `GET /api/users/{userId}` - 특정 사용자 조회
- `GET /api/users/search` - 사용자 검색
- `GET /api/users/role/{role}` - 역할별 사용자 조회
- `PUT /api/users/{userId}` - 사용자 정보 수정
- `PUT /api/users/{userId}/activate` - 사용자 활성화
- `PUT /api/users/{userId}/deactivate` - 사용자 비활성화
- `POST /api/users/import` - YAML 파일로 사용자 일괄 등록
- `GET /api/users/statistics` - 사용자 통계

## YAML 파일 형식 (YAML File Format)

사용자 데이터를 일괄 등록하기 위한 YAML 파일 형식:

```yaml
users:
  - email: john.doe@stonybrook.edu
    firstName: John
    lastName: Doe
    role: STUDENT
    studentId: "123456789"
    major: "Computer Science"
    gpa: 3.5
    graduationYear: 2025

  - email: prof.johnson@stonybrook.edu
    firstName: Michael
    lastName: Johnson
    role: INSTRUCTOR
    department: "Computer Science"
    officeLocation: "Computer Science Building, Room 123"
    officeHours: "Mon/Wed 2:00 PM - 4:00 PM"
```

### 필수 필드 (Required Fields)
- `email`: 이메일 주소
- `firstName`: 이름
- `lastName`: 성
- `role`: 사용자 역할 (STUDENT, INSTRUCTOR, ADVISOR, REGISTRAR)

### 선택적 필드 (Optional Fields)
- `studentId`: 학번 (학생용)
- `major`: 전공 (학생용)
- `gpa`: 학점 (학생용)
- `graduationYear`: 졸업 예정년도 (학생용)
- `department`: 학과 (강사/어드바이저용)
- `officeLocation`: 사무실 위치 (강사용)
- `officeHours`: 상담 시간 (강사용)

## 사용 방법 (Usage)

### 1. 로그인
1. 프론트엔드에서 "Login with SBU Google Account" 버튼 클릭
2. Google 계정으로 로그인
3. 시스템에서 자동으로 사용자 생성 또는 기존 사용자 정보 업데이트

### 2. 사용자 관리 (교무처장)
1. 네비게이션 바에서 "User Management" 클릭
2. 사용자 검색, 필터링, 통계 확인
3. YAML 파일 업로드를 통한 사용자 일괄 등록
4. 개별 사용자 활성화/비활성화

### 3. 사용자 데이터 일괄 등록
1. `sample_users.yaml` 파일을 참고하여 사용자 데이터 작성
2. User Management 페이지에서 파일 업로드
3. 성공 메시지 확인

## 보안 고려사항 (Security Considerations)

1. **OAuth2 인증**: Google OAuth2를 통한 안전한 인증
2. **역할 기반 접근 제어**: 교무처장만 사용자 관리 기능 접근 가능
3. **CORS 설정**: 프론트엔드 도메인만 허용
4. **세션 관리**: 안전한 로그인/로그아웃 처리

## 문제 해결 (Troubleshooting)

### 1. Google OAuth2 오류
- Google Cloud Console에서 클라이언트 ID와 시크릿 확인
- 리디렉션 URI가 정확한지 확인
- 환경 변수 설정 확인

### 2. 데이터베이스 연결 오류
- MySQL 서버 실행 상태 확인
- 데이터베이스 연결 정보 확인
- 데이터베이스 스키마 생성 확인

### 3. 프론트엔드 연결 오류
- 백엔드 서버 실행 상태 확인
- CORS 설정 확인
- API 엔드포인트 URL 확인

## 개발자 정보 (Developer Information)

- **담당자**: 성시훈
- **기능**: 사용자 관리 및 인증 총괄
- **구현 완료**: 2024년 12월

## 라이선스 (License)

이 프로젝트는 교육 목적으로 개발되었습니다.
