package cn.voidhaven.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class MediaStorageConfiguration {
    @Bean(destroyMethod = "close")
    S3Client voidhavenS3Client(
            @Value("${voidhaven.media-endpoint:}") String endpoint,
            @Value("${voidhaven.media-access-key:}") String accessKey,
            @Value("${voidhaven.media-secret-key:}") String secretKey,
            @Value("${voidhaven.media-region:us-east-1}") String region,
            @Value("${voidhaven.media-force-path-style:true}") boolean forcePathStyle,
            Environment environment) {
        if (endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank()) throw new IllegalStateException("S3-compatible media configuration is required");
        URI uri = URI.create(endpoint);
        if (isProduction(environment) && !"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalStateException("Production media endpoint must use HTTPS");
        return S3Client.builder()
                .endpointOverride(uri)
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(forcePathStyle)
                .build();
    }

    private boolean isProduction(Environment environment) {
        for (String profile : environment.getActiveProfiles()) if ("production".equals(profile)) return true;
        return false;
    }
}
