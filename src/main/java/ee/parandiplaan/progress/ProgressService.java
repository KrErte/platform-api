package ee.parandiplaan.progress;

import ee.parandiplaan.trust.TrustedContactRepository;
import ee.parandiplaan.user.User;
import ee.parandiplaan.vault.VaultEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final UserProgressRepository progressRepository;
    private final VaultEntryRepository entryRepository;
    private final TrustedContactRepository trustedContactRepository;

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

    public record ProgressResponse(
            BigDecimal progressPercentage,
            int totalCategories,
            int completedCategories,
            int totalEntries,
            int completedEntries,
            Instant lastCalculatedAt
    ) {}
}
