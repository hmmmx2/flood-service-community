package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.AddFavouriteRequest;
import com.fyp.floodmonitoring.dto.response.FavouriteNodeDto;
import com.fyp.floodmonitoring.entity.Node;
import com.fyp.floodmonitoring.entity.UserFavouriteNode;
import com.fyp.floodmonitoring.entity.UserFavouriteNodeId;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.NodeRepository;
import com.fyp.floodmonitoring.repository.UserFavouriteNodeRepository;
import com.fyp.floodmonitoring.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages bookmarked sensor nodes per user (SCRUM-112).
 *
 * <p>Favourite entries are stored in the {@code user_favourite_nodes} join table
 * which links {@code users.id} to {@code nodes.id}.</p>
 */
@Service
@RequiredArgsConstructor
public class FavouritesService {

    private final UserFavouriteNodeRepository favRepository;
    private final NodeRepository              nodeRepository;

    /** Returns all bookmarked nodes for the given user, ordered by favouritedAt DESC. */
    @Transactional(readOnly = true)
    public List<FavouriteNodeDto> getFavourites(UUID userId) {
        List<UserFavouriteNode> favs = favRepository.findByIdUserId(userId);

        List<UUID> nodeIds = favs.stream()
                .map(f -> f.getId().getNodeId())
                .toList();

        Map<UUID, Node> nodeMap = nodeRepository.findAllById(nodeIds)
                .stream()
                .collect(Collectors.toMap(Node::getId, n -> n));

        return favs.stream()
                .filter(f -> nodeMap.containsKey(f.getId().getNodeId()))
                .map(f -> toDto(nodeMap.get(f.getId().getNodeId()), f.getCreatedAt()))
                .toList();
    }

    /**
     * Bookmarks a node for the user. Idempotent — returns the existing record if already present.
     *
     * @throws AppException 404 if the nodeId does not match any node
     */
    @Transactional
    public FavouriteNodeDto addFavourite(UUID userId, AddFavouriteRequest req) {
        Node node = nodeRepository.findById(req.nodeId())
                .orElseThrow(() -> AppException.notFound("Node not found: " + req.nodeId()));

        UserFavouriteNodeId pk = new UserFavouriteNodeId(userId, node.getId());

        UserFavouriteNode fav = favRepository.findById(pk).orElseGet(() -> {
            UserFavouriteNode newFav = new UserFavouriteNode(pk, Instant.now());
            return favRepository.save(newFav);
        });

        return toDto(node, fav.getCreatedAt());
    }

    /**
     * Removes the bookmark. No-op if the user has not bookmarked the node.
     */
    @Transactional
    public void removeFavourite(UUID userId, UUID nodeId) {
        favRepository.deleteById(new UserFavouriteNodeId(userId, nodeId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private FavouriteNodeDto toDto(Node n, Instant favouritedAt) {
        double dist = GeoUtils.haversineKm(
                GeoUtils.KUCHING_LAT, GeoUtils.KUCHING_LON,
                n.getLatitude(), n.getLongitude());

        return new FavouriteNodeDto(
                n.getId().toString(),
                n.getName(),
                resolveStatus(n.getCurrentLevel(), n.getIsDead()),
                dist + " km",
                List.of(n.getLongitude(), n.getLatitude()),
                n.getArea(),
                n.getLocation(),
                n.getState(),
                n.getCurrentLevel() != null ? n.getCurrentLevel() : 0,
                n.getLastUpdated() != null ? n.getLastUpdated().toString() : null,
                favouritedAt != null ? favouritedAt.toString() : Instant.now().toString());
    }

    private String resolveStatus(Integer level, Boolean isDead) {
        if (Boolean.TRUE.equals(isDead)) return "inactive";
        if (level != null && level >= 2)  return "warning";
        return "active";
    }
}
