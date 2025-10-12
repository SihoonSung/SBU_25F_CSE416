package edu.sbu.cse416.sam_system.entity;

/**
 * Enum representing the different user roles in the SAM system.
 * 
 * TP: Used Cursor to implement user role definitions
 */
public enum UserRole {
    STUDENT("Student"),
    INSTRUCTOR("Instructor"), 
    ADVISOR("Advisor"),
    REGISTRAR("Registrar");
    
    private final String displayName;
    
    UserRole(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
