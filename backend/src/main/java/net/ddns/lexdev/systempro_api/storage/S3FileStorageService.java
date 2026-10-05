package net.ddns.lexdev.systempro_api.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import net.ddns.lexdev.systempro_api.config.StorageProperties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@ConditionalOnProperty(
    name = "systempro.storage.provider",
    havingValue = "s3"
)
public class S3FileStorageService implements FileStorageService {

    private final S3Client client;
    private final StorageProperties properties;

    public S3FileStorageService(StorageProperties properties) {
        this.properties = properties;

        boolean hasAccessKey = properties.accessKey() != null
            && !properties.accessKey().isBlank();
        boolean hasSecretKey = properties.secretKey() != null
            && !properties.secretKey().isBlank();

        if (hasAccessKey != hasSecretKey) {
            throw new IllegalStateException(
                "STORAGE_ACCESS_KEY e STORAGE_SECRET_KEY devem ser informadas juntas."
            );
        }

        AwsCredentialsProvider credentialsProvider = hasAccessKey
            ? StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    properties.accessKey(),
                    properties.secretKey()
                )
            )
            : DefaultCredentialsProvider.create();

        var builder = S3Client.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(credentialsProvider);

        if (properties.endpoint() != null
            && !properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }

        if (properties.pathStyleAccess()) {
            builder.forcePathStyle(true);
        }

        this.client = builder.build();
    }

    @PreDestroy
    void close() {
        client.close();
    }

    @Override
    public void upload(
        String storageKey,
        Path file,
        String contentType
    ) {
        try {
            long size = Files.size(file);
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(storageKey)
                    .contentType(contentType)
                    .contentLength(size)
                    .build(),
                RequestBody.fromFile(file)
            );
        } catch (IOException e) {
            throw new UncheckedIOException(
                "Não foi possível preparar o arquivo para armazenamento.",
                e
            );
        }
    }

    @Override
    public Resource download(String storageKey) {
        return new InputStreamResource(
            client.getObject(
                GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(storageKey)
                    .build()
            )
        );
    }

    @Override
    public void delete(String storageKey) {
        client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .build()
        );
    }
}
