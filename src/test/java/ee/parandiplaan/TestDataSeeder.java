package ee.parandiplaan;

import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import ee.parandiplaan.vault.VaultCategory;
import ee.parandiplaan.vault.VaultCategoryRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

/**
 * Seeds the 13 vault categories that Flyway V2 + V7 normally provide.
 * Used by integration tests since Flyway is disabled with H2.
 */
@TestConfiguration
public class TestDataSeeder {

    @Bean
    public ApplicationRunner seedVaultCategories(VaultCategoryRepository categoryRepository) {
        return args -> {
            if (categoryRepository.count() > 0) return;

            List<VaultCategory> categories = List.of(
                    cat("banking", "Pangandus", "Banking", "\uD83C\uDFE6", 1),
                    cat("insurance", "Kindlustus", "Insurance", "\uD83D\uDEE1\uFE0F", 2),
                    cat("property", "Kinnisvara", "Property", "\uD83C\uDFE0", 3),
                    cat("digital_accounts", "Digitaalsed kontod", "Digital Accounts", "\uD83D\uDCBB", 4),
                    cat("health", "Tervis", "Health", "\u2764\uFE0F", 5),
                    cat("legal", "\u00D5iguslikud dokumendid", "Legal Documents", "\u2696\uFE0F", 6),
                    cat("contracts", "Lepingud ja tellimused", "Contracts & Subscriptions", "\uD83D\uDCCB", 7),
                    cat("vehicles", "S\u00F5idukid", "Vehicles", "\uD83D\uDE97", 8),
                    cat("personal_wishes", "Isiklikud soovid", "Personal Wishes", "\uD83D\uDD4A\uFE0F", 9),
                    cat("important_contacts", "Olulised kontaktid", "Important Contacts", "\uD83D\uDCDE", 10),
                    cat("digital_identity", "Digitaalne identiteet", "Digital Identity", "\uD83E\uDEAA", 11),
                    cat("pets", "Lemmikloomad", "Pets", "\uD83D\uDC3E", 12),
                    cat("pensions", "Pensionid ja investeeringud", "Pensions & Investments", "\uD83D\uDCC8", 13)
            );
            categoryRepository.saveAll(categories);
        };
    }

    /**
     * Creates an admin user for testing. Call from test methods that need admin access.
     */
    public static User createAdminUser(UserRepository userRepository) {
        User admin = new User();
        admin.setEmail("admin@test.ee");
        admin.setPasswordHash(new BCryptPasswordEncoder().encode("AdminPass123"));
        admin.setFullName("Admin User");
        admin.setRole("ADMIN");
        admin.setEmailVerified(true);
        return userRepository.save(admin);
    }

    private static VaultCategory cat(String slug, String nameEt, String nameEn, String icon, int sortOrder) {
        VaultCategory c = new VaultCategory();
        c.setSlug(slug);
        c.setNameEt(nameEt);
        c.setNameEn(nameEn);
        c.setIcon(icon);
        c.setSortOrder(sortOrder);
        c.setFieldTemplate("[]");
        return c;
    }
}
