package ee.parandiplaan.config;

import ee.parandiplaan.auth.eid.EIdProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class StartupValidator {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${app.cors.allowed-origins}")
    private String corsOrigins;

    @Value("${app.vault-escrow.master-key:DevEscrowKeyThatIsAtLeast32BytesLong!!}")
    private String vaultEscrowMasterKey;

    private final EIdProperties eIdProperties;

    @PostConstruct
    public void validate() {
        if (jwtSecret.contains("DevSecret")) {
            log.warn("JWT secret is using default development value. Set JWT_SECRET for production!");
        }

        if (datasourceUrl.contains("localhost")) {
            log.info("Database pointing to localhost — development mode.");
        }

        if (corsOrigins.contains("localhost")) {
            log.info("CORS allowing localhost origins — development mode.");
        }

        // eID validation
        String sidHost = eIdProperties.getSmartId().getHostUrl();
        String midHost = eIdProperties.getMobileId().getHostUrl();
        if (sidHost != null && sidHost.contains("demo")) {
            log.warn("Smart-ID is using DEMO endpoint ({}). Set SMARTID_HOST_URL for production!", sidHost);
        }
        if (midHost != null && midHost.contains("demo")) {
            log.warn("Mobile-ID is using DEMO endpoint ({}). Set MOBILEID_HOST_URL for production!", midHost);
        }
        if ("DEMO".equals(eIdProperties.getSmartId().getRelyingPartyName())) {
            log.warn("Smart-ID relying party name is 'DEMO'. Set SMARTID_RP_NAME and SMARTID_RP_UUID for production!");
        }
        if ("DEMO".equals(eIdProperties.getMobileId().getRelyingPartyName())) {
            log.warn("Mobile-ID relying party name is 'DEMO'. Set MOBILEID_RP_NAME and MOBILEID_RP_UUID for production!");
        }

        // Vault escrow key validation
        if (vaultEscrowMasterKey.contains("DevEscrow")) {
            log.warn("Vault escrow master key is using default value. Set VAULT_ESCROW_MASTER_KEY for production!");
        }

        log.info("Pärandiplaan API startup validation complete.");
    }
}
