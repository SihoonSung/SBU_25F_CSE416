package edu.sbu.cse416.sam_system.entity;

/**
 * TP: Used cursor to implement enum representing the status of a major/minor declaration.
 * Enum representing the status of a major/minor declaration.
 */
public enum DeclarationStatus {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn");
    
    private final String displayName;
    
    DeclarationStatus(String displayName) {
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