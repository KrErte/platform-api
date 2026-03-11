package ee.parandiplaan.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeRoleRequest(
        @NotBlank @Pattern(regexp = "USER|ADMIN") String role
) {}
