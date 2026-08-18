package org.kstacks.devs.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class MediaCaptionTrack {
    @Column(nullable = false, length = 35)
    private String language;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false)
    private String path;

    @Column(name = "is_default", nullable = false)
    private boolean defaultTrack;

    protected MediaCaptionTrack() {}

    public MediaCaptionTrack(String language, String label, String path, boolean defaultTrack) {
        this.language = language;
        this.label = label;
        this.path = path;
        this.defaultTrack = defaultTrack;
    }

    public String getLanguage() { return language; }
    public String getLabel() { return label; }
    public String getPath() { return path; }
    public boolean isDefaultTrack() { return defaultTrack; }
}
