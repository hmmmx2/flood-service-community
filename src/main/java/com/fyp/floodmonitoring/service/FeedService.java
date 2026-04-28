package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.response.CursorPageDto;
import com.fyp.floodmonitoring.dto.response.FeedItemDto;
import com.fyp.floodmonitoring.entity.Event;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.EventRepository;
import com.fyp.floodmonitoring.util.WaterLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Cursor-paginated activity feed built from IoT water-level events.
 *
 * <p>Events with {@code newLevel >= 2} become {@code "alert"} items.
 * Events with {@code newLevel < 2} become {@code "update"} (all-clear) items.</p>
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private static final int PAGE_SIZE = 20;

    private final EventRepository eventRepository;

    // ── Paginated feed ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CursorPageDto<FeedItemDto> getFeed(String cursor) {
        List<Event> rows;

        if (cursor != null && !cursor.isBlank()) {
            UUID cursorId;
            try {
                cursorId = UUID.fromString(cursor);
            } catch (IllegalArgumentException e) {
                throw AppException.badRequest("INVALID_CURSOR", "Invalid pagination cursor format");
            }
            Event pivot = eventRepository.findById(cursorId)
                    .orElseThrow(() -> AppException.badRequest("INVALID_CURSOR", "Invalid pagination cursor"));
            rows = eventRepository.findPageAfterCursor(pivot.getCreatedAt(), PAGE_SIZE + 1);
        } else {
            rows = eventRepository.findFirstPage(PAGE_SIZE + 1);
        }

        boolean hasMore = rows.size() > PAGE_SIZE;
        List<FeedItemDto> data = rows.stream()
                .limit(PAGE_SIZE)
                .map(this::toFeedItem)
                .toList();

        String nextCursor = (hasMore && !data.isEmpty())
                ? data.get(data.size() - 1).id()
                : null;

        return new CursorPageDto<>(data, nextCursor, hasMore);
    }

    // ── Single item ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FeedItemDto getFeedItem(String id) {
        UUID eventId;
        try {
            eventId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw AppException.badRequest("INVALID_ID", "Invalid ID format");
        }
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> AppException.notFound("Feed item not found"));
        return toFeedItem(event);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private FeedItemDto toFeedItem(Event e) {
        int level        = e.getNewLevel() != null ? e.getNewLevel() : 0;
        double meters    = WaterLevel.toMeters(level);
        String createdAt = e.getCreatedAt().toString();
        String id        = e.getId().toString();

        if (level >= 2) {
            String severity = level >= 3 ? "critical" : "warning";
            return FeedItemDto.alert(id, e.getNodeId(), createdAt, severity, meters);
        }
        return FeedItemDto.update(id, e.getNodeId(), createdAt, level, meters);
    }
}
