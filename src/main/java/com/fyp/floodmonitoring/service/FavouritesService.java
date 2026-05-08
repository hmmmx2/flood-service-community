package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.AddFavouriteRequest;
import com.fyp.floodmonitoring.dto.request.UpdateFavouriteChannelsRequest;
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
 * which links {@code users.id} to {@code nodes.id}. Each row also carries
 * per-channel notification toggles (email / SMS / WhatsApp / in-app push).</p>
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
                .map(f -> toDto(nodeMap.get(f.getId().getNodeId()), f))
                .toList();
    }

    /**
     * Bookmarks a node for the user. Idempotent — returns the existing record if already present.
     * New favourites have all 4 channels enabled by default.
     *
     * @throws AppException 404 if the nodeId does not match any node
     */
    @Transactional
    public FavouriteNodeDto addFavourite(UUID userId, AddFavouriteRequest req) {
        Node node = nodeRepository.findByNodeId(req.nodeId())
                .orElseThrow(() -> AppException.notFound("Node not found: " + req.nodeId()));

        UserFavouriteNodeId pk = new UserFavouriteNodeId(userId, node.getId());

        UserFavouriteNode fav = favRepository.findById(pk).orElseGet(() -> {
            UserFavouriteNode newFav = new UserFavouriteNode(pk, Instant.now());
            return favRepository.save(newFav);
        });

        return toDto(node, fav);
    }

    /**
     * Updates the per-channel notification preferences for a favourited node.
     * Null fields in the request are left unchanged. If the favourite does
     * not yet exist, it is created with the requested values (and {@code true}
     * for any field left null).
     */
    @Transactional
    public FavouriteNodeDto updateChannels(UUID userId, String nodeId, UpdateFavouriteChannelsRequest req) {
        Node node = nodeRepository.findByNodeId(nodeId)
                .orElseThrow(() -> AppException.notFound("Node not found: " + nodeId));

        UserFavouriteNodeId pk = new UserFavouriteNodeId(userId, node.getId());

        UserFavouriteNode fav = favRepository.findById(pk).orElseGet(() -> {
            UserFavouriteNode newFav = new UserFavouriteNode(pk, Instant.now());
            return favRepository.save(newFav);
        });

        if (req.emailEnabled()    != null) fav.setEmailEnabled(req.emailEnabled());
        if (req.smsEnabled()      != null) fav.setSmsEnabled(req.smsEnabled());
        if (req.whatsappEnabled() != null) fav.setWhatsappEnabled(req.whatsappEnabled());
        if (req.pushEnabled()     != null) fav.setPushEnabled(req.pushEnabled());

        fav = favRepository.save(fav);
        return toDto(node, fav);
    }

    /**
     * Removes the bookmark. No-op if the user has not bookmarked the node.
     */
    @Transactional
    public void removeFavourite(UUID userId, String nodeId) {
        Node node = nodeRepository.findByNodeId(nodeId)
                .orElseThrow(() -> AppException.notFound("Node not found: " + nodeId));
        favRepository.deleteById(new UserFavouriteNodeId(userId, node.getId()));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private FavouriteNodeDto toDto(Node n, UserFavouriteNode fav) {
        double dist = GeoUtils.haversineKm(
                GeoUtils.KUCHING_LAT, GeoUtils.KUCHING_LON,
                n.getLatitude(), n.getLongitude());

        Instant favouritedAt = fav != null ? fav.getCreatedAt() : Instant.now();
        boolean email    = fav == null || Boolean.TRUE.equals(fav.getEmailEnabled());
        boolean sms      = fav == null || Boolean.TRUE.equals(fav.getSmsEnabled());
        boolean whatsapp = fav == null || Boolean.TRUE.equals(fav.getWhatsappEnabled());
        boolean push     = fav == null || Boolean.TRUE.equals(fav.getPushEnabled());

        return new FavouriteNodeDto(
                n.getId().toString(),
                n.getNodeId(),
                n.getName() != null ? n.getName() : "Node " + n.getNodeId(),
                resolveStatus(n.getCurrentLevel(), n.getIsDead()),
                dist + " km",
                List.of(n.getLongitude(), n.getLatitude()),
                n.getArea(),
                n.getLocation(),
                n.getState(),
                n.getCurrentLevel() != null ? n.getCurrentLevel() : 0,
                n.getLastUpdated() != null ? n.getLastUpdated().toString() : null,
                favouritedAt != null ? favouritedAt.toString() : Instant.now().toString(),
                email, sms, whatsapp, push);
    }

    private String resolveStatus(Integer level, Boolean isDead) {
        if (Boolean.TRUE.equals(isDead)) return "inactive";
        if (level != null && level >= 2)  return "warning";
        return "active";
    }
}
