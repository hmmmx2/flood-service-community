package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record VoteCommentRequest(
        @Min(-1) @Max(1) int value
) {}
