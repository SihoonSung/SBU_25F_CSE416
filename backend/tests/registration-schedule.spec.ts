import { test, expect } from '@playwright/test';

const API_BASE = 'http://localhost:8080/api/admin/registration-schedules';

/**
 * Goal: Test registration schedule API endpoints
 * Tests cover CRUD operations for registration schedules and eligibility checking
 */
test.describe('Registration Schedule API', () => {
  let createdScheduleId: number;

  /**
   * Goal: Test creating a new registration schedule
   * Scenario: Registrar creates a schedule for SENIOR students for Fall 2025
   * Pass criteria: Schedule is created successfully, returns 200 with schedule data
   * Outcome: System passes - schedule is created and persisted
   */
  test('POST /api/admin/registration-schedules - Create schedule', async ({ request }) => {
    const response = await request.post(API_BASE, {
      data: {
        semester: 'Fall',
        year: 2025,
        classStanding: 'SENIOR',
        minCredits: 90,
        maxCredits: null,
        startDate: '2025-10-15',
        endDate: '2025-10-22',
        createdById: 1,
        description: 'Senior registration period'
      },
      headers: {
        'Content-Type': 'application/json'
      }
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.schedule).toBeDefined();
    expect(body.schedule.semester).toBe('Fall');
    expect(body.schedule.year).toBe(2025);
    expect(body.schedule.classStanding).toBe('SENIOR');
    
    createdScheduleId = body.schedule.scheduleId;
  });

  /**
   * Goal: Test retrieving schedules for a term
   * Scenario: Get all schedules for Fall 2025
   * Pass criteria: Returns list of schedules for the specified term
   * Outcome: System passes - schedules are retrieved correctly
   */
  test('GET /api/admin/registration-schedules?semester=Fall&year=2025 - Get schedules for term', async ({ request }) => {
    const response = await request.get(`${API_BASE}?semester=Fall&year=2025`);

    expect(response.status()).toBe(200);
    const schedules = await response.json();
    expect(Array.isArray(schedules)).toBe(true);
  });

  /**
   * Goal: Test retrieving a specific schedule by ID
   * Scenario: Get schedule by scheduleId
   * Pass criteria: Returns schedule data with correct ID
   * Outcome: System passes - schedule is retrieved correctly
   */
  test('GET /api/admin/registration-schedules/{scheduleId} - Get schedule by ID', async ({ request }) => {
    // First create a schedule
    const createResponse = await request.post(API_BASE, {
      data: {
        semester: 'Spring',
        year: 2026,
        classStanding: 'JUNIOR',
        startDate: '2026-03-15',
        endDate: '2026-03-22',
        createdById: 1,
        description: 'Junior registration period'
      },
      headers: {
        'Content-Type': 'application/json'
      }
    });

    const createBody = await createResponse.json();
    const scheduleId = createBody.schedule.scheduleId;

    // Then retrieve it
    const getResponse = await request.get(`${API_BASE}/${scheduleId}`);
    expect(getResponse.status()).toBe(200);
    const schedule = await getResponse.json();
    expect(schedule.scheduleId).toBe(scheduleId);
    expect(schedule.semester).toBe('Spring');
    expect(schedule.year).toBe(2026);
  });

  /**
   * Goal: Test updating a registration schedule
   * Scenario: Update schedule description and dates
   * Pass criteria: Schedule is updated successfully, changes are reflected
   * Outcome: System passes - schedule is updated correctly
   */
  test('PUT /api/admin/registration-schedules/{scheduleId} - Update schedule', async ({ request }) => {
    // First create a schedule
    const createResponse = await request.post(API_BASE, {
      data: {
        semester: 'Summer',
        year: 2026,
        classStanding: 'FRESHMAN',
        startDate: '2026-06-01',
        endDate: '2026-06-08',
        createdById: 1,
        description: 'Freshman registration period'
      },
      headers: {
        'Content-Type': 'application/json'
      }
    });

    const createBody = await createResponse.json();
    const scheduleId = createBody.schedule.scheduleId;

    // Then update it
    const updateResponse = await request.put(`${API_BASE}/${scheduleId}`, {
      data: {
        semester: 'Summer',
        year: 2026,
        classStanding: 'FRESHMAN',
        startDate: '2026-06-05',
        endDate: '2026-06-12',
        description: 'Updated freshman registration period'
      },
      headers: {
        'Content-Type': 'application/json'
      }
    });

    expect(updateResponse.status()).toBe(200);
    const updateBody = await updateResponse.json();
    expect(updateBody.success).toBe(true);
    expect(updateBody.schedule.description).toBe('Updated freshman registration period');
    expect(updateBody.schedule.startDate).toBe('2026-06-05');
  });

  /**
   * Goal: Test deleting a registration schedule
   * Scenario: Delete an existing schedule
   * Pass criteria: Schedule is deleted successfully, returns success message
   * Outcome: System passes - schedule is deleted correctly
   */
  test('DELETE /api/admin/registration-schedules/{scheduleId} - Delete schedule', async ({ request }) => {
    // First create a schedule
    const createResponse = await request.post(API_BASE, {
      data: {
        semester: 'Fall',
        year: 2026,
        classStanding: 'SOPHOMORE',
        startDate: '2026-09-15',
        endDate: '2026-09-22',
        createdById: 1,
        description: 'Sophomore registration period'
      },
      headers: {
        'Content-Type': 'application/json'
      }
    });

    const createBody = await createResponse.json();
    const scheduleId = createBody.schedule.scheduleId;

    // Then delete it
    const deleteResponse = await request.delete(`${API_BASE}/${scheduleId}`);
    expect(deleteResponse.status()).toBe(200);
    const deleteBody = await deleteResponse.json();
    expect(deleteBody.success).toBe(true);
    expect(deleteBody.message).toContain('deleted successfully');

    // Verify it's deleted by trying to get it
    const getResponse = await request.get(`${API_BASE}/${scheduleId}`);
    expect(getResponse.status()).toBe(400);
  });

  /**
   * Goal: Test registration eligibility checking
   * Scenario: Check if a student can register for a term
   * Pass criteria: Returns eligibility status with cumulative credits and class standing
   * Outcome: System passes - eligibility is correctly determined
   */
  test('GET /api/admin/registration-schedules/check/{userId} - Check registration eligibility', async ({ request }) => {
    const userId = 1;
    const response = await request.get(`${API_BASE}/check/${userId}?semester=Fall&year=2025`);

    expect(response.status()).toBe(200);
    const eligibility = await response.json();
    expect(eligibility).toHaveProperty('eligible');
    expect(eligibility).toHaveProperty('cumulativeCredits');
    expect(eligibility).toHaveProperty('classStanding');
    expect(typeof eligibility.eligible).toBe('boolean');
    expect(typeof eligibility.cumulativeCredits).toBe('number');
  });

  /**
   * Goal: Test getting declaration dates for a term
   * Scenario: Get major/minor declaration dates from academic calendar
   * Pass criteria: Returns declaration dates for the specified term
   * Outcome: System passes - declaration dates are retrieved correctly
   */
  test('GET /api/admin/registration-schedules/declaration-dates - Get declaration dates', async ({ request }) => {
    const response = await request.get(`${API_BASE}/declaration-dates?semester=Fall&year=2025`);

    expect(response.status()).toBe(200);
    const dates = await response.json();
    expect(dates).toBeDefined();
  });

  /**
   * Goal: Test error handling for invalid schedule creation
   * Scenario: Try to create schedule with invalid date range (start after end)
   * Pass criteria: Returns 400 error with appropriate error message
   * Outcome: System passes - validation prevents invalid schedule creation
   */
  test('POST /api/admin/registration-schedules - Invalid date range', async ({ request }) => {
    const response = await request.post(API_BASE, {
      data: {
        semester: 'Fall',
        year: 2025,
        classStanding: 'SENIOR',
        startDate: '2025-10-22',
        endDate: '2025-10-15', // End before start
        createdById: 1,
        description: 'Invalid schedule'
      },
      headers: {
        'Content-Type': 'application/json'
      }
    });

    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body.success).toBe(false);
    expect(body.message).toBeDefined();
  });

  /**
   * Goal: Test error handling for non-existent schedule
   * Scenario: Try to get schedule with invalid ID
   * Pass criteria: Returns 400 error with appropriate error message
   * Outcome: System passes - error is handled correctly
   */
  test('GET /api/admin/registration-schedules/{scheduleId} - Schedule not found', async ({ request }) => {
    const invalidId = 99999;
    const response = await request.get(`${API_BASE}/${invalidId}`);

    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body.error).toBeDefined();
  });
});


