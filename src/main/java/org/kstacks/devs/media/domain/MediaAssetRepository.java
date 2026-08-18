package org.kstacks.devs.media.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAssetEntity, UUID> {
    Optional<MediaAssetEntity> findByProviderAssetId(String providerAssetId);
    Optional<MediaAssetEntity> findBySourceObjectKey(String sourceObjectKey);
    Optional<MediaAssetEntity> findByPlaybackPath(String playbackPath);
    long countByStatus(MediaStatus status);
}
