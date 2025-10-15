package edu.sbu.cse416.sam_system.entity;

/**
 * Enum representing the type of declaration (Major or Minor).
 * 
 * TP: Used Cursor to implement major/minor declaration system
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
