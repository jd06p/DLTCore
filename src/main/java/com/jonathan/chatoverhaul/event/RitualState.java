package com.jonathan.chatoverhaul.event;

/**
 * States for the Entity303 (evil_user_0) ritual/bossfight, per the
 * requested design. FAILED, CANCELLED, and COMPLETED are all terminal -
 * once reached, Entity303RitualManager guarantees no further processing
 * happens for that ritual instance (checked via RitualInstance#isTerminal()).
 */
public enum RitualState {
    INACTIVE,
    STARTING,
    ACTIVE,
    FAILED,
    CANCELLED,
    COMPLETED
}
