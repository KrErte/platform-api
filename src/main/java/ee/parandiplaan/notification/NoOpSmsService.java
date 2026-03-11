package ee.parandiplaan.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "none", matchIfMissing = true)
@Slf4j
public class NoOpSmsService implements SmsService {

    @Override
    public void sendSms(String to, String message) {
        log.debug("SMS (no-op): to={}, message={}", to, message);
    }
}
