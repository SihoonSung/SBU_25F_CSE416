package edu.sbu.cse416.sam_system.entity;

/**
 * TP: Used cursor to implement enum representing the type of declaration (Major or Minor).
 * Enum representing the type of declaration (Major or Minor).
 */
public enum DeclarationType {
    MAJOR("Major"),
    MINOR("Minor");
    
    private final String displayName;
    
    DeclarationType(String displayName) {
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