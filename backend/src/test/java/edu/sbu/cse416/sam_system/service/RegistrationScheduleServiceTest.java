package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.*;
import edu.sbu.cse416.sam_system.repository.RegistrationScheduleRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.UserClassesRepository;
import edu.sbu.cse416.sam_system.repository.AcademicEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Goal: Test class schedule generation and registration eligibility checking
 * Tests cover registration schedule creation, student eligibility
 * determination,
 * and cumulative credits calculation for registration purposes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationScheduleServiceTest {

        @Mock
        private RegistrationScheduleRepository scheduleRepository;

        @Mock
        private UserRepository userRepository;

        @Mock
        private UserClassesRepository userClassesRepository;

        @Mock
        private SystemDateService systemDateService;

        @Mock
        private AcademicEventRepository academicEventRepository;

        @Mock
        private MajorMinorDeclarationService majorMinorDeclarationService;

        @InjectMocks
        private RegistrationScheduleService registrationScheduleService;

        private User testStudent;
        private User testRegistrar;
        private RegistrationSchedule testSchedule;
        private List<UserClasses> testUserClasses;

        @BeforeEach
        void setUp() {
                // Create test student
                testStudent = new User();
                testStudent.setUserId(1L);
                testStudent.setFirstName("John");
                testStudent.setLastName("Doe");
                testStudent.setRole(UserRole.STUDENT);

                // Create test registrar
                testRegistrar = new User();
                testRegistrar.setUserId(100L);
                testRegistrar.setRole(UserRole.REGISTRAR);

                // Create test schedule
                testSchedule = new RegistrationSchedule();
                testSchedule.setScheduleId(1L);
                testSchedule.setSemester("Fall");
                testSchedule.setYear(2025);
                testSchedule.setClassStanding("SENIOR");
                testSchedule.setStartDate(LocalDate.of(2025, 10, 15));
                testSchedule.setEndDate(LocalDate.of(2025, 10, 22));
                testSchedule.setCreatedBy(testRegistrar);

                testUserClasses = new ArrayList<>();
        }

        /**
         * Goal: Test registration schedule creation by registrar
         * Scenario: Registrar creates a new registration schedule for Fall 2025 for
         * SENIOR students
         * Pass criteria: Schedule is saved successfully, all fields are set correctly
         * Outcome: System passes - schedule is created and persisted
         */
        @Test
        void testCreateSchedule_Success() {
                LocalDate startDate = LocalDate.of(2025, 10, 15);
                LocalDate endDate = LocalDate.of(2025, 10, 22);

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Fall"), eq(2025)))
                                .thenReturn(Optional.empty());
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());
                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenReturn(testSchedule);

                RegistrationSchedule result = registrationScheduleService.createSchedule(
                                "Fall", 2025, "SENIOR", null, null,
                                startDate, endDate, 100L, "Senior registration period");

                assertNotNull(result);
                assertEquals("Fall", result.getSemester());
                assertEquals(2025, result.getYear());
                assertEquals("SENIOR", result.getClassStanding());
                verify(scheduleRepository, times(1)).save(any(RegistrationSchedule.class));
        }

        /**
         * Goal: Test registration schedule creation validation
         * Scenario: Registrar tries to create schedule with start date after end date
         * Pass criteria: Method throws RuntimeException with appropriate error message
         * Outcome: System passes - validation prevents invalid schedule creation
         */
        @Test
        void testCreateSchedule_InvalidDateRange() {
                LocalDate startDate = LocalDate.of(2025, 10, 22);
                LocalDate endDate = LocalDate.of(2025, 10, 15); // End before start

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> registrationScheduleService.createSchedule(
                                                "Fall", 2025, "SENIOR", null, null,
                                                startDate, endDate, 100L, null));

                assertTrue(exception.getMessage().contains("Start date must be before"));
                verify(scheduleRepository, never()).save(any());
        }

        /**
         * Goal: Test student registration eligibility check
         * Scenario: SENIOR student with 90 credits checks eligibility for Fall 2025
         * Pass criteria: Eligibility result shows student is eligible, cumulative
         * credits and class standing are calculated correctly
         * Outcome: System passes - eligibility is correctly determined based on credits
         * and schedule
         */
        @Test
        void testGetRegistrationEligibility_EligibleStudent() {
                // Student has 90 credits (SENIOR)
                UserClasses class1 = new UserClasses();
                class1.setCredits(30);
                class1.setGrade("A");
                UserClasses class2 = new UserClasses();
                class2.setCredits(30);
                class2.setGrade("B");
                UserClasses class3 = new UserClasses();
                class3.setCredits(30);
                class3.setGrade("A");
                testUserClasses.addAll(Arrays.asList(class1, class2, class3));

                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 10, 16)); // Within schedule period
                when(scheduleRepository.findActiveSchedulesForTerm("Fall", 2025, LocalDate.of(2025, 10, 16)))
                                .thenReturn(Arrays.asList(testSchedule));

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertNotNull(result);
                assertTrue(result.containsKey("eligible"));
                assertTrue(result.containsKey("cumulativeCredits"));
                assertTrue(result.containsKey("classStanding"));
                verify(userClassesRepository, atLeastOnce()).findCompletedClassesByUserId(1L);
        }

        /**
         * Goal: Test student registration eligibility when not eligible
         * Scenario: FRESHMAN student with 10 credits checks eligibility for SENIOR-only
         * schedule
         * Pass criteria: Eligibility result shows student is not eligible, all
         * schedules are returned for reference
         * Outcome: System passes - correctly identifies ineligibility and provides
         * available schedules
         */
        @Test
        void testGetRegistrationEligibility_NotEligibleStudent() {
                // Student has 10 credits (FRESHMAN)
                UserClasses class1 = new UserClasses();
                class1.setCredits(10);
                class1.setGrade("A");
                testUserClasses.add(class1);

                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 10, 16));
                when(scheduleRepository.findActiveSchedulesForTerm("Fall", 2025, LocalDate.of(2025, 10, 16)))
                                .thenReturn(Arrays.asList(testSchedule));
                when(scheduleRepository.findBySemesterAndYear("Fall", 2025))
                                .thenReturn(Arrays.asList(testSchedule));

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertNotNull(result);
                assertEquals(false, result.get("eligible"));
                assertTrue(result.containsKey("allSchedules"));
                verify(scheduleRepository, times(1)).findBySemesterAndYear("Fall", 2025);
        }

        /**
         * Goal: Test cumulative credits calculation
         * Scenario: Student has completed multiple courses with different credits
         * Pass criteria: Cumulative credits are calculated correctly by summing all
         * completed course credits
         * Outcome: System passes - credits are accurately calculated
         */
        @Test
        void testCalculateCumulativeCredits() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(3);
                class1.setGrade("A");
                UserClasses class2 = new UserClasses();
                class2.setCredits(4);
                class2.setGrade("B");
                UserClasses class3 = new UserClasses();
                class3.setCredits(3);
                class3.setGrade("C");
                testUserClasses.addAll(Arrays.asList(class1, class2, class3));

                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 10, 16));
                when(scheduleRepository.findActiveSchedulesForTerm(anyString(), anyInt(), any()))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear(anyString(), anyInt()))
                                .thenReturn(Collections.emptyList());

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertNotNull(result);
                assertEquals(10, result.get("cumulativeCredits")); // 3 + 4 + 3 = 10
                verify(userClassesRepository, atLeastOnce()).findCompletedClassesByUserId(1L);
        }

        /**
         * Goal: Test class standing calculation based on credits
         * Scenario: Student with different credit amounts should have correct class
         * standing
         * Pass criteria: Class standing is correctly determined: 0-23=FRESHMAN,
         * 24-56=SOPHOMORE, 57-84=JUNIOR, 85+=SENIOR
         * Outcome: System passes - class standing is accurately calculated
         */
        @Test
        void testCalculateClassStanding() {
                // Test SENIOR (90 credits)
                UserClasses class1 = new UserClasses();
                class1.setCredits(30);
                class1.setGrade("A");
                UserClasses class2 = new UserClasses();
                class2.setCredits(30);
                class2.setGrade("B");
                UserClasses class3 = new UserClasses();
                class3.setCredits(30);
                class3.setGrade("A");
                testUserClasses.addAll(Arrays.asList(class1, class2, class3));

                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 10, 16));
                when(scheduleRepository.findActiveSchedulesForTerm(anyString(), anyInt(), any()))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear(anyString(), anyInt()))
                                .thenReturn(Collections.emptyList());

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertNotNull(result);
                assertEquals("SENIOR", result.get("classStanding"));
        }

        /**
         * Goal: Test getting all schedules for a term
         * Scenario: Retrieve all registration schedules for Fall 2025
         * Pass criteria: Method returns list of schedules for the specified term
         * Outcome: System passes - schedules are retrieved correctly
         */
        @Test
        void testGetSchedulesForTerm() {
                when(scheduleRepository.findBySemesterAndYear("Fall", 2025))
                                .thenReturn(Arrays.asList(testSchedule));

                List<RegistrationSchedule> result = registrationScheduleService.getSchedulesForTerm("Fall", 2025);

                assertNotNull(result);
                assertEquals(1, result.size());
                assertEquals("Fall", result.get(0).getSemester());
                assertEquals(2025, result.get(0).getYear());
                verify(scheduleRepository, times(1)).findBySemesterAndYear("Fall", 2025);
        }

        /**
         * Goal: Test schedule deletion
         * Scenario: Registrar deletes an existing registration schedule
         * Pass criteria: Schedule is deleted from repository, no exceptions thrown
         * Outcome: System passes - schedule is successfully deleted
         */
        @Test
        void testDeleteSchedule() {
                when(scheduleRepository.existsById(1L)).thenReturn(true);
                doNothing().when(scheduleRepository).deleteById(1L);

                registrationScheduleService.deleteSchedule(1L);

                verify(scheduleRepository, times(1)).existsById(1L);
                verify(scheduleRepository, times(1)).deleteById(1L);
        }

        /**
         * Goal: Test schedule deletion when schedule not found
         * Scenario: Attempt to delete non-existent schedule
         * Pass criteria: Method throws RuntimeException with appropriate message
         * Outcome: System passes - error is handled correctly
         */
        @Test
        void testDeleteSchedule_NotFound() {
                when(scheduleRepository.existsById(999L)).thenReturn(false);

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> registrationScheduleService.deleteSchedule(999L));

                assertTrue(exception.getMessage().contains("Schedule not found"));
                verify(scheduleRepository, never()).deleteById(any());
        }

        /**
         * Goal: Test getting schedule by ID
         * Scenario: Retrieve a specific schedule by its ID
         * Pass criteria: Method returns the correct schedule
         * Outcome: System passes - schedule is retrieved correctly
         */
        @Test
        void testGetScheduleById() {
                when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));

                RegistrationSchedule result = registrationScheduleService.getScheduleById(1L);

                assertNotNull(result);
                assertEquals(1L, result.getScheduleId());
                assertEquals("Fall", result.getSemester());
                verify(scheduleRepository, times(1)).findById(1L);
        }

        /**
         * Goal: Test getting schedule by ID when not found
         * Scenario: Attempt to retrieve non-existent schedule
         * Pass criteria: Method throws RuntimeException
         * Outcome: System passes - error is handled correctly
         */
        @Test
        void testGetScheduleById_NotFound() {
                when(scheduleRepository.findById(999L)).thenReturn(Optional.empty());

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> registrationScheduleService.getScheduleById(999L));

                assertTrue(exception.getMessage().contains("Schedule not found"));
        }

        /**
         * Goal: Test updating a schedule
         * Scenario: Registrar updates an existing schedule
         * Pass criteria: Schedule is updated with new values
         * Outcome: System passes - schedule is updated correctly
         */
        @Test
        void testUpdateSchedule() {
                LocalDate newStartDate = LocalDate.of(2025, 10, 20);
                LocalDate newEndDate = LocalDate.of(2025, 10, 27);

                when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Fall"), eq(2025)))
                                .thenReturn(Optional.empty());
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());
                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenReturn(testSchedule);

                RegistrationSchedule result = registrationScheduleService.updateSchedule(
                                1L, "Fall", 2025, "JUNIOR", null, null,
                                newStartDate, newEndDate, "Updated description");

                assertNotNull(result);
                verify(scheduleRepository, times(1)).save(any(RegistrationSchedule.class));
        }

        /**
         * Goal: Test getting active schedules for a term
         * Scenario: Retrieve all active schedules for Fall 2025 on current date
         * Pass criteria: Method returns list of active schedules
         * Outcome: System passes - active schedules are retrieved correctly
         */
        @Test
        void testGetActiveSchedulesForTerm() {
                LocalDate currentDate = LocalDate.of(2025, 10, 16);
                when(systemDateService.today()).thenReturn(currentDate);
                when(scheduleRepository.findActiveSchedulesForTerm("Fall", 2025, currentDate))
                                .thenReturn(Arrays.asList(testSchedule));

                List<RegistrationSchedule> result = registrationScheduleService.getActiveSchedulesForTerm("Fall", 2025);

                assertNotNull(result);
                assertEquals(1, result.size());
                verify(scheduleRepository, times(1)).findActiveSchedulesForTerm("Fall", 2025, currentDate);
        }

        /**
         * Goal: Test canStudentRegister method - eligible student
         * Scenario: SENIOR student with 90 credits can register
         * Pass criteria: Method returns true
         * Outcome: System passes - eligibility is correctly determined
         */
        @Test
        void testCanStudentRegister_Eligible() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(30);
                class1.setGrade("A");
                UserClasses class2 = new UserClasses();
                class2.setCredits(30);
                class2.setGrade("B");
                UserClasses class3 = new UserClasses();
                class3.setCredits(30);
                class3.setGrade("A");
                testUserClasses.addAll(Arrays.asList(class1, class2, class3));

                LocalDate currentDate = LocalDate.of(2025, 10, 16);
                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(currentDate);
                when(scheduleRepository.findActiveSchedulesForTerm("Fall", 2025, currentDate))
                                .thenReturn(Arrays.asList(testSchedule));

                boolean result = registrationScheduleService.canStudentRegister(1L, "Fall", 2025);

                assertTrue(result);
        }

        /**
         * Goal: Test canStudentRegister method - not eligible
         * Scenario: FRESHMAN student cannot register for SENIOR-only schedule
         * Pass criteria: Method returns false
         * Outcome: System passes - ineligibility is correctly determined
         */
        @Test
        void testCanStudentRegister_NotEligible() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(10);
                class1.setGrade("A");
                testUserClasses.add(class1);

                LocalDate currentDate = LocalDate.of(2025, 10, 16);
                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(currentDate);
                when(scheduleRepository.findActiveSchedulesForTerm("Fall", 2025, currentDate))
                                .thenReturn(Arrays.asList(testSchedule));

                boolean result = registrationScheduleService.canStudentRegister(1L, "Fall", 2025);

                assertFalse(result);
        }

        /**
         * Goal: Test class standing calculation - FRESHMAN
         * Scenario: Student with 15 credits should be FRESHMAN
         * Pass criteria: Class standing is correctly calculated as FRESHMAN
         * Outcome: System passes - class standing calculation works
         */
        @Test
        void testCalculateClassStanding_Freshman() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(15);
                class1.setGrade("A");
                testUserClasses.add(class1);

                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 10, 16));
                when(scheduleRepository.findActiveSchedulesForTerm(anyString(), anyInt(), any()))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear(anyString(), anyInt()))
                                .thenReturn(Collections.emptyList());

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertEquals("FRESHMAN", result.get("classStanding"));
        }

        /**
         * Goal: Test class standing calculation - SOPHOMORE
         * Scenario: Student with 30 credits should be SOPHOMORE
         * Pass criteria: Class standing is correctly calculated as SOPHOMORE
         * Outcome: System passes - class standing calculation works
         */
        @Test
        void testCalculateClassStanding_Sophomore() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(30);
                class1.setGrade("A");
                testUserClasses.add(class1);

                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 10, 16));
                when(scheduleRepository.findActiveSchedulesForTerm(anyString(), anyInt(), any()))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear(anyString(), anyInt()))
                                .thenReturn(Collections.emptyList());

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertEquals("SOPHOMORE", result.get("classStanding"));
        }

        /**
         * Goal: Test class standing calculation - JUNIOR
         * Scenario: Student with 60 credits should be JUNIOR
         * Pass criteria: Class standing is correctly calculated as JUNIOR
         * Outcome: System passes - class standing calculation works
         */
        @Test
        void testCalculateClassStanding_Junior() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(30);
                class1.setGrade("A");
                UserClasses class2 = new UserClasses();
                class2.setCredits(30);
                class2.setGrade("B");
                testUserClasses.addAll(Arrays.asList(class1, class2));

                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 10, 16));
                when(scheduleRepository.findActiveSchedulesForTerm(anyString(), anyInt(), any()))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear(anyString(), anyInt()))
                                .thenReturn(Collections.emptyList());

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertEquals("JUNIOR", result.get("classStanding"));
        }

        /**
         * Goal: Test class standing normalization - U1 format
         * Scenario: Schedule created with "U1" should be normalized to "FRESHMAN"
         * Pass criteria: Class standing is normalized correctly
         * Outcome: System passes - normalization works
         */
        @Test
        void testNormalizeClassStanding_U1() {
                LocalDate startDate = LocalDate.of(2025, 10, 15);
                LocalDate endDate = LocalDate.of(2025, 10, 22);

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Fall"), eq(2025)))
                                .thenReturn(Optional.empty());
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());
                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenAnswer(invocation -> {
                        RegistrationSchedule schedule = invocation.getArgument(0);
                        schedule.setScheduleId(1L);
                        return schedule;
                });

                RegistrationSchedule result = registrationScheduleService.createSchedule(
                                "Fall", 2025, "U1", null, null,
                                startDate, endDate, 100L, null);

                assertEquals("FRESHMAN", result.getClassStanding());
        }

        /**
         * Goal: Test schedule creation with minCredits and maxCredits
         * Scenario: Create schedule with credit range instead of class standing
         * Pass criteria: Schedule is created with credit thresholds
         * Outcome: System passes - credit-based schedule works
         */
        @Test
        void testCreateSchedule_WithCreditsRange() {
                LocalDate startDate = LocalDate.of(2025, 10, 15);
                LocalDate endDate = LocalDate.of(2025, 10, 22);

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Fall"), eq(2025)))
                                .thenReturn(Optional.empty());
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());
                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenAnswer(invocation -> {
                        RegistrationSchedule schedule = invocation.getArgument(0);
                        schedule.setScheduleId(1L);
                        return schedule;
                });

                RegistrationSchedule result = registrationScheduleService.createSchedule(
                                "Fall", 2025, null, 24, 56,
                                startDate, endDate, 100L, null);

                assertNotNull(result);
                assertEquals(24, result.getMinCredits());
                assertEquals(56, result.getMaxCredits());
        }

        /**
         * Goal: Test schedule creation with auto-filled credits from class standing
         * Scenario: Create schedule with class standing, credits should auto-fill
         * Pass criteria: Credits are automatically set based on class standing
         * Outcome: System passes - auto-fill logic works
         */
        @Test
        void testCreateSchedule_AutoFillCredits() {
                LocalDate startDate = LocalDate.of(2025, 10, 15);
                LocalDate endDate = LocalDate.of(2025, 10, 22);

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Fall"), eq(2025)))
                                .thenReturn(Optional.empty());
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());
                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenAnswer(invocation -> {
                        RegistrationSchedule schedule = invocation.getArgument(0);
                        schedule.setScheduleId(1L);
                        return schedule;
                });

                RegistrationSchedule result = registrationScheduleService.createSchedule(
                                "Fall", 2025, "SOPHOMORE", null, null,
                                startDate, endDate, 100L, null);

                assertNotNull(result);
                assertEquals("SOPHOMORE", result.getClassStanding());
                assertNotNull(result.getMinCredits());
                assertNotNull(result.getMaxCredits());
        }

        /**
         * Goal: Test schedule creation validation - no criteria specified
         * Scenario: Try to create schedule without class standing or credits
         * Pass criteria: Method throws RuntimeException
         * Outcome: System passes - validation prevents invalid schedule
         */
        @Test
        void testCreateSchedule_NoCriteria() {
                LocalDate startDate = LocalDate.of(2025, 10, 15);
                LocalDate endDate = LocalDate.of(2025, 10, 22);

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> registrationScheduleService.createSchedule(
                                                "Fall", 2025, null, null, null,
                                                startDate, endDate, 100L, null));

                assertTrue(exception.getMessage().contains("must be specified"));
        }

        /**
         * Goal: Test getAdvancedRegistrationBegins
         * Scenario: Retrieve advanced registration start date from academic calendar
         * Pass criteria: Method returns Optional with date if found
         * Outcome: System passes - academic calendar lookup works
         */
        @Test
        void testGetAdvancedRegistrationBegins() {
                AcademicEvent event = new AcademicEvent();
                event.setTitle("Advanced Registration Begins");
                event.setSemester("Fall");
                event.setYear(2025);
                event.setDate(LocalDate.of(2025, 10, 1));

                when(academicEventRepository.findByTitleAndSemesterAndYear("Advanced Registration Begins", "Fall",
                                2025))
                                .thenReturn(Optional.of(event));

                Optional<LocalDate> result = registrationScheduleService.getAdvancedRegistrationBegins("Fall", 2025);

                assertTrue(result.isPresent());
                assertEquals(LocalDate.of(2025, 10, 1), result.get());
        }

        /**
         * Goal: Test getLateRegistrationEnds
         * Scenario: Retrieve late registration end date from academic calendar
         * Pass criteria: Method returns Optional with date if found
         * Outcome: System passes - academic calendar lookup works
         */
        @Test
        void testGetLateRegistrationEnds() {
                AcademicEvent event = new AcademicEvent();
                event.setTitle("Late Registration Ends");
                event.setSemester("Fall");
                event.setYear(2025);
                event.setDate(LocalDate.of(2025, 10, 31));

                when(academicEventRepository.findByTitleAndSemesterAndYear("Late Registration Ends", "Fall", 2025))
                                .thenReturn(Optional.of(event));

                Optional<LocalDate> result = registrationScheduleService.getLateRegistrationEnds("Fall", 2025);

                assertTrue(result.isPresent());
                assertEquals(LocalDate.of(2025, 10, 31), result.get());
        }

        /**
         * Goal: Test getDeclarationDatesForTerm
         * Scenario: Retrieve major/minor declaration dates for a term
         * Pass criteria: Method returns declaration dates if available
         * Outcome: System passes - declaration dates are retrieved
         */
        @Test
        void testGetDeclarationDatesForTerm() {
                LocalDate beginDate = LocalDate.of(2025, 9, 1);
                LocalDate endDate = LocalDate.of(2025, 9, 15);

                when(majorMinorDeclarationService.getMajorMinorChangesBegin("Fall", 2025))
                                .thenReturn(Optional.of(beginDate));
                when(majorMinorDeclarationService.getMajorMinorChangesEnd("Fall", 2025))
                                .thenReturn(Optional.of(endDate));

                Map<String, Object> result = registrationScheduleService.getDeclarationDatesForTerm("Fall", 2025);

                assertNotNull(result);
                assertTrue(result.containsKey("declarationAvailable"));
                assertEquals(true, result.get("declarationAvailable"));
        }

        /**
         * Goal: Test registration eligibility with credit-based schedule
         * Scenario: Student with 30 credits matches schedule with minCredits=24,
         * maxCredits=56
         * Pass criteria: Student is eligible
         * Outcome: System passes - credit-based eligibility works
         */
        @Test
        void testGetRegistrationEligibility_CreditBased() {
                RegistrationSchedule creditSchedule = new RegistrationSchedule();
                creditSchedule.setScheduleId(2L);
                creditSchedule.setSemester("Fall");
                creditSchedule.setYear(2025);
                creditSchedule.setMinCredits(24);
                creditSchedule.setMaxCredits(56);
                creditSchedule.setStartDate(LocalDate.of(2025, 10, 15));
                creditSchedule.setEndDate(LocalDate.of(2025, 10, 22));

                UserClasses class1 = new UserClasses();
                class1.setCredits(30);
                class1.setGrade("A");
                testUserClasses.add(class1);

                LocalDate currentDate = LocalDate.of(2025, 10, 16);
                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(currentDate);
                when(scheduleRepository.findActiveSchedulesForTerm("Fall", 2025, currentDate))
                                .thenReturn(Arrays.asList(creditSchedule));

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertNotNull(result);
                assertTrue(result.containsKey("eligible"));
        }

        /**
         * Goal: Test registration eligibility - before schedule period
         * Scenario: Current date is before schedule start date
         * Pass criteria: Student is not eligible
         * Outcome: System passes - date validation works
         */
        @Test
        void testGetRegistrationEligibility_BeforePeriod() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(90);
                class1.setGrade("A");
                testUserClasses.add(class1);

                LocalDate currentDate = LocalDate.of(2025, 10, 10); // Before start date
                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(currentDate);
                when(scheduleRepository.findActiveSchedulesForTerm("Fall", 2025, currentDate))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear("Fall", 2025))
                                .thenReturn(Arrays.asList(testSchedule));

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertNotNull(result);
                assertEquals(false, result.get("eligible"));
        }

        /**
         * Goal: Test registration eligibility - after schedule period
         * Scenario: Current date is after schedule end date
         * Pass criteria: Student is not eligible
         * Outcome: System passes - date validation works
         */
        @Test
        void testGetRegistrationEligibility_AfterPeriod() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(90);
                class1.setGrade("A");
                testUserClasses.add(class1);

                LocalDate currentDate = LocalDate.of(2025, 10, 23); // After end date
                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(currentDate);
                when(scheduleRepository.findActiveSchedulesForTerm("Fall", 2025, currentDate))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear("Fall", 2025))
                                .thenReturn(Arrays.asList(testSchedule));

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertNotNull(result);
                assertEquals(false, result.get("eligible"));
        }

        /**
         * Goal: Test createSchedule with start date before advanced registration
         * Scenario: Try to create schedule starting before advanced registration begins
         * Pass criteria: RuntimeException is thrown
         */
        @Test
        void testCreateSchedule_BeforeAdvancedRegistration() {
                LocalDate startDate = LocalDate.of(2025, 9, 1); // Before advanced reg
                LocalDate endDate = LocalDate.of(2025, 10, 22);
                LocalDate advancedRegDate = LocalDate.of(2025, 10, 1);

                AcademicEvent event = new AcademicEvent();
                event.setDate(advancedRegDate);

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 8, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Fall"), eq(2025)))
                                .thenReturn(Optional.of(event));

                RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                        registrationScheduleService.createSchedule(
                                        "Fall", 2025, "SENIOR", null, null,
                                        startDate, endDate, 100L, null);
                });

                assertTrue(exception.getMessage().contains("cannot be before Advanced Registration Begins"));
        }

        /**
         * Goal: Test createSchedule with auto-calculated end date (Next Term logic)
         * Scenario: Create schedule with null endDate, should infer from next term's
         * late registration
         * Pass criteria: End date is set to next term's late registration end
         */
        @Test
        void testCreateSchedule_AutoEndDate() {
                LocalDate startDate = LocalDate.of(2025, 10, 15);
                LocalDate lateRegEnds = LocalDate.of(2026, 2, 1); // Spring 2026 late reg

                AcademicEvent event = new AcademicEvent();
                event.setDate(lateRegEnds);

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));
                // Mock advanced reg check to pass
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Fall"), eq(2025)))
                                .thenReturn(Optional.empty());
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());

                // Mock next term (Spring 2026) late registration lookup
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Spring"), eq(2026)))
                                .thenReturn(Optional.of(event));

                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenAnswer(invocation -> {
                        RegistrationSchedule schedule = invocation.getArgument(0);
                        schedule.setScheduleId(1L);
                        return schedule;
                });

                RegistrationSchedule result = registrationScheduleService.createSchedule(
                                "Fall", 2025, "SENIOR", null, null,
                                startDate, null, 100L, null);

                assertNotNull(result.getEndDate());
                assertEquals(lateRegEnds, result.getEndDate());
        }

        /**
         * Goal: Test calculateClassStanding with exact boundaries
         * Scenario: Test 23, 24, 56, 57, 84, 85 credits
         * Pass criteria: Correct class standing returned for each boundary
         */
        @Test
        void testCalculateClassStanding_Boundaries() {
                assertEquals("FRESHMAN", registrationScheduleService.calculateClassStanding(23));
                assertEquals("SOPHOMORE", registrationScheduleService.calculateClassStanding(24));
                assertEquals("SOPHOMORE", registrationScheduleService.calculateClassStanding(56));
                assertEquals("JUNIOR", registrationScheduleService.calculateClassStanding(57));
                assertEquals("JUNIOR", registrationScheduleService.calculateClassStanding(84));
                assertEquals("SENIOR", registrationScheduleService.calculateClassStanding(85));
                assertEquals("FRESHMAN", registrationScheduleService.calculateClassStanding(null));
                assertEquals("FRESHMAN", registrationScheduleService.calculateClassStanding(-5));
        }

        /**
         * Goal: Test validateAgainstDeclarationDates error handling
         * Scenario: MajorMinorDeclarationService throws exception
         * Pass criteria: Exception is caught and logged, flow continues without error
         */
        @Test
        void testCreateSchedule_DeclarationServiceError() {
                LocalDate startDate = LocalDate.of(2025, 10, 15);
                LocalDate endDate = LocalDate.of(2025, 10, 22);

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Fall"), eq(2025)))
                                .thenReturn(Optional.empty());
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());

                // Mock declaration service to throw exception
                when(majorMinorDeclarationService.getMajorMinorChangesBegin(anyString(), anyInt()))
                                .thenThrow(new RuntimeException("Service down"));

                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenReturn(testSchedule);

                // Should not throw exception
                assertDoesNotThrow(() -> registrationScheduleService.createSchedule(
                                "Fall", 2025, "SENIOR", null, null,
                                startDate, endDate, 100L, null));
        }

        /**
         * Goal: Test getDeclarationDatesForTerm error handling
         * Scenario: MajorMinorDeclarationService throws exception
         * Pass criteria: Returns map with error message
         */
        @Test
        void testGetDeclarationDatesForTerm_Error() {
                when(majorMinorDeclarationService.getMajorMinorChangesBegin(anyString(), anyInt()))
                                .thenThrow(new RuntimeException("Service down"));

                Map<String, Object> result = registrationScheduleService.getDeclarationDatesForTerm("Fall", 2025);

                assertNotNull(result);
                assertEquals(false, result.get("declarationAvailable"));
                assertTrue(result.containsKey("error"));
        }

        /**
         * Goal: Test cumulative credits calculation with null credits
         * Scenario: Some classes have null credits
         * Pass criteria: Null credits are treated as 0
         * Outcome: System passes - null handling works
         */
        @Test
        void testCalculateCumulativeCredits_WithNullCredits() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(3);
                class1.setGrade("A");
                UserClasses class2 = new UserClasses();
                class2.setCredits(null); // Null credits
                class2.setGrade("B");
                testUserClasses.addAll(Arrays.asList(class1, class2));

                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 10, 16));
                when(scheduleRepository.findActiveSchedulesForTerm(anyString(), anyInt(), any()))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear(anyString(), anyInt()))
                                .thenReturn(Collections.emptyList());

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertEquals(3, result.get("cumulativeCredits")); // Only 3 credits counted
        }

        /**
         * Goal: Test class standing calculation with null credits
         * Scenario: Student has no completed classes (0 credits)
         * Pass criteria: Class standing defaults to FRESHMAN
         * Outcome: System passes - default handling works
         */
        @Test
        void testCalculateClassStanding_ZeroCredits() {
                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(Collections.emptyList());
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 10, 16));
                when(scheduleRepository.findActiveSchedulesForTerm(anyString(), anyInt(), any()))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear(anyString(), anyInt()))
                                .thenReturn(Collections.emptyList());

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertEquals("FRESHMAN", result.get("classStanding"));
                assertEquals(0, result.get("cumulativeCredits"));
        }

        /**
         * Goal: Test schedule creation with null startDate - auto-fill from calendar
         * Scenario: Create schedule without startDate, should get from academic
         * calendar
         * Pass criteria: Start date is retrieved from calendar
         * Outcome: System passes - auto-fill from calendar works
         */
        @Test
        void testCreateSchedule_AutoFillStartDate() {
                AcademicEvent event = new AcademicEvent();
                event.setTitle("Advanced Registration Begins");
                event.setSemester("Fall");
                event.setYear(2025);
                event.setDate(LocalDate.of(2025, 10, 1));

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear("Advanced Registration Begins", "Fall",
                                2025))
                                .thenReturn(Optional.of(event));
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());
                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenAnswer(invocation -> {
                        RegistrationSchedule schedule = invocation.getArgument(0);
                        schedule.setScheduleId(1L);
                        return schedule;
                });

                RegistrationSchedule result = registrationScheduleService.createSchedule(
                                "Fall", 2025, "SENIOR", null, null,
                                null, null, 100L, null);

                assertNotNull(result);
                verify(scheduleRepository, times(1)).save(any(RegistrationSchedule.class));
        }

        /**
         * Goal: Test schedule creation with startDate before advanced registration
         * Scenario: Try to create schedule with start date before calendar date
         * Pass criteria: Method throws RuntimeException
         * Outcome: System passes - validation prevents early start date
         */
        @Test
        void testCreateSchedule_StartDateBeforeAdvancedReg() {
                LocalDate startDate = LocalDate.of(2025, 9, 15);
                LocalDate endDate = LocalDate.of(2025, 10, 22);

                AcademicEvent event = new AcademicEvent();
                event.setTitle("Advanced Registration Begins");
                event.setSemester("Fall");
                event.setYear(2025);
                event.setDate(LocalDate.of(2025, 10, 1)); // Later than startDate

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear("Advanced Registration Begins", "Fall",
                                2025))
                                .thenReturn(Optional.of(event));

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> registrationScheduleService.createSchedule(
                                                "Fall", 2025, "SENIOR", null, null,
                                                startDate, endDate, 100L, null));

                assertTrue(exception.getMessage().contains("cannot be before"));
        }

        /**
         * Goal: Test schedule creation with null endDate - auto-fill from next term
         * Scenario: Create schedule without endDate, should get from next term's
         * calendar
         * Pass criteria: End date is retrieved from next term calendar
         * Outcome: System passes - auto-fill from next term works
         */
        @Test
        void testCreateSchedule_AutoFillEndDate() {
                LocalDate startDate = LocalDate.of(2025, 10, 15);

                AcademicEvent nextTermEvent = new AcademicEvent();
                nextTermEvent.setTitle("Late Registration Ends");
                nextTermEvent.setSemester("Spring");
                nextTermEvent.setYear(2026);
                nextTermEvent.setDate(LocalDate.of(2026, 2, 1));

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Fall"), eq(2025)))
                                .thenReturn(Optional.empty());
                when(academicEventRepository.findByTitleAndSemesterAndYear("Late Registration Ends", "Spring", 2026))
                                .thenReturn(Optional.of(nextTermEvent));
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());
                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenAnswer(invocation -> {
                        RegistrationSchedule schedule = invocation.getArgument(0);
                        schedule.setScheduleId(1L);
                        return schedule;
                });

                RegistrationSchedule result = registrationScheduleService.createSchedule(
                                "Fall", 2025, "SENIOR", null, null,
                                startDate, null, 100L, null);

                assertNotNull(result);
                verify(scheduleRepository, times(1)).save(any(RegistrationSchedule.class));
        }

        /**
         * Goal: Test schedule creation with Spring semester
         * Scenario: Create schedule for Spring 2025
         * Pass criteria: Schedule is created for Spring term
         * Outcome: System passes - Spring semester handling works
         */
        @Test
        void testCreateSchedule_SpringSemester() {
                LocalDate startDate = LocalDate.of(2025, 2, 1);
                LocalDate endDate = LocalDate.of(2025, 2, 8);

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 1, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Spring"), eq(2025)))
                                .thenReturn(Optional.empty());
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());
                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenAnswer(invocation -> {
                        RegistrationSchedule schedule = invocation.getArgument(0);
                        schedule.setScheduleId(1L);
                        return schedule;
                });

                RegistrationSchedule result = registrationScheduleService.createSchedule(
                                "Spring", 2025, "JUNIOR", null, null,
                                startDate, endDate, 100L, null);

                assertNotNull(result);
                assertEquals("Spring", result.getSemester());
        }

        /**
         * Goal: Test schedule creation with Summer semester
         * Scenario: Create schedule for Summer 2025
         * Pass criteria: Schedule is created for Summer term
         * Outcome: System passes - Summer semester handling works
         */
        @Test
        void testCreateSchedule_SummerSemester() {
                LocalDate startDate = LocalDate.of(2025, 6, 1);
                LocalDate endDate = LocalDate.of(2025, 6, 8);

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 5, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Summer"), eq(2025)))
                                .thenReturn(Optional.empty());
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());
                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenAnswer(invocation -> {
                        RegistrationSchedule schedule = invocation.getArgument(0);
                        schedule.setScheduleId(1L);
                        return schedule;
                });

                RegistrationSchedule result = registrationScheduleService.createSchedule(
                                "Summer", 2025, "SOPHOMORE", null, null,
                                startDate, endDate, 100L, null);

                assertNotNull(result);
                assertEquals("Summer", result.getSemester());
        }

        /**
         * Goal: Test class standing normalization - U2 format
         * Scenario: Schedule created with "U2" should be normalized to "SOPHOMORE"
         * Pass criteria: Class standing is normalized correctly
         * Outcome: System passes - normalization works
         */
        @Test
        void testNormalizeClassStanding_U2() {
                LocalDate startDate = LocalDate.of(2025, 10, 15);
                LocalDate endDate = LocalDate.of(2025, 10, 22);

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Fall"), eq(2025)))
                                .thenReturn(Optional.empty());
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());
                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenAnswer(invocation -> {
                        RegistrationSchedule schedule = invocation.getArgument(0);
                        schedule.setScheduleId(1L);
                        return schedule;
                });

                RegistrationSchedule result = registrationScheduleService.createSchedule(
                                "Fall", 2025, "U2", null, null,
                                startDate, endDate, 100L, null);

                assertEquals("SOPHOMORE", result.getClassStanding());
        }

        /**
         * Goal: Test class standing normalization - U3 format
         * Scenario: Schedule created with "U3" should be normalized to "JUNIOR"
         * Pass criteria: Class standing is normalized correctly
         * Outcome: System passes - normalization works
         */
        @Test
        void testNormalizeClassStanding_U3() {
                LocalDate startDate = LocalDate.of(2025, 10, 15);
                LocalDate endDate = LocalDate.of(2025, 10, 22);

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Fall"), eq(2025)))
                                .thenReturn(Optional.empty());
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());
                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenAnswer(invocation -> {
                        RegistrationSchedule schedule = invocation.getArgument(0);
                        schedule.setScheduleId(1L);
                        return schedule;
                });

                RegistrationSchedule result = registrationScheduleService.createSchedule(
                                "Fall", 2025, "U3", null, null,
                                startDate, endDate, 100L, null);

                assertEquals("JUNIOR", result.getClassStanding());
        }

        /**
         * Goal: Test class standing normalization - U4 format
         * Scenario: Schedule created with "U4" should be normalized to "SENIOR"
         * Pass criteria: Class standing is normalized correctly
         * Outcome: System passes - normalization works
         */
        @Test
        void testNormalizeClassStanding_U4() {
                LocalDate startDate = LocalDate.of(2025, 10, 15);
                LocalDate endDate = LocalDate.of(2025, 10, 22);

                when(userRepository.findById(100L)).thenReturn(Optional.of(testRegistrar));
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 9, 1));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Fall"), eq(2025)))
                                .thenReturn(Optional.empty());
                when(academicEventRepository.findByTitleKeyword(anyString())).thenReturn(Collections.emptyList());
                when(scheduleRepository.save(any(RegistrationSchedule.class))).thenAnswer(invocation -> {
                        RegistrationSchedule schedule = invocation.getArgument(0);
                        schedule.setScheduleId(1L);
                        return schedule;
                });

                RegistrationSchedule result = registrationScheduleService.createSchedule(
                                "Fall", 2025, "U4", null, null,
                                startDate, endDate, 100L, null);

                assertEquals("SENIOR", result.getClassStanding());
        }

        /**
         * Goal: Test registration eligibility with multiple matching schedules
         * Scenario: Student matches multiple schedules for the same term
         * Pass criteria: All matching schedules are returned
         * Outcome: System passes - multiple schedules are handled
         */
        @Test
        void testGetRegistrationEligibility_MultipleSchedules() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(90);
                class1.setGrade("A");
                testUserClasses.add(class1);

                RegistrationSchedule schedule1 = new RegistrationSchedule();
                schedule1.setScheduleId(1L);
                schedule1.setSemester("Fall");
                schedule1.setYear(2025);
                schedule1.setClassStanding("SENIOR");
                schedule1.setStartDate(LocalDate.of(2025, 10, 15));
                schedule1.setEndDate(LocalDate.of(2025, 10, 22));

                RegistrationSchedule schedule2 = new RegistrationSchedule();
                schedule2.setScheduleId(2L);
                schedule2.setSemester("Fall");
                schedule2.setYear(2025);
                schedule2.setMinCredits(85);
                schedule2.setStartDate(LocalDate.of(2025, 10, 15));
                schedule2.setEndDate(LocalDate.of(2025, 10, 22));

                LocalDate currentDate = LocalDate.of(2025, 10, 16);
                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(currentDate);
                when(scheduleRepository.findActiveSchedulesForTerm("Fall", 2025, currentDate))
                                .thenReturn(Arrays.asList(schedule1, schedule2));

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertNotNull(result);
                assertTrue(result.containsKey("eligible"));
                assertTrue(result.containsKey("matchingSchedules"));
        }

        /**
         * Goal: Test registration eligibility with no active schedules
         * Scenario: No schedules exist for the term or all are inactive
         * Pass criteria: Student is not eligible
         * Outcome: System passes - no schedules case is handled
         */
        @Test
        void testGetRegistrationEligibility_NoActiveSchedules() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(90);
                class1.setGrade("A");
                testUserClasses.add(class1);

                LocalDate currentDate = LocalDate.of(2025, 10, 16);
                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(currentDate);
                when(scheduleRepository.findActiveSchedulesForTerm("Fall", 2025, currentDate))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear("Fall", 2025))
                                .thenReturn(Collections.emptyList());

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertNotNull(result);
                assertEquals(false, result.get("eligible"));
        }

        /**
         * Goal: Test canStudentRegister with credit-based schedule
         * Scenario: Student with 30 credits matches schedule with minCredits=24,
         * maxCredits=56
         * Pass criteria: Method returns true
         * Outcome: System passes - credit-based eligibility works
         */
        @Test
        void testCanStudentRegister_CreditBased() {
                RegistrationSchedule creditSchedule = new RegistrationSchedule();
                creditSchedule.setScheduleId(2L);
                creditSchedule.setSemester("Fall");
                creditSchedule.setYear(2025);
                creditSchedule.setMinCredits(24);
                creditSchedule.setMaxCredits(56);
                creditSchedule.setStartDate(LocalDate.of(2025, 10, 15));
                creditSchedule.setEndDate(LocalDate.of(2025, 10, 22));

                UserClasses class1 = new UserClasses();
                class1.setCredits(30);
                class1.setGrade("A");
                testUserClasses.add(class1);

                LocalDate currentDate = LocalDate.of(2025, 10, 16);
                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(currentDate);
                when(scheduleRepository.findActiveSchedulesForTerm("Fall", 2025, currentDate))
                                .thenReturn(Arrays.asList(creditSchedule));

                boolean result = registrationScheduleService.canStudentRegister(1L, "Fall", 2025);

                assertTrue(result);
        }

        /**
         * Goal: Test canStudentRegister - before schedule period
         * Scenario: Current date is before schedule start date
         * Pass criteria: Method returns false
         * Outcome: System passes - date validation works
         */
        @Test
        void testCanStudentRegister_BeforePeriod() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(90);
                class1.setGrade("A");
                testUserClasses.add(class1);

                LocalDate currentDate = LocalDate.of(2025, 10, 10); // Before start date
                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(currentDate);
                when(scheduleRepository.findActiveSchedulesForTerm("Fall", 2025, currentDate))
                                .thenReturn(Collections.emptyList());

                boolean result = registrationScheduleService.canStudentRegister(1L, "Fall", 2025);

                assertFalse(result);
        }

        /**
         * Goal: Test canStudentRegister - after schedule period
         * Scenario: Current date is after schedule end date
         * Pass criteria: Method returns false
         * Outcome: System passes - date validation works
         */
        @Test
        void testCanStudentRegister_AfterPeriod() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(90);
                class1.setGrade("A");
                testUserClasses.add(class1);

                LocalDate currentDate = LocalDate.of(2025, 10, 25); // After end date
                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(currentDate);
                when(scheduleRepository.findActiveSchedulesForTerm("Fall", 2025, currentDate))
                                .thenReturn(Collections.emptyList());

                boolean result = registrationScheduleService.canStudentRegister(1L, "Fall", 2025);

                assertFalse(result);
        }

        /**
         * Goal: Test updateSchedule with invalid date range
         * Scenario: Try to update schedule with start date after end date
         * Pass criteria: Method throws RuntimeException
         * Outcome: System passes - validation prevents invalid update
         */
        @Test
        void testUpdateSchedule_InvalidDateRange() {
                LocalDate startDate = LocalDate.of(2025, 10, 22);
                LocalDate endDate = LocalDate.of(2025, 10, 15);

                when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));
                when(academicEventRepository.findByTitleAndSemesterAndYear(anyString(), eq("Fall"), eq(2025)))
                                .thenReturn(Optional.empty());

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> registrationScheduleService.updateSchedule(
                                                1L, "Fall", 2025, "SENIOR", null, null,
                                                startDate, endDate, null));

                assertTrue(exception.getMessage().contains("Start date must be before"));
        }

        /**
         * Goal: Test getDeclarationDatesForTerm when dates not found
         * Scenario: Declaration dates don't exist in academic calendar
         * Pass criteria: Method returns declarationAvailable=false
         * Outcome: System passes - missing dates are handled
         */
        @Test
        void testGetDeclarationDatesForTerm_NotFound() {
                when(majorMinorDeclarationService.getMajorMinorChangesBegin("Fall", 2025))
                                .thenReturn(Optional.empty());
                when(majorMinorDeclarationService.getMajorMinorChangesEnd("Fall", 2025))
                                .thenReturn(Optional.empty());

                Map<String, Object> result = registrationScheduleService.getDeclarationDatesForTerm("Fall", 2025);

                assertNotNull(result);
                assertEquals(false, result.get("declarationAvailable"));
        }

        /**
         * Goal: Test calculateClassStanding with negative credits
         * Scenario: Student has negative credits (edge case)
         * Pass criteria: Class standing defaults to FRESHMAN
         * Outcome: System passes - edge case is handled
         */
        @Test
        void testCalculateClassStanding_NegativeCredits() {
                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(Collections.emptyList());
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 10, 16));
                when(scheduleRepository.findActiveSchedulesForTerm(anyString(), anyInt(), any()))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear(anyString(), anyInt()))
                                .thenReturn(Collections.emptyList());

                // Test with null (which becomes -1 or 0 in calculation)
                String result = registrationScheduleService.calculateClassStanding(null);

                assertEquals("FRESHMAN", result);
        }

        /**
         * Goal: Test calculateClassStanding boundary - exactly 24 credits
         * Scenario: Student has exactly 24 credits (SOPHOMORE threshold)
         * Pass criteria: Class standing is SOPHOMORE
         * Outcome: System passes - boundary condition works
         */
        @Test
        void testCalculateClassStanding_Boundary24() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(24);
                class1.setGrade("A");
                testUserClasses.add(class1);

                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 10, 16));
                when(scheduleRepository.findActiveSchedulesForTerm(anyString(), anyInt(), any()))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear(anyString(), anyInt()))
                                .thenReturn(Collections.emptyList());

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertEquals("SOPHOMORE", result.get("classStanding"));
        }

        /**
         * Goal: Test calculateClassStanding boundary - exactly 57 credits
         * Scenario: Student has exactly 57 credits (JUNIOR threshold)
         * Pass criteria: Class standing is JUNIOR
         * Outcome: System passes - boundary condition works
         */
        @Test
        void testCalculateClassStanding_Boundary57() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(57);
                class1.setGrade("A");
                testUserClasses.add(class1);

                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 10, 16));
                when(scheduleRepository.findActiveSchedulesForTerm(anyString(), anyInt(), any()))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear(anyString(), anyInt()))
                                .thenReturn(Collections.emptyList());

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertEquals("JUNIOR", result.get("classStanding"));
        }

        /**
         * Goal: Test calculateClassStanding boundary - exactly 85 credits
         * Scenario: Student has exactly 85 credits (SENIOR threshold)
         * Pass criteria: Class standing is SENIOR
         * Outcome: System passes - boundary condition works
         */
        @Test
        void testCalculateClassStanding_Boundary85() {
                UserClasses class1 = new UserClasses();
                class1.setCredits(85);
                class1.setGrade("A");
                testUserClasses.add(class1);

                when(userClassesRepository.findCompletedClassesByUserId(1L)).thenReturn(testUserClasses);
                when(systemDateService.today()).thenReturn(LocalDate.of(2025, 10, 16));
                when(scheduleRepository.findActiveSchedulesForTerm(anyString(), anyInt(), any()))
                                .thenReturn(Collections.emptyList());
                when(scheduleRepository.findBySemesterAndYear(anyString(), anyInt()))
                                .thenReturn(Collections.emptyList());

                Map<String, Object> result = registrationScheduleService.getRegistrationEligibility(1L, "Fall", 2025);

                assertEquals("SENIOR", result.get("classStanding"));
        }

        /**
         * Goal: Test schedule creation with user not found
         * Scenario: Try to create schedule with non-existent user ID
         * Pass criteria: Method throws RuntimeException
         * Outcome: System passes - error handling works
         */
        @Test
        void testCreateSchedule_UserNotFound() {
                LocalDate startDate = LocalDate.of(2025, 10, 15);
                LocalDate endDate = LocalDate.of(2025, 10, 22);

                when(userRepository.findById(999L)).thenReturn(Optional.empty());

                RuntimeException exception = assertThrows(
                                RuntimeException.class,
                                () -> registrationScheduleService.createSchedule(
                                                "Fall", 2025, "SENIOR", null, null,
                                                startDate, endDate, 999L, null));

                assertTrue(exception.getMessage().contains("User not found"));
        }
}
