package com.fyp.floodmonitoring.dto.response;

import java.util.List;

public record DashboardTimeSeriesDto(List<Integer> monthly, List<Integer> yearly) {}
