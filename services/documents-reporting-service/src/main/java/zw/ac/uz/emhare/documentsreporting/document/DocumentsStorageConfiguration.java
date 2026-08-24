package zw.ac.uz.emhare.documentsreporting.document;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import zw.ac.uz.emhare.documentsreporting.upload.MalwareScanProperties;

/**
 * @author Tinashe K
 */
@Configuration
@EnableConfigurationProperties({DocumentsStorageProperties.class, MalwareScanProperties.class})
public class DocumentsStorageConfiguration {

  @Bean
  S3Client documentsS3Client(DocumentsStorageProperties properties) {
    return S3Client.builder()
        .endpointOverride(URI.create(properties.endpoint()))
        .region(Region.of(properties.region()))
        .credentialsProvider(credentials(properties))
        .serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess()).build())
        .build();
  }

  @Bean
  S3Presigner documentsS3Presigner(DocumentsStorageProperties properties) {
    return S3Presigner.builder()
        .endpointOverride(URI.create(properties.endpoint()))
        .region(Region.of(properties.region()))
        .credentialsProvider(credentials(properties))
        .serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess()).build())
        .build();
  }

  private StaticCredentialsProvider credentials(DocumentsStorageProperties properties) {
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
  }
}
