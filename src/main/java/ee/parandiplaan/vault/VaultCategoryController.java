package ee.parandiplaan.vault;

import ee.parandiplaan.common.security.CurrentUser;
import ee.parandiplaan.user.User;
import ee.parandiplaan.vault.dto.VaultCategoryWithCountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/vault/categories")
@RequiredArgsConstructor
public class VaultCategoryController {

    private final VaultCategoryRepository categoryRepository;
    private final VaultEntryRepository entryRepository;

    @GetMapping
    public ResponseEntity<List<VaultCategoryWithCountResponse>> listCategories(
            @CurrentUser User user) {

        List<VaultCategory> categories = categoryRepository.findAllByOrderBySortOrderAsc();

        // If user is authenticated, include entry counts
        Map<UUID, Long> entryCounts = Map.of();
        Map<UUID, Long> completedCounts = Map.of();

        if (user != null) {
            List<VaultEntry> entries = entryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
            entryCounts = entries.stream()
                    .collect(Collectors.groupingBy(e -> e.getCategory().getId(), Collectors.counting()));

            Map<UUID, Long> finalEntryCounts = entryCounts;
            completedCounts = entries.stream()
                    .filter(VaultEntry::isComplete)
                    .collect(Collectors.groupingBy(e -> e.getCategory().getId(), Collectors.counting()));
        }

        Map<UUID, Long> finalEntryCounts = entryCounts;
        Map<UUID, Long> finalCompletedCounts = completedCounts;

        List<VaultCategoryWithCountResponse> response = categories.stream()
                .map(cat -> new VaultCategoryWithCountResponse(
                        cat.getId(),
                        cat.getSlug(),
                        cat.getNameEt(),
                        cat.getNameEn(),
                        cat.getIcon(),
                        cat.getSortOrder(),
                        cat.getFieldTemplate(),
                        finalEntryCounts.getOrDefault(cat.getId(), 0L),
                        finalCompletedCounts.getOrDefault(cat.getId(), 0L)
                ))
                .toList();

        return ResponseEntity.ok(response);
    }
}
