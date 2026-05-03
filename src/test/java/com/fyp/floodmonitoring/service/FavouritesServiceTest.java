package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.AddFavouriteRequest;
import com.fyp.floodmonitoring.dto.response.FavouriteNodeDto;
import com.fyp.floodmonitoring.entity.Node;
import com.fyp.floodmonitoring.entity.UserFavouriteNode;
import com.fyp.floodmonitoring.entity.UserFavouriteNodeId;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.NodeRepository;
import com.fyp.floodmonitoring.repository.UserFavouriteNodeRepository;
import com.fyp.floodmonitoring.util.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FavouritesService}.
 *
 * <p>All repository dependencies are mocked. Tests cover the idempotent add
 * behaviour, removal, and retrieval of bookmarked sensor nodes.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FavouritesService Tests")
class FavouritesServiceTest {

    @Mock private UserFavouriteNodeRepository favRepository;
    @Mock private NodeRepository              nodeRepository;

    @InjectMocks private FavouritesService favouritesService;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private Node sampleNode;
    private UserFavouriteNode sampleFav;

    @BeforeEach
    void setUp() {
        sampleNode = TestDataBuilder.buildNode();

        UserFavouriteNodeId pk = new UserFavouriteNodeId(USER_ID, sampleNode.getId());
        sampleFav = new UserFavouriteNode(pk, Instant.parse("2025-06-15T08:00:00Z"));
    }

    // ── getFavourites() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFavourites()")
    class GetFavourites {

        @Test
        @DisplayName("returns list of favourite node DTOs for the user")
        void getFavourites_WithFavourites_ReturnsDtoList() {
            when(favRepository.findByIdUserId(USER_ID)).thenReturn(List.of(sampleFav));
            when(nodeRepository.findAllById(anyList())).thenReturn(List.of(sampleNode));

            List<FavouriteNodeDto> result = favouritesService.getFavourites(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).nodeId()).isEqualTo(sampleNode.getNodeId());
            assertThat(result.get(0).name()).isEqualTo("Node 102782478");
            assertThat(result.get(0).favouritedAt()).isNotNull();
        }

        @Test
        @DisplayName("returns empty list when user has no favourites")
        void getFavourites_NoFavourites_ReturnsEmpty() {
            when(favRepository.findByIdUserId(USER_ID)).thenReturn(Collections.emptyList());
            when(nodeRepository.findAllById(anyList())).thenReturn(Collections.emptyList());

            List<FavouriteNodeDto> result = favouritesService.getFavourites(USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("filters out favourites whose node no longer exists")
        void getFavourites_OrphanedFavourite_IsFiltered() {
            when(favRepository.findByIdUserId(USER_ID)).thenReturn(List.of(sampleFav));
            when(nodeRepository.findAllById(anyList())).thenReturn(Collections.emptyList());

            List<FavouriteNodeDto> result = favouritesService.getFavourites(USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns correct status for active node (level 1)")
        void getFavourites_ActiveNode_StatusIsActive() {
            sampleNode.setCurrentLevel(1);
            sampleNode.setIsDead(false);
            when(favRepository.findByIdUserId(USER_ID)).thenReturn(List.of(sampleFav));
            when(nodeRepository.findAllById(anyList())).thenReturn(List.of(sampleNode));

            List<FavouriteNodeDto> result = favouritesService.getFavourites(USER_ID);

            assertThat(result.get(0).status()).isEqualTo("active");
        }

        @Test
        @DisplayName("returns 'warning' status for node at level 2")
        void getFavourites_WarningNode_StatusIsWarning() {
            sampleNode.setCurrentLevel(2);
            sampleNode.setIsDead(false);
            when(favRepository.findByIdUserId(USER_ID)).thenReturn(List.of(sampleFav));
            when(nodeRepository.findAllById(anyList())).thenReturn(List.of(sampleNode));

            List<FavouriteNodeDto> result = favouritesService.getFavourites(USER_ID);

            assertThat(result.get(0).status()).isEqualTo("warning");
        }
    }

    // ── addFavourite() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addFavourite()")
    class AddFavourite {

        @Test
        @DisplayName("saves a new favourite and returns DTO")
        void addFavourite_NewNode_SavesAndReturnsDto() {
            AddFavouriteRequest req = new AddFavouriteRequest(sampleNode.getNodeId());
            when(nodeRepository.findByNodeId(sampleNode.getNodeId())).thenReturn(Optional.of(sampleNode));
            when(favRepository.findById(any(UserFavouriteNodeId.class))).thenReturn(Optional.empty());
            when(favRepository.save(any(UserFavouriteNode.class))).thenReturn(sampleFav);

            FavouriteNodeDto result = favouritesService.addFavourite(USER_ID, req);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Node 102782478");
            verify(favRepository).save(any(UserFavouriteNode.class));
        }

        @Test
        @DisplayName("returns existing favourite without duplicating (idempotent)")
        void addFavourite_AlreadyExists_ReturnsExistingWithoutSaving() {
            AddFavouriteRequest req = new AddFavouriteRequest(sampleNode.getNodeId());
            when(nodeRepository.findByNodeId(sampleNode.getNodeId())).thenReturn(Optional.of(sampleNode));
            when(favRepository.findById(any(UserFavouriteNodeId.class))).thenReturn(Optional.of(sampleFav));

            FavouriteNodeDto result = favouritesService.addFavourite(USER_ID, req);

            assertThat(result).isNotNull();
            verify(favRepository, never()).save(any(UserFavouriteNode.class));
        }

        @Test
        @DisplayName("throws NOT_FOUND when nodeId does not match any node")
        void addFavourite_NodeNotFound_ThrowsNotFound() {
            AddFavouriteRequest req = new AddFavouriteRequest("unknown-node");
            when(nodeRepository.findByNodeId("unknown-node")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> favouritesService.addFavourite(USER_ID, req))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

            verify(favRepository, never()).save(any());
        }
    }

    // ── removeFavourite() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeFavourite()")
    class RemoveFavourite {

        @Test
        @DisplayName("deletes favourite entry when node exists")
        void removeFavourite_Exists_DeletesEntry() {
            when(nodeRepository.findByNodeId(sampleNode.getNodeId())).thenReturn(Optional.of(sampleNode));
            doNothing().when(favRepository).deleteById(any(UserFavouriteNodeId.class));

            favouritesService.removeFavourite(USER_ID, sampleNode.getNodeId());

            verify(favRepository).deleteById(argThat(pk ->
                    pk.getUserId().equals(USER_ID) &&
                    pk.getNodeId().equals(sampleNode.getId())
            ));
        }

        @Test
        @DisplayName("throws NOT_FOUND when nodeId does not match any node")
        void removeFavourite_NodeNotFound_ThrowsNotFound() {
            when(nodeRepository.findByNodeId("ghost-node")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> favouritesService.removeFavourite(USER_ID, "ghost-node"))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

            verify(favRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("no-op when node exists but user has not bookmarked it (JPA deleteById is safe)")
        void removeFavourite_NotBookmarked_NoException() {
            when(nodeRepository.findByNodeId(sampleNode.getNodeId())).thenReturn(Optional.of(sampleNode));
            doNothing().when(favRepository).deleteById(any(UserFavouriteNodeId.class));

            assertThatCode(() -> favouritesService.removeFavourite(USER_ID, sampleNode.getNodeId()))
                    .doesNotThrowAnyException();
        }
    }
}
