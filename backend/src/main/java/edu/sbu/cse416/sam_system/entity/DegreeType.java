package edu.sbu.cse416.sam_system.entity;

/**
 * TP: Used cursor to implement enum representing the degree types (BA, BS, Minor).
 * Enum representing the degree types (BA, BS, Minor).
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