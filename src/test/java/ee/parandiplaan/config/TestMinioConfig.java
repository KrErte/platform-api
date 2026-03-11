package ee.parandiplaan.config;

import ee.parandiplaan.vault.StorageService;
import io.minio.MinioClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestMinioConfig {

    @Bean
    @Primary
    public MinioClient minioClient() {
        return Mockito.mock(MinioClient.class);
    }

    @Bean
    @Primary
    public StorageService storageService() {
        return Mockito.mock(StorageService.class);
    }
}
