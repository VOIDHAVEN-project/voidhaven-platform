package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class S3MediaStorage implements MediaStorage {
    private final S3Client s3;
    private final String bucket;

    public S3MediaStorage(S3Client s3, @Value("${voidhaven.media-bucket:}") String bucket) {
        if (bucket.isBlank()) throw new IllegalStateException("Media bucket is required");
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, String mediaType, byte[] bytes) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(mediaType).build(), RequestBody.fromBytes(bytes));
    }

    @Override
    public StoredObject get(String key) {
        try {
            ResponseBytes<GetObjectResponse> response = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), ResponseTransformer.toBytes());
            return new StoredObject(response.asByteArray(), response.response().contentType());
        } catch (NoSuchKeyException error) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "媒体不存在或当前不可见。");
        } catch (S3Exception error) {
            if (error.statusCode() == 404) throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "媒体不存在或当前不可见。");
            throw error;
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(request -> request.bucket(bucket).key(key));
    }
}
