package org.kstacks.devs.content.application;

import org.kstacks.devs.content.api.ContentDtos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.kstacks.devs.content.api.ContentDtos.LocalizedText;

public final class ReferenceCatalog {
    private static final Map<String, ContentDtos.Topic> TOPICS = new LinkedHashMap<>();
    private static final Map<String, ContentDtos.Level> LEVELS = new LinkedHashMap<>();

    static {
        addTopic("web", "Web Development", "تطوير الويب");
        addTopic("backend", "Backend", "تطوير الخوادم");
        addTopic("git", "Git & Collaboration", "جِت والعمل الجماعي");
        addTopic("data", "Data", "البيانات");

        addLevel("getting-started", "Getting started", "تمهيدي");
        addLevel("builder", "Builder", "تطبيقي");
        addLevel("deep-dive", "Deep dive", "متقدم");
    }

    private ReferenceCatalog() {}

    private static void addTopic(String slug, String en, String ar) {
        TOPICS.put(slug, new ContentDtos.Topic("topic-" + slug, slug, new LocalizedText(en, ar)));
    }

    private static void addLevel(String slug, String en, String ar) {
        LEVELS.put(slug, new ContentDtos.Level("level-" + slug, slug, new LocalizedText(en, ar)));
    }

    public static ContentDtos.Topic topic(String slug) {
        return TOPICS.getOrDefault(slug, new ContentDtos.Topic("topic-" + slug, slug, new LocalizedText(slug, null)));
    }

    public static ContentDtos.Level level(String slug) {
        return LEVELS.getOrDefault(slug, new ContentDtos.Level("level-" + slug, slug, new LocalizedText(slug, null)));
    }

    public static List<ContentDtos.Topic> topics() { return List.copyOf(TOPICS.values()); }
    public static List<ContentDtos.Level> levels() { return List.copyOf(LEVELS.values()); }
}
