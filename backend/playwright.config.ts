import { defineConfig, devices } from '@playwright/test';

/**
 * Goal: Configure Playwright for backend API testing
 * Tests will make HTTP requests to Spring Boot server running on localhost:8080
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:8080',
    trace: 'on-first-retry',
  },

  projects: [
    {
      name: 'api-tests',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  // webServer 설정 제거 - 서버를 수동으로 실행해야 함
  // 서버 실행: ./mvnw spring-boot:run
  // 또는 기존 서버가 실행 중이면 그대로 사용
});

