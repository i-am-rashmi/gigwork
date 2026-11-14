package com.gigwork.core.profile.web.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The portfolio section (FR-6): up to 10 links; their order is the display order. An empty list clears the section. */
public record PortfolioRequest(@NotNull @Size(max = 100) List<@NotBlank @Size(max = 4096) String> links) {
}
