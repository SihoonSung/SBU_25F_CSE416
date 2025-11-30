# Bypass Mode와 기존 OAuth2 로그인 호환성 확인

## ✅ 충돌 없음 확인

### 1. 인증 객체 타입 분리
- **OAuth2 로그인**: `OidcUser` (Spring Security OAuth2가 생성)
- **Bypass 로그인**: `UserPrincipal` (우리가 생성)
- 두 타입이 완전히 다르므로 **충돌 없음**

### 2. 인증 플로우 분리
- **OAuth2 로그인**: 
  - Google OAuth2 → `OAuth2LoginSuccessHandler` → `OidcUser` 생성
  - `/oauth2/authorization/google` → `/login/oauth2/code/google` → redirect
  
- **Bypass 로그인**:
  - Email 입력 → `/api/auth/bypass-login` → `UserPrincipal` 생성
  - 완전히 별도 엔드포인트 사용

### 3. 핵심 컴포넌트 호환성

#### ✅ CurrentUser.getEmail()
```java
// UserPrincipal (bypass mode) 먼저 체크
if (auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
    return user.getEmail();
}
// OAuth2User (기존 OAuth2) 나중에 체크
else if (auth.getPrincipal() instanceof OAuth2User o2User) {
    return o2User.getAttribute("email");
}
```
**→ 둘 다 처리하며, 우선순위로 충돌 방지**

#### ✅ AuthController.getCurrentUser()
```java
// UserPrincipal (bypass mode) 먼저 체크
if (principal instanceof UserPrincipal userPrincipal) {
    user = userPrincipal.getUser();
}
// OidcUser (기존 OAuth2) 나중에 체크
else if (principal instanceof OidcUser oidcUser) {
    // OAuth2 로직
}
```
**→ 둘 다 처리하며, if-else로 충돌 방지**

#### ✅ ProfileController.resolveUserForMe()
```java
private User resolveUserForMe() {
    String email = currentUser.getEmail(); // 둘 다 처리됨
    return userRepository.findByEmailIgnoreCase(email.trim())...
}
```
**→ CurrentUser.getEmail()을 사용하므로 둘 다 처리됨**

### 4. Student Profile 정보 접근

#### ✅ Classes, Transfer Courses, Course Plan
- `/api/users/{userId}/classes`
- `/api/users/{userId}/transfer-courses`
- `/api/users/{userId}/course-plan`

**이 엔드포인트들은:**
- 단순히 `userId`를 path parameter로 받음
- `@PreAuthorize("isAuthenticated()")`만 체크
- 둘 다 인증된 상태면 동일하게 작동

#### ✅ Profile 상세 정보
- `/api/profile/me/details` → `resolveUserForMe()` 사용 → 둘 다 작동
- `/api/profile/me` → `resolveUserForMe()` 사용 → 둘 다 작동

### 5. 동시 사용 불가능
- OAuth2 로그인 시: `OidcUser`가 SecurityContext에 설정
- Bypass 로그인 시: `UserPrincipal`이 SecurityContext에 설정
- **한 번에 하나의 인증 방식만 활성화되므로 충돌 불가능**

### 6. 로그아웃 시나리오
- `/api/auth/logout` → 세션 무효화
- 둘 다 동일한 엔드포인트 사용
- 로그아웃 후 다시 로그인 시 원하는 방식 선택 가능

## 결론

✅ **충돌 없음**
- 두 인증 방식이 완전히 분리되어 있음
- 모든 핵심 컴포넌트가 둘 다 처리하도록 구현됨
- Student profile 정보 (classes, transfer_courses, course_plan) 모두 접근 가능
- 동시 사용 불가능하므로 충돌 시나리오 자체가 없음

## 테스트 시나리오

1. **OAuth2 로그인 → Student Profile 확인** ✅
   - `/api/auth/me` → OidcUser 처리
   - `/api/profile/me/details` → OAuth2User 처리
   - `/api/users/{userId}/classes` → 정상 작동

2. **Bypass 로그인 → Student Profile 확인** ✅
   - `/api/auth/me` → UserPrincipal 처리
   - `/api/profile/me/details` → UserPrincipal 처리
   - `/api/users/{userId}/classes` → 정상 작동

3. **OAuth2 로그인 → 로그아웃 → Bypass 로그인** ✅
   - 세션 무효화 후 새로운 인증 생성
   - 충돌 없음

4. **Bypass 로그인 → 로그아웃 → OAuth2 로그인** ✅
   - 세션 무효화 후 새로운 인증 생성
   - 충돌 없음

