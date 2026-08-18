package org.kstacks.devs.media.application;

import org.kstacks.devs.media.domain.MediaCaptionTrack;

import java.util.List;

public interface StaticHlsPackageValidator {
    void validate(String manifestPath, List<MediaCaptionTrack> captions);
}
