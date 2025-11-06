# 테스트 코드 분석 결과

## 사용하지 않는 부분

1. **`clickButton` 헬퍼 함수** (라인 66-72)
   - 1곳에서만 사용됨 (라인 108)
   - 대부분의 테스트는 직접 `userEvent.click` 사용
   - 제거 가능: 단일 사용처를 직접 호출로 변경

## 커버되지 않은 주요 라인들

### 1. 에러 핸들링 (라인 105, 400, 420)
- `useEffect`의 `.catch` 에러 핸들링
- `fetchAvailableTerms`의 catch 블록
- `fetchClassesByTerm`의 catch 블록

### 2. 조건부 로직 (라인 179, 183, 195-196, 209-211)
- `sortClasses` 함수의 조건부 비교
- `loadRegisteredClasses`의 userId 체크 및 early return
- 배열 체크 로직

### 3. Waiver 요청 처리 (라인 630-638)
- waiver 성공 메시지 (라인 628)
- waiver 실패 메시지 (라인 630-634)
- waiver 에러 핸들링 (라인 636-638)

### 4. Cancel Waitlist (라인 689-721)
- 전체 로직이 커버되지 않음
- userId 체크, confirm 체크, 성공/실패 처리

### 5. Drop Course (라인 729-754)
- 전체 로직이 커버되지 않음
- userId 체크, 성공/실패 처리, reload

### 6. Conflict 재추가 에러 핸들링 (라인 837, 895, 917)
- submit registration 성공 시 conflict 재추가 에러
- submit registration 실패 시 conflict 재추가 에러
- catch 블록의 conflict 재추가 에러

### 7. Registration Failed 일반 메시지 (라인 934)
- 다른 에러 타입이 없을 때의 기본 메시지

### 8. UI 렌더링 (라인 1105-1219, 1335, 1375-1398)
- Course details 확장/축소
- Course details 내부 필드들 (lecSchedule, prerequisites 등)
- 다양한 조건부 렌더링

## 추가 테스트 필요

1. useEffect 에러 핸들링 테스트
2. fetchAvailableTerms/fetchClassesByTerm 에러 테스트
3. Waiver 요청 성공/실패 메시지 테스트
4. Cancel waitlist 전체 로직 테스트
5. Drop course 전체 로직 테스트
6. Conflict 재추가 에러 핸들링 테스트
7. Registration failed 일반 메시지 테스트
8. Course details UI 렌더링 테스트

