package ee.parandiplaan.vault;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/vault/categories")
@RequiredArgsConstructor
public class VaultCategoryController {

    private final VaultCategoryRepository categoryRepository;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listCategories() {
        List<Map<String, Object>> categories = categoryRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(cat -> Map.<String, Object>of(
                        "id", cat.getId(),
                        "slug", cat.getSlug(),
                        "nameEt", cat.getNameEt(),
                        "nameEn", cat.getNameEn(),
                        "icon", cat.getIcon(),
                        "sortOrder", cat.getSortOrder(),
                        "fieldTemplate", cat.getFieldTemplate()
                ))
                .toList();
        return ResponseEntity.ok(categories);
    }
}
