package ee.parandiplaan.auth.eid;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "eid")
@Getter
@Setter
public class EIdProperties {

    private SmartId smartId = new SmartId();
    private MobileId mobileId = new MobileId();

    @Getter
    @Setter
    public static class SmartId {
        private String relyingPartyUuid = "00000000-0000-4000-8000-000000000000";
        private String relyingPartyName = "DEMO";
        private String hostUrl = "https://sid.demo.sk.ee/smart-id-rp/v2/";
    }

    @Getter
    @Setter
    public static class MobileId {
        private String relyingPartyUuid = "00000000-0000-0000-0000-000000000000";
        private String relyingPartyName = "DEMO";
        private String hostUrl = "https://tsp.demo.sk.ee/mid-api";
    }
}
