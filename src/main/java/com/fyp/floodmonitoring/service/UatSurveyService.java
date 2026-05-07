package com.fyp.floodmonitoring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyp.floodmonitoring.dto.SubmitUatSurveyRequest;
import com.fyp.floodmonitoring.dto.UatSurveyResponseDto;
import com.fyp.floodmonitoring.entity.UatSurveyResponse;
import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.UatSurveyResponseRepository;
import com.fyp.floodmonitoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.Writer;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UatSurveyService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    /** Hard cap on a single export request so a runaway browser tab can't
     *  drag the whole table into memory. Bumped via env later if needed. */
    private static final int EXPORT_LIMIT = 5000;

    private final UatSurveyResponseRepository repo;
    private final UserRepository userRepo;

    /** Persist a survey submission. {@code authedUserId} may be null for
     *  anonymous submissions; in that case we also skip the displayName
     *  lookup. */
    @Transactional
    public UatSurveyResponseDto submit(SubmitUatSurveyRequest req, UUID authedUserId) {
        String json = serialiseAnswers(req.answers());

        String displayName = null;
        if (authedUserId != null) {
            displayName = userRepo.findById(authedUserId)
                    .map(u -> {
                        String first = Optional.ofNullable(u.getFirstName()).orElse("").trim();
                        String last  = Optional.ofNullable(u.getLastName()).orElse("").trim();
                        String full  = (first + " " + last).trim();
                        return full.isEmpty() ? u.getEmail() : full;
                    })
                    .orElse(null);
        }

        UatSurveyResponse saved = repo.save(UatSurveyResponse.builder()
                .userId(authedUserId)
                .displayName(displayName)
                .role(req.role())
                .source(req.source())
                .answers(json)
                .satisfactionScore(req.satisfactionScore())
                .recommendScore(req.recommendScore())
                .businessFit(req.businessFit())
                .appVersion(req.appVersion())
                .userAgent(req.userAgent())
                .build());

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<UatSurveyResponseDto> list(String role, String source, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<UatSurveyResponse> rows;
        if (role != null && !role.isBlank()) {
            rows = repo.findByRoleOrderBySubmittedAtDesc(role, pageable);
        } else if (source != null && !source.isBlank()) {
            rows = repo.findBySourceOrderBySubmittedAtDesc(source, pageable);
        } else {
            rows = repo.findAllByOrderBySubmittedAtDesc(pageable);
        }
        return rows.map(this::toDto);
    }

    /** Streams the (bounded) full table out as CSV — Excel opens this
     *  natively, no xlsx library needed. */
    @Transactional(readOnly = true)
    public void exportCsv(Writer writer) {
        Pageable pageable = PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "submittedAt"));
        List<UatSurveyResponse> rows = repo.findAllForExport(pageable);

        try (PrintWriter pw = new PrintWriter(writer)) {
            pw.println(String.join(",",
                    "id", "submitted_at", "role", "source", "user_id", "display_name",
                    "satisfaction_score", "recommend_score", "business_fit",
                    "app_version", "answers_json"));
            for (UatSurveyResponse r : rows) {
                pw.println(String.join(",",
                        csv(r.getId().toString()),
                        csv(DateTimeFormatter.ISO_INSTANT.format(r.getSubmittedAt())),
                        csv(r.getRole()),
                        csv(r.getSource()),
                        csv(Optional.ofNullable(r.getUserId()).map(UUID::toString).orElse("")),
                        csv(Optional.ofNullable(r.getDisplayName()).orElse("")),
                        csv(Optional.ofNullable(r.getSatisfactionScore()).map(Object::toString).orElse("")),
                        csv(Optional.ofNullable(r.getRecommendScore()).map(Object::toString).orElse("")),
                        csv(Optional.ofNullable(r.getBusinessFit()).orElse("")),
                        csv(Optional.ofNullable(r.getAppVersion()).orElse("")),
                        csv(r.getAnswers())
                ));
            }
        }
    }

    private UatSurveyResponseDto toDto(UatSurveyResponse r) {
        return new UatSurveyResponseDto(
                r.getId().toString(),
                Optional.ofNullable(r.getUserId()).map(UUID::toString).orElse(null),
                r.getDisplayName(),
                r.getRole(),
                r.getSource(),
                r.getSatisfactionScore(),
                r.getRecommendScore(),
                r.getBusinessFit(),
                deserialiseAnswers(r.getAnswers()),
                r.getSubmittedAt(),
                r.getAppVersion()
        );
    }

    private String serialiseAnswers(Map<String, Object> answers) {
        Map<String, Object> safe = answers == null ? new HashMap<>() : answers;
        try {
            return MAPPER.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            throw AppException.badRequest("INVALID_ANSWERS", "Survey answers could not be serialised");
        }
    }

    private Map<String, Object> deserialiseAnswers(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, MAP_REF);
        } catch (JsonProcessingException e) {
            // Don't blow up the admin list because one row has malformed
            // JSON — surface a placeholder.
            return Map.of("__error", "could not parse stored answers");
        }
    }

    /** RFC-4180-ish CSV escape: wrap in quotes, double any embedded quotes,
     *  collapse newlines so a multi-line free-text answer doesn't break
     *  the row. */
    private static String csv(String v) {
        if (v == null) return "";
        String cleaned = v.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        return "\"" + cleaned.replace("\"", "\"\"") + "\"";
    }
}
