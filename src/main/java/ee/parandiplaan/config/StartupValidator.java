package ee.parandiplaan.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StartupValidator {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${app.cors.allowed-origins}")
    private String corsOrigins;

    @PostConstruct
    public void validate() {
        if (jwtSecret.contains("DevSecret")) {
            log.warn("⚠ JWT secret is using default development value. Set JWT_SECRET for production!");
        }

        if (datasourceUrl.contains("localhost")) {
            log.info("Database pointing to localhost — development mode.");
        }

        if (corsOrigins.contains("localhost")) {
            log.info("CORS allowing localhost origins — development mode.");
        }

        log.info("Pärandiplaan API startup validation complete.");
    }
}
