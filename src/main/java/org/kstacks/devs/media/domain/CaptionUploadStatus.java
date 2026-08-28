package org.kstacks.devs.media.domain;

/** Lifecycle of a standalone WebVTT upload before it is referenced by media. */
public enum CaptionUploadStatus {
    UPLOADING,
    COMPLETED,
    ATTACHED,
    DELETED
}
