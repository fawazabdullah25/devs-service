package org.kstacks.devs.media.infrastructure;

import org.kstacks.devs.config.R2Properties;
import org.kstacks.devs.media.application.ObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
public class R2Configuration {
    @Bean
    @ConditionalOnProperty(name = "devs.r2.enabled", havingValue = "true")
    S3Client r2Client(R2Properties properties) {
        return S3Client.builder()
            .endpointOverride(URI.create(require(properties.endpoint(), "R2 endpoint")))
            .region(Region.of(require(properties.region(), "R2 region")))
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = "devs.r2.enabled", havingValue = "true")
    S3Presigner r2Presigner(R2Properties properties) {
        return S3Presigner.builder()
            .endpointOverride(URI.create(require(properties.endpoint(), "R2 endpoint")))
            .region(Region.of(require(properties.region(), "R2 region")))
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = "devs.r2.enabled", havingValue = "true")
    ObjectStorage r2ObjectStorage(S3Client client, S3Presigner presigner, R2Properties r2, org.kstacks.devs.config.MediaProperties media) {
        return new R2ObjectStorage(client, presigner, r2.bucket(), media.uploadExpiryMinutes());
    }

    private StaticCredentialsProvider credentials(R2Properties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
            require(properties.accessKeyId(), "R2 access key"), require(properties.secretAccessKey(), "R2 secret key")
        ));
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required when R2 is enabled");
        return value;
    }
}
