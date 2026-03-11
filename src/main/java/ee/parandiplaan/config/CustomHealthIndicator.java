package ee.parandiplaan.config;

import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();

        // Check database
        boolean dbUp = checkDatabase();

        // Check MinIO
        boolean minioUp = checkMinio();

        if (dbUp && minioUp) {
            builder.up();
        } else {
            builder.down();
        }

        builder.withDetail("database", dbUp ? "UP" : "DOWN");
        builder.withDetail("minio", minioUp ? "UP" : "DOWN");

        return builder.build();
    }

    private boolean checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(3);
        } catch (Exception e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkMinio() {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            log.warn("MinIO health check failed: {}", e.getMessage());
            return false;
        }
    }
}
