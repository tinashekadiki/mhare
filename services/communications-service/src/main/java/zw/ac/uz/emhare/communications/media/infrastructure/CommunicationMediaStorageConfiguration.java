package zw.ac.uz.emhare.communications.media.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** Configures S3-compatible Communications media storage. @author Tinashe K */
@Configuration
@EnableConfigurationProperties(CommunicationMediaStorageProperties.class)
public class CommunicationMediaStorageConfiguration {

  @Bean
  S3Client communicationMediaS3Client(CommunicationMediaStorageProperties properties) {
    return S3Client.builder()
        .endpointOverride(properties.endpoint())
        .region(Region.of(properties.region()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
        .serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess()).build())
        .build();
  }
}
