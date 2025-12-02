package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.repository.WaitlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Service for managing waitlist operations.
 * Handles waitlist cleanup based on academic calendar dates.
 * 
 * TP: Used Cursor to implement waitlist service with scheduled cleanup.
 */
@Service
@Transactional
public class WaitlistService {
    
    @Autowired
    private WaitlistRepository waitlistRepository;
    
    @Autowired
    private RegistrationScheduleService registrationScheduleService;
    
    @Autowired
    private SystemDateService systemDateService;
    
    /**
     * Process waitlist cleanup based on academic calendar dates.
     * This method checks if waitlist_process_ends date has passed for any term
     * and deletes all waitlist entries for that term.
     * 
     * This method can be called manually or scheduled to run periodically.
     */
    public void processWaitlistCleanup() {
        LocalDate currentDate = systemDateService.today();
        
        // Get current term
        java.util.Map<String, Object> currentTerm = registrationScheduleService.getCurrentTerm(currentDate);
        if (currentTerm == null) {
            return; // Not in any term period
        }
        
        String currentSemester = (String) currentTerm.get("semester");
        Integer currentYear = (Integer) currentTerm.get("year");
        
        // Get next term from current term
        java.util.Map<String, Object> nextTerm = registrationScheduleService.getNextTerm(currentSemester, currentYear);
        String nextSemester = (String) nextTerm.get("semester");
        Integer nextYear = (Integer) nextTerm.get("year");
        
        // Check if waitlist_process_ends date has passed for the next term
        java.util.Optional<LocalDate> waitlistProcessEnds = registrationScheduleService.getWaitlistProcessEndsDate(nextSemester, nextYear);
        
        if (waitlistProcessEnds.isPresent()) {
            LocalDate processEndsDate = waitlistProcessEnds.get();
            
            // If current date is after waitlist_process_ends, delete all waitlist entries for that term
            if (currentDate.isAfter(processEndsDate)) {
                // Delete all waitlist entries for the next term
                waitlistRepository.deleteBySemesterAndYear(nextSemester, nextYear);
            }
        }
    }
    
    /**
     * Scheduled task to process waitlist cleanup daily at midnight
     * Checks if waitlist_process_ends date has passed and deletes waitlist entries accordingly
     */
    @Scheduled(cron = "0 0 0 * * ?") // Run daily at midnight
    public void scheduledWaitlistCleanup() {
        try {
            processWaitlistCleanup();
        } catch (Exception e) {
            // Log error but don't fail the scheduled task
            System.err.println("[WaitlistService] Error in scheduled waitlist cleanup: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
}

