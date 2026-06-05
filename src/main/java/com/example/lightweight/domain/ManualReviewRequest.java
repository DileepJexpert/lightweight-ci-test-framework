package com.example.lightweight.domain;

import jakarta.validation.constraints.NotBlank;

public record ManualReviewRequest(@NotBlank String reviewer, @NotBlank String action) {
}
