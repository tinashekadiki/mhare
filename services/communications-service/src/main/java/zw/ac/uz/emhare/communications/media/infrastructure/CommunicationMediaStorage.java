package zw.ac.uz.emhare.communications.media.infrastructure;

import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Minimal S3 adapter for governed public media. @author Tinashe K */
@Component
public class CommunicationMediaStorage {

  private final S3Client s3Client;
  private final CommunicationMediaStorageProperties properties;

  public CommunicationMediaStorage(
      S3Client s3Client, CommunicationMediaStorageProperties properties) {
    this.s3Client = s3Client;
    this.properties = properties;
  }

  @PostConstruct
  void ensureBucket() {
    try {
      ensureBucketExists();
    } catch (RuntimeException exception) {
      // Runtime environments may start before object storage. Uploads surface the dependency error.
    }
  }

  public void store(String key, String contentType, byte[] bytes) {
    if (bytes.length > maximumBytes()) {
      throw new IllegalArgumentException(
          "Media file exceeds the maximum size of " + maximumBytes() + " bytes.");
    }
    ensureBucketExists();
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .contentType(contentType)
            .build(),
        RequestBody.fromBytes(bytes));
  }

  public byte[] read(String key) {
    return s3Client
        .getObjectAsBytes(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build())
        .asByteArray();
  }

  public long maximumBytes() {
    return Optional.of(properties.maximumBytes()).filter(value -> value > 0).orElse(10_485_760L);
  }

  private void ensureBucketExists() {
    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
    } catch (S3Exception exception) {
      if (exception.statusCode() != 404) {
        throw exception;
      }
      s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
    }
  }
}
