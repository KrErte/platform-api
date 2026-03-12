package ee.parandiplaan.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    private final WebClient webClient;
    private final String fromEmail;
    private final String appUrl;
    private final boolean enabled;

    public EmailService(
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from-email:noreply@parandiplaan.ee}") String fromEmail,
            @Value("${app.url:http://localhost:8080}") String appUrl) {
        this.fromEmail = fromEmail;
        this.appUrl = appUrl;
        this.enabled = apiKey != null && !apiKey.isBlank();

        this.webClient = WebClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        if (!enabled) {
            log.warn("Resend API key not configured — emails will be logged only");
        }
    }

    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        String subject = "Kinnita oma e-posti aadress — Pärandiplaan";
        String verifyUrl = appUrl + "/api/v1/auth/verify-email?token=" + token;
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Tere, %s!</h2>
                    <p>Aitäh, et liitusid Parandiplaaniga. Palun kinnita oma e-posti aadress:</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #2D6A4F; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Kinnita e-post
                        </a>
                    </p>
                    <p style="color: #666; font-size: 14px;">Kui sa ei loonud kontot, ignoreeri seda kirja.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(fullName, verifyUrl);

        sendEmail(toEmail, subject, html);
    }

    public void sendPasswordResetEmail(String toEmail, String fullName, String token) {
        String subject = "Parooli lähtestamine — Pärandiplaan";
        String resetUrl = appUrl + "/reset-password.html?token=" + token;
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Parooli lähtestamine</h2>
                    <p>Tere, %s!</p>
                    <p>Saime taotluse sinu parooli lähtestamiseks. Kliki alloleval nupul, et valida uus parool:</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #2D6A4F; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Lähtesta parool
                        </a>
                    </p>
                    <p style="color: #DC2626; font-weight: bold; font-size: 14px;">⚠️ Hoiatus: Parooli lähtestamisel kaotad ligipääsu kõigile krüpteeritud tresori andmetele. Seda toimingut ei saa tagasi võtta.</p>
                    <p style="color: #666; font-size: 14px;">See link kehtib 1 tunni. Kui sa ei taotlenud parooli lähtestamist, ignoreeri seda kirja.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(fullName, resetUrl);

        sendEmail(toEmail, subject, html);
    }

    public void sendInviteEmail(String toEmail, String contactName, String ownerName, String inviteToken) {
        String subject = ownerName + " lisas sind usalduskontaktiks — Pärandiplaan";
        String acceptUrl = appUrl + "/invite/" + inviteToken;
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Tere, %s!</h2>
                    <p><strong>%s</strong> on lisanud sind oma usalduskontaktiks Parandiplaani platvormil.</p>
                    <p>See tähendab, et vajadusel on sul juurdepääs tema olulistele andmetele ja dokumentidele.</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #2D6A4F; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Võta kutse vastu
                        </a>
                    </p>
                    <p style="color: #666; font-size: 14px;">Kui sa ei tunne seda isikut, ignoreeri seda kirja.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(contactName, ownerName, acceptUrl);

        sendEmail(toEmail, subject, html);
    }

    public void sendHandoverRequestEmail(String toEmail, String ownerName, String contactName, String reason) {
        String subject = "Üleandmistaotlus — " + contactName + " soovib juurdepääsu";
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Üleandmistaotlus</h2>
                    <p>Tere, %s!</p>
                    <p>Sinu usalduskontakt <strong>%s</strong> on esitanud üleandmistaotluse.</p>
                    %s
                    <p>Sul on <strong>72 tundi</strong> aega vastata. Kui sa ei vasta, siis taotlus aegub.</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #2D6A4F; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Vaata taotlust
                        </a>
                    </p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(
                ownerName,
                contactName,
                reason != null ? "<p><em>Põhjus: " + reason + "</em></p>" : "",
                appUrl
        );

        sendEmail(toEmail, subject, html);
    }

    public void sendHandoverApprovedEmail(String toEmail, String contactName, String ownerName) {
        String subject = "Üleandmistaotlus kinnitatud — Pärandiplaan";
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Taotlus kinnitatud</h2>
                    <p>Tere, %s!</p>
                    <p><strong>%s</strong> on kinnitanud sinu üleandmistaotluse. Sul on nüüd juurdepääs tema andmetele.</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #2D6A4F; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Ava Pärandiplaan
                        </a>
                    </p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(contactName, ownerName, appUrl);

        sendEmail(toEmail, subject, html);
    }

    public void sendHandoverDeniedEmail(String toEmail, String contactName, String ownerName) {
        String subject = "Üleandmistaotlus keelatud — Pärandiplaan";
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Taotlus keelatud</h2>
                    <p>Tere, %s!</p>
                    <p><strong>%s</strong> on keeldunud sinu üleandmistaotlusest.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(contactName, ownerName);

        sendEmail(toEmail, subject, html);
    }

    public void sendSharedVaultEmail(String toEmail, String contactName, String ownerName, String sharedUrl) {
        String subject = "Juurdepääs " + ownerName + " tresori andmetele — Pärandiplaan";
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Tere, %s!</h2>
                    <p><strong>%s</strong> on andnud sulle juurdepääsu oma Pärandiplaani tresori andmetele.</p>
                    <p>Kliki alloleval nupul, et vaadata jagatud andmeid:</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #2D6A4F; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Ava jagatud tresor
                        </a>
                    </p>
                    <p style="color: #DC2626; font-weight: bold; font-size: 14px;">⚠️ See link sisaldab tundlikku informatsiooni. Ära jaga seda teistega.</p>
                    <p style="color: #666; font-size: 14px;">Link kehtib 30 päeva. Pärast seda pead taotlema uue juurdepääsu.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(contactName, ownerName, sharedUrl);

        sendEmail(toEmail, subject, html);
    }

    public void sendAccountDeletedEmail(String toEmail, String fullName) {
        String subject = "Konto kustutatud — Pärandiplaan";
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Konto kustutatud</h2>
                    <p>Tere, %s!</p>
                    <p>Sinu Pärandiplaani konto ja kõik sellega seotud andmed on edukalt kustutatud.</p>
                    <p>Kui sa ei soovinud oma kontot kustutada, võta meiega kohe ühendust.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(fullName);

        sendEmail(toEmail, subject, html);
    }

    public void sendExpirationReminder(String toEmail, String fullName,
                                       String categoryName, int daysAhead, LocalDate expiryDate) {
        String subject = "Meeldetuletus: " + categoryName + " aegub " + daysAhead + " paeva parast";
        String formattedDate = expiryDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Meeldetuletus</h2>
                    <p>Tere, %s!</p>
                    <p>Sinu <strong>%s</strong> kirje aegub <strong>%d paeva parast</strong> (%s).</p>
                    <p>Soovitame kontrollida ja vajadusel uuendada seda kirjet oma Parandiplaanis.</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #2D6A4F; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Ava Parandiplaan
                        </a>
                    </p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Parandiplaan — Sinu digitaalne parand, turvaliselt korraldatud</p>
                </div>
                """.formatted(fullName, categoryName, daysAhead, formattedDate, appUrl);

        sendEmail(toEmail, subject, html);
    }

    public void sendTimeCapsuleEmail(String toEmail, String recipientName, String senderName) {
        String subject = "Sulle on saabunud ajakapsel — " + senderName;
        String html = """
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #1B4332;">Ajakapsel saabunud!</h2>
                    <p>Tere, %s!</p>
                    <p><strong>%s</strong> on jätnud sulle isikliku sõnumi Pärandiplaani ajakapsli kaudu.</p>
                    <p>Sõnumi nägemiseks ava oma jagatud tresori link, mis saadeti sulle varem e-postile.</p>
                    <p style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background: #2D6A4F; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: bold;">
                            Ava Pärandiplaan
                        </a>
                    </p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    <p style="color: #999; font-size: 12px;">Pärandiplaan — Sinu digitaalne pärand, turvaliselt korraldatud</p>
                </div>
                """.formatted(recipientName, senderName, appUrl);

        sendEmail(toEmail, subject, html);
    }

    public void sendEmail(String to, String subject, String html) {
        if (!enabled) {
            log.info("EMAIL (dry-run) to={}, subject={}", to, subject);
            return;
        }

        try {
            Map<String, Object> body = Map.of(
                    "from", fromEmail,
                    "to", to,
                    "subject", subject,
                    "html", html
            );

            webClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(response -> log.info("Email sent to {}: {}", to, subject))
                    .doOnError(error -> log.error("Failed to send email to {}: {}", to, error.getMessage()))
                    .subscribe();
        } catch (Exception e) {
            log.error("Email sending failed for {}: {}", to, e.getMessage());
        }
    }
}
