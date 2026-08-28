package org.kstacks.devs.media.application;

import org.kstacks.devs.media.domain.MediaCaptionTrack;

import java.util.List;

public interface StaticHlsPackageValidator {
    /** Validates the immutable package and returns its duration derived from the renditions. */
    ValidationResult validate(String manifestPath, List<MediaCaptionTrack> captions);

    /** Validates standalone VTT files before they can be referenced by media. */
    void validateCaptions(List<MediaCaptionTrack> captions);

    record ValidationResult(long durationSeconds) {
        public ValidationResult {
            if (durationSeconds <= 0) throw new IllegalArgumentException("Validated media duration must be positive");
        }
    }
}
