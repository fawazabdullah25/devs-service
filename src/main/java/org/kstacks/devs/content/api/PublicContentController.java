package org.kstacks.devs.content.api;

import org.kstacks.devs.content.application.ContentService;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.SpokenLanguage;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devs/api/v1/public")
public class PublicContentController {
    private final ContentService service;

    public PublicContentController(ContentService service) {
        this.service = service;
    }

    @GetMapping("/home")
    public ContentDtos.Home home() {
        return service.home();
    }

    @GetMapping("/catalog")
    public ContentDtos.Catalog catalog(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) ContentKind kind,
        @RequestParam(required = false) String topic,
        @RequestParam(required = false) String level,
        @RequestParam(required = false) SpokenLanguage language
    ) {
        return service.catalog(query, kind, topic, level, language);
    }

    @GetMapping("/content/{slug}")
    public ContentDtos.LearningContent content(@PathVariable String slug, Authentication authentication) {
        return service.getPublished(slug, authentication);
    }
}
