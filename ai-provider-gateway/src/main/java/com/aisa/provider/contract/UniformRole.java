package com.aisa.provider.contract;

/**
 * Role of a message in a uniform conversation request. Provider-agnostic so the same
 * request structure is presented regardless of the selected provider (Requirement 20.4).
 */
public enum UniformRole {
    SYSTEM,
    USER,
    ASSISTANT
}
