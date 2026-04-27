package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.response.CursorPageDto;
import com.fyp.floodmonitoring.dto.response.FeedItemDto;
import com.fyp.floodmonitoring.service.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * <pre>
 * GET /feed?cursor={uuid}   — paginated activity feed
 * GET /feed/{id}            — single feed item
 * </pre>
 */
@RestController
@RequestMapping("/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    @GetMapping
    public ResponseEntity<CursorPageDto<FeedItemDto>> getFeed(
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(feedService.getFeed(cursor));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FeedItemDto> getFeedItem(@PathVariable String id) {
        return ResponseEntity.ok(feedService.getFeedItem(id));
    }
}
