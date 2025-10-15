package edu.sbu.cse416.sam_system.entity;

/**
 * Enum representing different types of degrees.
 * 
 * TP: Used Cursor to implement degree requirements management system
 */
public enum DegreeType {
    BA("Bachelor of Arts"),
    BS("Bachelor of Science"),
    MINOR("Minor");
    
    private final String displayName;
    
    DegreeType(String displayName) {
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
