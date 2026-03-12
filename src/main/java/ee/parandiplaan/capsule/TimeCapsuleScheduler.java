package ee.parandiplaan.capsule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TimeCapsuleScheduler {

    private final TimeCapsuleService capsuleService;

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Tallinn")
    public void deliverDueCapsules() {
        log.info("Starting daily time capsule delivery check...");
        capsuleService.deliverDateCapsules();
        log.info("Daily time capsule delivery check completed");
    }
}
