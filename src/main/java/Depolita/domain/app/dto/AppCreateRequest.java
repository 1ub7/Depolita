package Depolita.domain.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AppCreateRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$",
        message = "이름은 소문자, 숫자, 하이픈만 가능하며 3-50자여야 합니다.")
        String name,

        @NotBlank
        @Pattern(regexp = "^https://github\\.com/[A-Za-z0-9-]+/[A-Za-z0-9._-]+?(\\.git)?/?$",
                message = "GitHub HTTPS 저장소 URL만 허용됩니다.")
        String repositoryUrl,

        @NotNull
        @Min(1) @Max(65535)
        Integer internalPort
) {
}
