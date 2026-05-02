package com.fyp.floodmonitoring.enums;

import java.util.Locale;

/**
 * Canonical CRM / auth roles. Persisted in DB as {@link #getPersistenceValue()}
 * (lower snake-case: {@code admin}, {@code operations_manager}, …).
 */
public enum Role {
    ADMIN("Admin"),
    OPERATIONS_MANAGER("Operations Manager"),
    FIELD_TECHNICIAN("Field Technician"),
    NGO_VOLUNTEER("NGO Volunteer"),
    VIEWER("Viewer"),
    CUSTOMER("Customer");

    private final String displayLabel;

    Role(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    /** Value stored in {@code users.role}. */
    public String getPersistenceValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Role fromString(String s) {
        if (s == null || s.isBlank()) {
            return CUSTOMER;
        }
        String normalized = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Role.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return CUSTOMER;
        }
    }
}
