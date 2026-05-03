package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.CreateCommunityCommentRequest;
import com.fyp.floodmonitoring.dto.request.CreateCommunityPostRequest;
import com.fyp.floodmonitoring.dto.request.CreateGroupRequest;
import com.fyp.floodmonitoring.dto.request.UpdatePostRequest;
import com.fyp.floodmonitoring.dto.response.CommunityCommentDto;
import com.fyp.floodmonitoring.dto.response.CommunityGroupDto;
import com.fyp.floodmonitoring.dto.response.CommunityPostDto;
import com.fyp.floodmonitoring.dto.response.LikeToggleDto;
import com.fyp.floodmonitoring.entity.*;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.CommunityCommentRepository;
import com.fyp.floodmonitoring.repository.CommunityCommentVoteRepository;
import com.fyp.floodmonitoring.repository.CommunityGroupMemberRepository;
import com.fyp.floodmonitoring.repository.CommunityGroupRepository;
import com.fyp.floodmonitoring.repository.CommunityPostLikeRepository;
import com.fyp.floodmonitoring.repository.CommunityPostRepository;
import com.fyp.floodmonitoring.repository.UserRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CommunityService}.
 *
 * <p>Each nested class covers one cohesive service operation.
 * All repositories are mocked — no Spring context or database required.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommunityService Tests")
class CommunityServiceTest {

    @Mock private CommunityPostRepository         postRepo;
    @Mock private CommunityCommentRepository      commentRepo;
    @Mock private CommunityCommentVoteRepository   voteRepo;
    @Mock private CommunityPostLikeRepository     likeRepo;
    @Mock private CommunityGroupRepository        groupRepo;
    @Mock private CommunityGroupMemberRepository  memberRepo;
    @Mock private UserRepository                  userRepo;

    @InjectMocks private CommunityService communityService;

    private User author;
    private User adminUser;
    private CommunityGroup group;
    private CommunityPost  post;
    private CommunityComment comment;

    @BeforeEach
    void setUp() {
        author    = TestDataBuilder.buildUser();
        adminUser = TestDataBuilder.buildAdmin();
        group     = TestDataBuilder.buildGroup();
        post      = TestDataBuilder.buildPost();
        comment   = TestDataBuilder.buildComment();
    }

    // ── createPost() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPost()")
    class CreatePost {

        @Test
        @DisplayName("saves post with correct author and returns DTO")
        void createPost_Valid_SavesWithAuthor() {
            when(userRepo.findById(author.getId())).thenReturn(Optional.of(author));
            when(postRepo.save(any(CommunityPost.class))).thenReturn(post);

            CreateCommunityPostRequest req = new CreateCommunityPostRequest(
                    "Flood update in Kuching area",
                    "Water levels have risen significantly near the river banks...",
                    null, null
            );

            CommunityPostDto result = communityService.createPost(author.getId(), req);

            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("Flood update in Kuching area");
            assertThat(result.authorId()).isEqualTo(author.getId().toString());
            verify(postRepo).save(any(CommunityPost.class));
        }

        @Test
        @DisplayName("throws NOT_FOUND when user does not exist")
        void createPost_UserNotFound_ThrowsNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(userRepo.findById(unknownId)).thenReturn(Optional.empty());

            CreateCommunityPostRequest req = new CreateCommunityPostRequest(
                    "Title", "Content", null, null
            );

            assertThatThrownBy(() -> communityService.createPost(unknownId, req))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("adjusts group post count when post belongs to a group")
        void createPost_WithGroup_AdjustsPostCount() {
            when(userRepo.findById(author.getId())).thenReturn(Optional.of(author));
            when(groupRepo.findBySlug("flood-alerts")).thenReturn(Optional.of(group));
            when(postRepo.save(any(CommunityPost.class))).thenAnswer(inv -> {
                CommunityPost p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            CreateCommunityPostRequest req = new CreateCommunityPostRequest(
                    "Title", "Content", null, "flood-alerts"
            );

            communityService.createPost(author.getId(), req);

            verify(groupRepo).adjustPosts(group.getId(), 1);
        }
    }

    // ── updatePost() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updatePost()")
    class UpdatePost {

        @Test
        @DisplayName("updates post when requester is the author")
        void updatePost_Owner_UpdatesSuccessfully() {
            when(postRepo.findById(post.getId())).thenReturn(Optional.of(post));
            when(postRepo.save(any())).thenReturn(post);

            UpdatePostRequest req = new UpdatePostRequest("New Title", null, null, false);

            CommunityPostDto result = communityService.updatePost(post.getId(), author.getId(), req);

            assertThat(result).isNotNull();
            verify(postRepo).save(any(CommunityPost.class));
        }

        @Test
        @DisplayName("throws FORBIDDEN when requester is not the author")
        void updatePost_NonOwner_ThrowsForbidden() {
            UUID otherUserId = UUID.fromString("99999999-9999-9999-9999-999999999999");
            when(postRepo.findById(post.getId())).thenReturn(Optional.of(post));

            UpdatePostRequest req = new UpdatePostRequest("Hacked Title", null, null, false);

            assertThatThrownBy(() -> communityService.updatePost(post.getId(), otherUserId, req))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

            verify(postRepo, never()).save(any());
        }

        @Test
        @DisplayName("throws NOT_FOUND when post does not exist")
        void updatePost_PostNotFound_ThrowsNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(postRepo.findById(unknownId)).thenReturn(Optional.empty());

            UpdatePostRequest req = new UpdatePostRequest("Title", null, null, false);

            assertThatThrownBy(() -> communityService.updatePost(unknownId, author.getId(), req))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    // ── deletePost() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deletePost()")
    class DeletePost {

        @Test
        @DisplayName("admin can delete any post regardless of ownership")
        void deletePost_Admin_CanDeleteAnyPost() {
            when(postRepo.findById(post.getId())).thenReturn(Optional.of(post));
            doNothing().when(postRepo).delete(post);

            communityService.deletePost(post.getId(), adminUser.getId(), true);

            verify(postRepo).delete(post);
        }

        @Test
        @DisplayName("author can delete their own post")
        void deletePost_Author_CanDeleteOwnPost() {
            when(postRepo.findById(post.getId())).thenReturn(Optional.of(post));
            doNothing().when(postRepo).delete(post);

            communityService.deletePost(post.getId(), author.getId(), false);

            verify(postRepo).delete(post);
        }

        @Test
        @DisplayName("throws FORBIDDEN when non-admin tries to delete another user's post")
        void deletePost_NonAdmin_NonAuthor_ThrowsForbidden() {
            UUID intruder = UUID.fromString("88888888-8888-8888-8888-888888888888");
            when(postRepo.findById(post.getId())).thenReturn(Optional.of(post));

            assertThatThrownBy(() -> communityService.deletePost(post.getId(), intruder, false))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

            verify(postRepo, never()).delete(any());
        }
    }

    // ── toggleLike() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toggleLike()")
    class ToggleLike {

        @Test
        @DisplayName("adds like if user has not liked the post yet")
        void toggleLike_NotYetLiked_AddsLike() {
            when(postRepo.findById(post.getId())).thenReturn(Optional.of(post));
            when(likeRepo.existsByPostIdAndUserId(post.getId(), author.getId())).thenReturn(false);
            when(likeRepo.save(any())).thenReturn(null);
            doNothing().when(postRepo).adjustLikes(any(), eq(1));

            LikeToggleDto result = communityService.toggleLike(post.getId(), author.getId());

            assertThat(result.liked()).isTrue();
            assertThat(result.likesCount()).isEqualTo(post.getLikesCount() + 1);
            verify(likeRepo).save(any());
        }

        @Test
        @DisplayName("removes like if user already liked the post")
        void toggleLike_AlreadyLiked_RemovesLike() {
            when(postRepo.findById(post.getId())).thenReturn(Optional.of(post));
            when(likeRepo.existsByPostIdAndUserId(post.getId(), author.getId())).thenReturn(true);
            doNothing().when(likeRepo).deleteByPostIdAndUserId(post.getId(), author.getId());
            doNothing().when(postRepo).adjustLikes(any(), eq(-1));

            LikeToggleDto result = communityService.toggleLike(post.getId(), author.getId());

            assertThat(result.liked()).isFalse();
            verify(likeRepo).deleteByPostIdAndUserId(post.getId(), author.getId());
        }
    }

    // ── addComment() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addComment()")
    class AddComment {

        @Test
        @DisplayName("saves comment and returns DTO")
        void addComment_Valid_SavesAndReturnsDto() {
            when(postRepo.findById(post.getId())).thenReturn(Optional.of(post));
            when(userRepo.findById(author.getId())).thenReturn(Optional.of(author));
            when(commentRepo.save(any(CommunityComment.class))).thenReturn(comment);
            doNothing().when(postRepo).adjustComments(any(), eq(1));

            CreateCommunityCommentRequest req = new CreateCommunityCommentRequest("Thanks for the update!", null);

            CommunityCommentDto result = communityService.addComment(post.getId(), author.getId(), req);

            assertThat(result).isNotNull();
            assertThat(result.content()).isEqualTo("Thanks for the update!");
            assertThat(result.authorId()).isEqualTo(author.getId().toString());
            verify(commentRepo).save(any(CommunityComment.class));
            verify(postRepo).adjustComments(post.getId(), 1);
        }

        @Test
        @DisplayName("throws NOT_FOUND when post does not exist")
        void addComment_PostNotFound_ThrowsNotFound() {
            UUID unknownPost = UUID.randomUUID();
            when(postRepo.findById(unknownPost)).thenReturn(Optional.empty());

            CreateCommunityCommentRequest req = new CreateCommunityCommentRequest("Comment", null);

            assertThatThrownBy(() -> communityService.addComment(unknownPost, author.getId(), req))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    // ── deleteComment() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteComment()")
    class DeleteComment {

        @Test
        @DisplayName("comment author can delete their own comment")
        void deleteComment_Author_CanDelete() {
            when(commentRepo.findById(comment.getId())).thenReturn(Optional.of(comment));
            when(commentRepo.countByParent_Id(comment.getId())).thenReturn(0L);
            doNothing().when(voteRepo).deleteByComment_Id(comment.getId());
            doNothing().when(postRepo).adjustComments(any(), eq(-1));
            doNothing().when(commentRepo).delete(comment);

            communityService.deleteComment(post.getId(), comment.getId(), author.getId(), false);

            verify(commentRepo).delete(comment);
        }

        @Test
        @DisplayName("admin can delete any comment")
        void deleteComment_Admin_CanDeleteAnyComment() {
            when(commentRepo.findById(comment.getId())).thenReturn(Optional.of(comment));
            when(commentRepo.countByParent_Id(comment.getId())).thenReturn(0L);
            doNothing().when(voteRepo).deleteByComment_Id(comment.getId());
            doNothing().when(postRepo).adjustComments(any(), eq(-1));
            doNothing().when(commentRepo).delete(comment);

            communityService.deleteComment(post.getId(), comment.getId(), adminUser.getId(), true);

            verify(commentRepo).delete(comment);
        }

        @Test
        @DisplayName("throws FORBIDDEN when non-author, non-admin tries to delete")
        void deleteComment_Intruder_ThrowsForbidden() {
            UUID intruder = UUID.fromString("77777777-7777-7777-7777-777777777777");
            when(commentRepo.findById(comment.getId())).thenReturn(Optional.of(comment));

            assertThatThrownBy(() -> communityService.deleteComment(post.getId(), comment.getId(), intruder, false))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

            verify(commentRepo, never()).delete(any());
        }
    }

    // ── createGroup() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createGroup()")
    class CreateGroup {

        @Test
        @DisplayName("creates group with unique slug and returns DTO")
        void createGroup_UniqueSlug_SavesAndReturnsDto() {
            when(groupRepo.existsBySlug("flood-alerts")).thenReturn(false);
            when(userRepo.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
            when(groupRepo.save(any(CommunityGroup.class))).thenReturn(group);

            CreateGroupRequest req = new CreateGroupRequest(
                    "flood-alerts", "Flood Alerts",
                    "Community discussion about flood alerts", "#ed1c24"
            );

            CommunityGroupDto result = communityService.createGroup(adminUser.getId(), req);

            assertThat(result).isNotNull();
            assertThat(result.slug()).isEqualTo("flood-alerts");
            verify(groupRepo).save(any(CommunityGroup.class));
        }

        @Test
        @DisplayName("throws CONFLICT when slug already exists")
        void createGroup_DuplicateSlug_ThrowsConflict() {
            when(groupRepo.existsBySlug("flood-alerts")).thenReturn(true);

            CreateGroupRequest req = new CreateGroupRequest(
                    "flood-alerts", "Flood Alerts", null, null
            );

            assertThatThrownBy(() -> communityService.createGroup(adminUser.getId(), req))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

            verify(groupRepo, never()).save(any());
        }
    }

    // ── toggleMembership() (join / leave) ──────────────────────────────────────

    @Nested
    @DisplayName("toggleMembership() — join / leave group")
    class ToggleMembership {

        @Test
        @DisplayName("adds member when user is not yet in the group")
        void toggleMembership_NotMember_JoinsGroup() {
            when(groupRepo.findBySlug("flood-alerts")).thenReturn(Optional.of(group));
            when(memberRepo.existsByGroupIdAndUserId(group.getId(), author.getId())).thenReturn(false);
            when(memberRepo.save(any())).thenReturn(null);
            doNothing().when(groupRepo).adjustMembers(group.getId(), 1);

            CommunityGroupDto result = communityService.toggleMembership("flood-alerts", author.getId());

            assertThat(result.joinedByMe()).isTrue();
            verify(memberRepo).save(any());
        }

        @Test
        @DisplayName("removes member when user is already in the group")
        void toggleMembership_IsMember_LeavesGroup() {
            when(groupRepo.findBySlug("flood-alerts")).thenReturn(Optional.of(group));
            when(memberRepo.existsByGroupIdAndUserId(group.getId(), author.getId())).thenReturn(true);
            doNothing().when(memberRepo).deleteByGroupIdAndUserId(group.getId(), author.getId());
            doNothing().when(groupRepo).adjustMembers(group.getId(), -1);

            CommunityGroupDto result = communityService.toggleMembership("flood-alerts", author.getId());

            assertThat(result.joinedByMe()).isFalse();
            verify(memberRepo).deleteByGroupIdAndUserId(group.getId(), author.getId());
        }

        @Test
        @DisplayName("throws NOT_FOUND when group slug does not exist")
        void toggleMembership_GroupNotFound_ThrowsNotFound() {
            when(groupRepo.findBySlug("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> communityService.toggleMembership("nonexistent", author.getId()))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }
}
