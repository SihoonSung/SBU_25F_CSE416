package edu.sbu.cse416.sam_system.entity;

/**
 * Enum representing the status of a major/minor declaration.
 * 
 * TP: Used Cursor to implement major/minor declaration system
 */
public enum DeclarationStatus {
    PENDING("Pending Approval"),
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
