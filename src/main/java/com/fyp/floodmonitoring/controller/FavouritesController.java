package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.AddFavouriteRequest;
import com.fyp.floodmonitoring.dto.response.FavouriteNodeDto;
import com.fyp.floodmonitoring.service.FavouritesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Manages bookmarked sensor nodes per authenticated user (SCRUM-112).
 *
 * <pre>
 *   GET    /favourites           → list all bookmarked nodes
 *   POST   /favourites           → bookmark a node   { nodeId: UUID }
 *   DELETE /favourites/{nodeId}  → remove bookmark
 * </pre>
 */
@RestController
@RequestMapping("/favourites")
@RequiredArgsConstructor
public class FavouritesController {

    private final FavouritesService favouritesService;

    @GetMapping
    public ResponseEntity<List<FavouriteNodeDto>> getFavourites(
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(favouritesService.getFavourites(userId));
    }

    @PostMapping
    public ResponseEntity<FavouriteNodeDto> addFavourite(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody AddFavouriteRequest req) {

        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(favouritesService.addFavourite(userId, req));
    }

    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> removeFavourite(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID nodeId) {

        UUID userId = UUID.fromString(principal.getUsername());
        favouritesService.removeFavourite(userId, nodeId);
        return ResponseEntity.noContent().build();
    }
}
