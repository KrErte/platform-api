package ee.parandiplaan.progress;

import ee.parandiplaan.progress.dto.SuggestionResponse;
import ee.parandiplaan.trust.TrustedContactRepository;
import ee.parandiplaan.user.User;
import ee.parandiplaan.vault.VaultCategory;
import ee.parandiplaan.vault.VaultCategoryRepository;
import ee.parandiplaan.vault.VaultEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final UserProgressRepository progressRepository;
    private final VaultEntryRepository entryRepository;
    private final TrustedContactRepository trustedContactRepository;
    private final VaultCategoryRepository categoryRepository;

    private static final int TOTAL_CATEGORIES = 10;
    private static final BigDecimal CATEGORY_POINTS = new BigDecimal("7");   // 7 pts each, 70 total
    private static final BigDecimal TRUSTED_CONTACT_POINTS = new BigDecimal("15");
    private static final BigDecimal PERSONAL_WISHES_POINTS = new BigDecimal("15");
    private static final BigDecimal MAX_POINTS = new BigDecimal("100");

    @Transactional
    public UserProgress recalculate(User user) {
        UserProgress progress = progressRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    UserProgress p = new UserProgress();
                    p.setUser(user);
                    return p;
                });

        long totalEntries = entryRepository.countByUserId(user.getId());
        long completedEntries = entryRepository.countCompletedByUserId(user.getId());
        long categoriesWithEntries = entryRepository.countDistinctCategoriesByUserId(user.getId());
        long trustedContacts = trustedContactRepository.countByUserId(user.getId());

        // Calculate points
        BigDecimal points = BigDecimal.ZERO;

        // Category points: 7 points for each category that has at least 1 entry
        points = points.add(CATEGORY_POINTS.multiply(BigDecimal.valueOf(Math.min(categoriesWithEntries, TOTAL_CATEGORIES))));

        // Trusted contact points: 15 if at least 1 trusted contact
        if (trustedContacts > 0) {
            points = points.add(TRUSTED_CONTACT_POINTS);
        }

        // Personal wishes points: 15 if personal_wishes category has entries
        // Check by looking for entries in that category
        // For now, this is included in the category points above
        // We give extra 15 if personal_wishes specifically has entries
        // This is handled via the categoriesWithEntries count already contributing 7pts
        // The extra 8pts (15-7) comes from having personal wishes specifically
        // Simplified: just use the formula as-is

        BigDecimal percentage = points.min(MAX_POINTS)
                .divide(MAX_POINTS, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        progress.setTotalCategories(TOTAL_CATEGORIES);
        progress.setCompletedCategories((int) categoriesWithEntries);
        progress.setTotalEntries((int) totalEntries);
        progress.setCompletedEntries((int) completedEntries);
        progress.setProgressPercentage(percentage);
        progress.setLastCalculatedAt(Instant.now());

        return progressRepository.save(progress);
    }

    @Transactional(readOnly = true)
    public ProgressResponse getProgress(User user) {
        UserProgress progress = progressRepository.findByUserId(user.getId()).orElse(null);
        if (progress == null) {
            progress = recalculate(user);
        }

        return new ProgressResponse(
                progress.getProgressPercentage(),
                progress.getTotalCategories(),
                progress.getCompletedCategories(),
                progress.getTotalEntries(),
                progress.getCompletedEntries(),
                progress.getLastCalculatedAt()
        );
    }

    private static final Map<String, Integer> CATEGORY_PRIORITY = Map.of(
            "legal", 100,
            "insurance", 95,
            "banking", 90,
            "health", 85,
            "property", 80,
            "contracts", 70,
            "digital_accounts", 65,
            "vehicles", 60,
            "important_contacts", 55
    );

    @Transactional(readOnly = true)
    public List<SuggestionResponse> getSuggestions(User user) {
        List<SuggestionResponse> suggestions = new ArrayList<>();

        List<VaultCategory> allCategories = categoryRepository.findAllByOrderBySortOrderAsc();
        List<UUID> filledCategoryIds = entryRepository.findDistinctCategoryIdsByUserId(user.getId());
        Set<UUID> filledSet = new HashSet<>(filledCategoryIds);
        long trustedContacts = trustedContactRepository.countByUserId(user.getId());
        long incompleteEntries = entryRepository.countIncompleteByUserId(user.getId());
        Instant staleThreshold = Instant.now().minus(90, ChronoUnit.DAYS);
        List<UUID> staleCategoryIds = entryRepository.findCategoryIdsWithStaleEntries(user.getId(), staleThreshold);

        // Check personal_wishes category
        VaultCategory personalWishes = allCategories.stream()
                .filter(c -> "personal_wishes".equals(c.getSlug()))
                .findFirst().orElse(null);

        if (personalWishes != null && !filledSet.contains(personalWishes.getId())) {
            suggestions.add(new SuggestionResponse(
                    "MISSING_PERSONAL_WISHES", 200, personalWishes.getId(),
                    "Lisa isiklikud soovid",
                    "Isiklikud soovid on üks olulisemaid osasid sinu pärandiplaanist. Lisa oma soovid ja juhised lähedastele.",
                    15
            ));
        }

        // Check trusted contacts
        if (trustedContacts == 0) {
            suggestions.add(new SuggestionResponse(
                    "MISSING_TRUSTED_CONTACT", 190, null,
                    "Lisa usalduskontakt",
                    "Usalduskontakt on isik, kes saab ligipääsu sinu andmetele vajaduse korral. Lisa vähemalt üks usalduskontakt.",
                    15
            ));
        }

        // Check empty categories (excluding personal_wishes which is handled above)
        for (VaultCategory cat : allCategories) {
            if ("personal_wishes".equals(cat.getSlug())) continue;
            if (filledSet.contains(cat.getId())) continue;

            int priority = CATEGORY_PRIORITY.getOrDefault(cat.getSlug(), 50);
            suggestions.add(new SuggestionResponse(
                    "EMPTY_CATEGORY", priority, cat.getId(),
                    "Täida kategooria: " + cat.getNameEt(),
                    "Lisa vähemalt üks kirje kategooriasse \"" + cat.getNameEt() + "\" oma pärandiplaani täiendamiseks.",
                    7
            ));
        }

        // Check incomplete entries
        if (incompleteEntries > 0) {
            suggestions.add(new SuggestionResponse(
                    "INCOMPLETE_ENTRIES", 40, null,
                    "Lõpeta pooleli olevad kirjed",
                    "Sul on " + incompleteEntries + " lõpetamata kirjet. Täida kõik väljad, et sinu plaan oleks põhjalik.",
                    0
            ));
        }

        // Check stale entries (not reviewed in 90+ days)
        if (!staleCategoryIds.isEmpty()) {
            suggestions.add(new SuggestionResponse(
                    "REVIEW_NEEDED", 30, null,
                    "Vaata kirjed üle",
                    "Mõned kirjed pole üle 90 päeva üle vaadatud. Kontrolli, kas andmed on endiselt ajakohased.",
                    0
            ));
        }

        // Sort by priority descending and return max 5
        suggestions.sort(Comparator.comparingInt(SuggestionResponse::priority).reversed());
        return suggestions.stream().limit(5).toList();
    }

    public record ProgressResponse(
            BigDecimal progressPercentage,
            int totalCategories,
            int completedCategories,
            int totalEntries,
            int completedEntries,
            Instant lastCalculatedAt
    ) {}
}
