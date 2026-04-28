package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddFavouriteRequest(@NotBlank String nodeId) {}
