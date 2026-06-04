package com.example.lightweight.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CustomerCallbackRequest(
        @NotBlank String correlationId,
        @NotBlank String customerId,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Email @NotBlank String email,
        @NotBlank String source
) {
}
