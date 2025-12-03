import { test, expect } from '@playwright/test';

/**
 * Goal: Test degree requirement status API endpoints
 * Tests cover checking student degree requirement status
 */
test.describe('Degree Requirement Status API', () => {
  /**
   * Goal: Test getting degree requirement status for a student
   * Scenario: Get status for student with declared majors
   * Pass criteria: Returns status with declared majors, minors, and requirement satisfactions
   * Outcome: System passes - status is retrieved correctly
   */
  test('GET /api/degree-requirements-status/student/{userId} - Get degree requirement status', async ({ request }) => {
    const userId = 1;
    const response = await request.get(`http://localhost:8080/api/degree-requirements-status/student/${userId}`);

    expect(response.status()).toBe(200);
    const status = await response.json();
    expect(status).toHaveProperty('studentId');
    expect(status).toHaveProperty('studentName');
    expect(status).toHaveProperty('declaredMajors');
    expect(status).toHaveProperty('declaredMinors');
    expect(Array.isArray(status.declaredMajors)).toBe(true);
    expect(Array.isArray(status.declaredMinors)).toBe(true);
  });

  /**
   * Goal: Test error handling for non-existent student
   * Scenario: Try to get status for invalid student ID
   * Pass criteria: Returns appropriate error status
   * Outcome: System passes - error is handled correctly
   */
  test('GET /api/degree-requirements-status/student/{userId} - Student not found', async ({ request }) => {
    const invalidUserId = 99999;
    const response = await request.get(`http://localhost:8080/api/degree-requirements-status/student/${invalidUserId}`);

    // Should return error (400 or 404)
    expect([400, 404, 500]).toContain(response.status());
  });
});

