package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddFavouriteRequest(@NotNull UUID nodeId) {}
