package org.kstacks.devs.media.domain;

/**
 * Kept as an internal persistence discriminator while old V1-V10 schemas are
 * migrated. New media is always static HLS; this value is not exposed by the
 * API.
 */
public enum MediaProvider { STATIC_HLS }
