package edu.sbu.cse416.sam_system.util;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

/**
 * TP: Used cursor to implement time conflict checker for class schedules.
 * Simple time conflict checker for class schedules.
 * Parses schedule strings in format: "MW 05:00-06:20PM", "TR 02:00-03:20PM"
 * 
 * Day codes: M=Monday, T=Tuesday, W=Wednesday, R=Thursday, F=Friday
 */
public class TimeConflictChecker {
    
    /**
     * Check if two schedule strings have time conflicts
     */
    // TP: Used cursor to detect conflicts between two schedule strings
    public static boolean hasTimeConflict(String schedule1, String schedule2) {
        if (schedule1 == null || schedule2 == null || schedule1.trim().isEmpty() || schedule2.trim().isEmpty()) {
            return false;
        }
        
        try {
            // Parse both schedules into multiple time slots
            List<ScheduleInfo> slots1 = parseAllTimeSlots(schedule1);
            List<ScheduleInfo> slots2 = parseAllTimeSlots(schedule2);
            
            // Check for conflicts between any time slots
            for (ScheduleInfo info1 : slots1) {
                for (ScheduleInfo info2 : slots2) {
                    // Check for overlapping days
                    Set<DayOfWeek> commonDays = new HashSet<>(info1.days);
                    commonDays.retainAll(info2.days);
                    
                    if (!commonDays.isEmpty()) {
                        // Check if time ranges overlap
                        if (!(info1.endTime.isBefore(info2.startTime) || info1.startTime.isAfter(info2.endTime))) {
                            return true; // Found a conflict
                        }
                    }
                }
            }
            return false; // No conflicts found
            
        } catch (Exception e) {
            System.err.println("Error parsing schedules: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Parse all time slots from a schedule string
     * @param schedule Schedule string like "MW 05:00-06:20PM, TR 02:00-03:20PM"
     * @return List of ScheduleInfo objects
     */
    // TP: Used cursor to parse multiple time slots from a schedule string
    private static List<ScheduleInfo> parseAllTimeSlots(String schedule) {
        List<ScheduleInfo> timeSlots = new ArrayList<>();
        
        // Split by comma to handle multiple time slots
        String[] parts = schedule.trim().split(",");
        
        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty()) {
                try {
                    ScheduleInfo info = parseSingleTimeSlot(part);
                    timeSlots.add(info);
                } catch (Exception e) {
                    System.err.println("Failed to parse time slot: " + part + " - " + e.getMessage());
                }
            }
        }
        
        return timeSlots;
    }
    
    /**
     * Parse a single time slot like "MW 05:00-06:20PM" or "RECT 03:30-04:25PM"
     */
    // TP: Used cursor to parse a single time slot segment
    private static ScheduleInfo parseSingleTimeSlot(String timeSlot) {
        String[] parts = timeSlot.trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid schedule format: " + timeSlot);
        }
        
        String daysStr = parts[0];
        String timeStr = parts[1];
        
        // Parse days - handle special cases like RECT, RECR
        Set<DayOfWeek> days = parseDaysString(daysStr);
        
        // Parse time
        String[] timeParts = timeStr.split("-");
        
        if (timeParts.length != 2) {
            throw new IllegalArgumentException("Invalid time format: " + timeStr + " (length: " + timeParts.length + ")");
        }
        
        // Extract AM/PM from the end time
        String startTimeStr = timeParts[0];
        String endTimeStr = timeParts[1];
        
        // Find AM/PM from the original time string
        String ampm = timeStr.toUpperCase().contains("PM") ? "PM" : "AM";
        
        // If start time doesn't have AM/PM, add it
        if (!startTimeStr.toUpperCase().endsWith("AM") && !startTimeStr.toUpperCase().endsWith("PM")) {
            startTimeStr = startTimeStr + ampm;
        }
        
        // If end time doesn't have AM/PM, add it
        if (!endTimeStr.toUpperCase().endsWith("AM") && !endTimeStr.toUpperCase().endsWith("PM")) {
            endTimeStr = endTimeStr + ampm;
        }
        
        LocalTime startTime = parseTime(startTimeStr);
        LocalTime endTime = parseTime(endTimeStr);
        
        return new ScheduleInfo(days, startTime, endTime);
    }
    
    
    /**
     * Parse days string like "MW", "RECT", "RECR", "TR"
     */
    // TP: Used cursor to parse day codes (e.g., MW, TR, RECT)
    private static Set<DayOfWeek> parseDaysString(String daysStr) {
        Set<DayOfWeek> days = new HashSet<>();
        
        // Handle special cases first
        if (daysStr.equalsIgnoreCase("RECT")) {
            days.add(DayOfWeek.TUESDAY);
            return days;
        }
        if (daysStr.equalsIgnoreCase("RECR")) {
            days.add(DayOfWeek.THURSDAY);
            return days;
        }
        
        // Handle regular day codes
        for (char c : daysStr.toCharArray()) {
            DayOfWeek day = parseDay(c);
            if (day != null) {
                days.add(day);
            }
        }
        
        return days;
    }
    
    /**
     * Parse single day character
     */
    // TP: Used cursor to map a single day character to DayOfWeek
    private static DayOfWeek parseDay(char dayChar) {
        switch (Character.toUpperCase(dayChar)) {
            case 'M': return DayOfWeek.MONDAY;
            case 'T': return DayOfWeek.TUESDAY;
            case 'W': return DayOfWeek.WEDNESDAY;
            case 'R': return DayOfWeek.THURSDAY;
            case 'F': return DayOfWeek.FRIDAY;
            default: return null;
        }
    }
    
    /**
     * Parse time string like "05:00PM" or "06:20PM"
     */
    // TP: Used cursor to parse time with AM/PM into 24-hour LocalTime
    private static LocalTime parseTime(String timeStr) {
        boolean isPM = timeStr.toUpperCase().endsWith("PM");
        boolean isAM = timeStr.toUpperCase().endsWith("AM");
        
        String timeOnly;
        if (isPM || isAM) {
            timeOnly = timeStr.substring(0, timeStr.length() - 2); // Remove AM/PM
        } else {
            timeOnly = timeStr; // No AM/PM, use as is
        }
        
        String[] parts = timeOnly.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid time format: " + timeStr + " (parts length: " + parts.length + ")");
        }
        
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        
        // Convert to 24-hour format
        if (isPM && hour != 12) {
            hour += 12;
        } else if (isAM && hour == 12) {
            hour = 0;
        }
        
        return LocalTime.of(hour, minute);
    }
    
    /**
     * Simple schedule info holder
     */
    private static class ScheduleInfo {
        final Set<DayOfWeek> days;
        final LocalTime startTime;
        final LocalTime endTime;
        
        // TP: Used cursor to construct schedule info value object
        ScheduleInfo(Set<DayOfWeek> days, LocalTime startTime, LocalTime endTime) {
            this.days = days;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
    
}