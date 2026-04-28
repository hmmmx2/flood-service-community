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
import com.fyp.floodmonitoring.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityPostRepository postRepo;
    private final CommunityCommentRepository commentRepo;
    private final CommunityPostLikeRepository likeRepo;
    private final CommunityGroupRepository groupRepo;
    private final CommunityGroupMemberRepository memberRepo;
    private final UserRepository userRepo;

    // ── Groups ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CommunityGroupDto> listGroups(UUID viewerId) {
        Set<UUID> joined = viewerId != null
                ? Set.copyOf(memberRepo.findGroupIdByUserId(viewerId))
                : Set.of();
        return groupRepo.findAllByOrderByMembersCountDesc()
                .stream().map(g -> toGroupDto(g, joined.contains(g.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public CommunityGroupDto getGroup(String slug, UUID viewerId) {
        CommunityGroup g = groupRepo.findBySlug(slug)
                .orElseThrow(() -> AppException.notFound("Group not found"));
        boolean joined = viewerId != null && memberRepo.existsByGroupIdAndUserId(g.getId(), viewerId);
        return toGroupDto(g, joined);
    }

    @Transactional
    public CommunityGroupDto createGroup(UUID adminId, CreateGroupRequest req) {
        if (groupRepo.existsBySlug(req.slug())) {
            throw AppException.conflict("A group with this slug already exists");
        }
        User admin = userRepo.findById(adminId)
                .orElseThrow(() -> AppException.notFound("User not found"));
        String color = req.iconColor() != null ? req.iconColor() : "#ed1c24";
        CommunityGroup group = CommunityGroup.builder()
                .slug(req.slug())
                .name(req.name())
                .description(req.description())
                .iconColor(color)
                .createdBy(admin)
                .build();
        group = groupRepo.save(group);
        return toGroupDto(group, false);
    }

    @Transactional
    public void deleteGroup(UUID groupId) {
        if (!groupRepo.existsById(groupId)) throw AppException.notFound("Group not found");
        groupRepo.deleteById(groupId);
    }

    @Transactional
    public CommunityGroupDto toggleMembership(String slug, UUID userId) {
        CommunityGroup group = groupRepo.findBySlug(slug)
                .orElseThrow(() -> AppException.notFound("Group not found"));
        boolean isMember = memberRepo.existsByGroupIdAndUserId(group.getId(), userId);
        if (isMember) {
            memberRepo.deleteByGroupIdAndUserId(group.getId(), userId);
            groupRepo.adjustMembers(group.getId(), -1);
            return toGroupDto(group, false);
        } else {
            memberRepo.save(CommunityGroupMember.builder()
                    .groupId(group.getId()).userId(userId).build());
            groupRepo.adjustMembers(group.getId(), 1);
            return toGroupDto(group, true);
        }
    }

    // ── Posts ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CommunityPostDto> listPosts(int page, int size, String sort, String groupSlug, String search, UUID viewerId) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 50));
        boolean isTop     = "top".equalsIgnoreCase(sort);
        boolean hasGroup  = groupSlug != null && !groupSlug.isBlank();
        boolean hasSearch = search != null && !search.isBlank();
        Page<CommunityPost> posts;

        if (hasGroup) {
            CommunityGroup group = groupRepo.findBySlug(groupSlug)
                    .orElseThrow(() -> AppException.notFound("Group not found"));
            if (hasSearch) {
                posts = isTop
                        ? postRepo.searchByGroupAndLikesDesc(group.getId(), search.trim(), pageable)
                        : postRepo.searchByGroupAndCreatedAtDesc(group.getId(), search.trim(), pageable);
            } else {
                posts = isTop
                        ? postRepo.findByGroupIdOrderByLikesCountDescCreatedAtDesc(group.getId(), pageable)
                        : postRepo.findByGroupIdOrderByCreatedAtDesc(group.getId(), pageable);
            }
        } else if (hasSearch) {
            posts = isTop
                    ? postRepo.searchByLikesDesc(search.trim(), pageable)
                    : postRepo.searchByCreatedAtDesc(search.trim(), pageable);
        } else {
            posts = isTop
                    ? postRepo.findAllByOrderByLikesCountDescCreatedAtDesc(pageable)
                    : postRepo.findAllByOrderByCreatedAtDesc(pageable);
        }

        Set<UUID> likedIds = viewerId != null
                ? Set.copyOf(likeRepo.findPostIdByUserId(viewerId))
                : Set.of();

        return posts.map(p -> toDto(p, likedIds.contains(p.getId()), null));
    }

    @Transactional(readOnly = true)
    public CommunityPostDto getPost(UUID postId, UUID viewerId) {
        CommunityPost post = postRepo.findById(postId)
                .orElseThrow(() -> AppException.notFound("Post not found"));
        boolean liked = viewerId != null && likeRepo.existsByPostIdAndUserId(postId, viewerId);
        List<CommunityCommentDto> comments = commentRepo.findByPostIdOrderByCreatedAtAsc(postId)
                .stream().map(this::toCommentDto).toList();
        return toDto(post, liked, comments);
    }

    @Transactional
    public CommunityPostDto createPost(UUID userId, CreateCommunityPostRequest req) {
        User author = userRepo.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));

        CommunityGroup group = null;
        if (req.groupSlug() != null && !req.groupSlug().isBlank()) {
            group = groupRepo.findBySlug(req.groupSlug())
                    .orElseThrow(() -> AppException.notFound("Group not found"));
        }

        CommunityPost post = CommunityPost.builder()
                .author(author)
                .group(group)
                .title(req.title().trim())
                .content(req.content().trim())
                .imageUrl(req.imageUrl())
                .build();

        post = postRepo.save(post);

        if (group != null) {
            groupRepo.adjustPosts(group.getId(), 1);
        }
        return toDto(post, false, List.of());
    }

    @Transactional
    public CommunityPostDto updatePost(UUID postId, UUID requesterId, UpdatePostRequest req) {
        CommunityPost post = postRepo.findById(postId)
                .orElseThrow(() -> AppException.notFound("Post not found"));
        if (!post.getAuthor().getId().equals(requesterId)) {
            throw AppException.forbidden("You can only edit your own posts");
        }
        if (req.title() != null && !req.title().isBlank()) post.setTitle(req.title().trim());
        if (req.content() != null && !req.content().isBlank()) post.setContent(req.content().trim());
        // imageUrl can be set to null explicitly to remove the image
        if (req.imageUrl() != null || req.removeImage()) {
            post.setImageUrl(req.removeImage() ? null : req.imageUrl());
        }
        post = postRepo.save(post);
        return toDto(post, false, null);
    }

    @Transactional
    public void deletePost(UUID postId, UUID requesterId, boolean isAdmin) {
        CommunityPost post = postRepo.findById(postId)
                .orElseThrow(() -> AppException.notFound("Post not found"));
        if (!isAdmin && !post.getAuthor().getId().equals(requesterId)) {
            throw AppException.forbidden("You can only delete your own posts");
        }
        if (post.getGroup() != null) {
            groupRepo.adjustPosts(post.getGroup().getId(), -1);
        }
        postRepo.delete(post);
    }

    @Transactional
    public LikeToggleDto toggleLike(UUID postId, UUID userId) {
        CommunityPost post = postRepo.findById(postId)
                .orElseThrow(() -> AppException.notFound("Post not found"));
        // Capture the count now — adjustLikes uses a @Modifying JPQL UPDATE which
        // bypasses the JPA first-level cache, so post.getLikesCount() would be stale
        // after the call.  Computing from the snapshot avoids the race-window bug.
        int currentCount = post.getLikesCount();
        boolean alreadyLiked = likeRepo.existsByPostIdAndUserId(postId, userId);
        if (alreadyLiked) {
            likeRepo.deleteByPostIdAndUserId(postId, userId);
            postRepo.adjustLikes(postId, -1);
            return new LikeToggleDto(false, Math.max(0, currentCount - 1));
        } else {
            likeRepo.save(CommunityPostLike.builder().postId(postId).userId(userId).build());
            postRepo.adjustLikes(postId, 1);
            return new LikeToggleDto(true, currentCount + 1);
        }
    }

    @Transactional
    public CommunityCommentDto addComment(UUID postId, UUID userId, CreateCommunityCommentRequest req) {
        CommunityPost post = postRepo.findById(postId)
                .orElseThrow(() -> AppException.notFound("Post not found"));
        User author = userRepo.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));
        CommunityComment comment = CommunityComment.builder()
                .post(post).author(author).content(req.content().trim()).build();
        comment = commentRepo.save(comment);
        postRepo.adjustComments(postId, 1);
        return toCommentDto(comment);
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID requesterId, boolean isAdmin) {
        CommunityComment comment = commentRepo.findById(commentId)
                .orElseThrow(() -> AppException.notFound("Comment not found"));
        if (!isAdmin && !comment.getAuthor().getId().equals(requesterId)) {
            throw AppException.forbidden("You can only delete your own comments");
        }
        postRepo.adjustComments(comment.getPost().getId(), -1);
        commentRepo.delete(comment);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CommunityGroupDto toGroupDto(CommunityGroup g, boolean joinedByMe) {
        return new CommunityGroupDto(
                g.getId().toString(), g.getSlug(), g.getName(), g.getDescription(),
                g.getIconLetter(), g.getIconColor(),
                g.getMembersCount(), g.getPostsCount(), joinedByMe, g.getCreatedAt()
        );
    }

    private CommunityPostDto toDto(CommunityPost p, boolean likedByMe, List<CommunityCommentDto> comments) {
        User a = p.getAuthor();
        String name = (a.getFirstName() + " " + a.getLastName()).trim();
        if (name.isEmpty()) name = a.getEmail();
        CommunityGroup g = p.getGroup();
        return new CommunityPostDto(
                p.getId().toString(), a.getId().toString(), name, a.getAvatarUrl(),
                g != null ? g.getId().toString() : null,
                g != null ? g.getSlug() : null,
                g != null ? g.getName() : null,
                p.getTitle(), p.getContent(), p.getImageUrl(),
                p.getLikesCount(), p.getCommentsCount(), likedByMe,
                p.getCreatedAt(), p.getUpdatedAt(), comments
        );
    }

    private CommunityCommentDto toCommentDto(CommunityComment c) {
        User a = c.getAuthor();
        String name = (a.getFirstName() + " " + a.getLastName()).trim();
        if (name.isEmpty()) name = a.getEmail();
        return new CommunityCommentDto(
                c.getId().toString(), a.getId().toString(), name, a.getAvatarUrl(),
                c.getContent(), c.getCreatedAt()
        );
    }
}
