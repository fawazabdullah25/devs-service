package org.kstacks.devs.media.application;

import java.net.URI;

public interface VideoProvider {
    record CreatedAsset(String assetId) {}
    CreatedAsset createAsset(URI sourceUrl);
}
