package org.kstacks.devs.content.domain;

/**
 * Durable state used by the trash purger between its database claim and
 * external-object cleanup. A claim prevents a restore or a new child write
 * while an idempotent object cleanup is being retried.
 */
public enum TrashPurgeState {
    NONE,
    CLAIMED
}
