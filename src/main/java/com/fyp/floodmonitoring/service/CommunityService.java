package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.*;
import com.fyp.floodmonitoring.dto.response.*;
import com.fyp.floodmonitoring.entity.*;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityPostRepository postRepo;
    private final CommunityCommentRepository commentRepo;
    private final CommunityCommentVoteRepository voteRepo;
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
        boolean isTop = "top".equalsIgnoreCase(sort);
        boolean hasGroup = groupSlug != null && !groupSlug.isBlank();
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

        // Batch live comment counts — one query for the whole page, avoiding N+1
        List<UUID> postIds = posts.getContent().stream().map(CommunityPost::getId).toList();
        Map<UUID, Integer> liveCounts = new HashMap<>();
        if (!postIds.isEmpty()) {
            for (Object[] row : commentRepo.countByPostIdIn(postIds)) {
                liveCounts.put((UUID) row[0], ((Long) row[1]).intValue());
            }
        }

        return posts.map(p -> toDto(p, likedIds.contains(p.getId()), null,
                liveCounts.getOrDefault(p.getId(), p.getCommentsCount())));
    }

    @Transactional(readOnly = true)
    public CommunityPostDto getPost(UUID postId, UUID viewerId) {
        CommunityPost post = postRepo.findById(postId)
                .orElseThrow(() -> AppException.notFound("Post not found"));
        boolean liked = viewerId != null && likeRepo.existsByPostIdAndUserId(postId, viewerId);
        return toDto(post, liked, null);
    }

    @Transactional(readOnly = true)
    public CommunityCommentsPageDto listComments(UUID postId, UUID viewerId, String sort, int page, int size) {
        if (!postRepo.existsById(postId)) {
            throw AppException.notFound("Post not found");
        }
        String safeSort = switch (sort != null ? sort.toLowerCase() : "new") {
            case "top", "old" -> sort.toLowerCase();
            default -> "new";
        };
        int safeSize = Math.max(1, Math.min(size, 50));
        List<CommunityComment> all = commentRepo.findByPost_IdOrderByCreatedAtAsc(postId);
        List<CommunityComment> roots = all.stream().filter(c -> c.getParent() == null).toList();

        List<CommunityComment> sortedRoots = new ArrayList<>(roots);
        Comparator<CommunityComment> cmp = switch (safeSort) {
            case "top" -> Comparator.comparingInt(CommunityComment::getScore).reversed()
                    .thenComparing(CommunityComment::getCreatedAt);
            case "old" -> Comparator.comparing(CommunityComment::getCreatedAt);
            default -> Comparator.comparing(CommunityComment::getCreatedAt).reversed();
        };
        sortedRoots.sort(cmp);

        int total = sortedRoots.size();
        int from = Math.min(page * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<CommunityComment> pageRoots = sortedRoots.subList(from, to);

        Map<UUID, List<UUID>> byParent = new HashMap<>();
        for (CommunityComment c : all) {
            if (c.getParent() != null) {
                byParent.computeIfAbsent(c.getParent().getId(), k -> new ArrayList<>()).add(c.getId());
            }
        }

        Set<UUID> visible = new LinkedHashSet<>();
        for (CommunityComment root : pageRoots) {
            visible.add(root.getId());
            collectDescendants(root.getId(), byParent, visible);
        }

        List<CommunityComment> slice = all.stream().filter(c -> visible.contains(c.getId())).toList();

        Map<UUID, Integer> replyCountMap = directReplyCounts(all);
        Map<UUID, Integer> voteMap = loadMyVotes(slice, viewerId);

        List<CommunityCommentDto> dtos = slice.stream()
                .map(c -> toCommentDto(c, voteMap.getOrDefault(c.getId(), 0),
                        replyCountMap.getOrDefault(c.getId(), 0)))
                .toList();

        return new CommunityCommentsPageDto(dtos, total, page, safeSize, all.size());
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
        return toDto(post, false, null);
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
        int currentCount = Math.max(0, post.getLikesCount());
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

    // ── Comments ──────────────────────────────────────────────────────────────

    @Transactional
    public CommunityCommentDto addComment(UUID postId, UUID userId, CreateCommunityCommentRequest req) {
        CommunityPost post = postRepo.findById(postId)
                .orElseThrow(() -> AppException.notFound("Post not found"));
        User author = userRepo.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));

        CommunityComment parent = null;
        if (req.parentId() != null && !req.parentId().isBlank()) {
            UUID parentId = UUID.fromString(req.parentId().trim());
            parent = commentRepo.findById(parentId)
                    .orElseThrow(() -> AppException.notFound("Parent comment not found"));
            if (!parent.getPost().getId().equals(postId)) {
                throw AppException.badRequest("INVALID_PARENT", "Parent comment belongs to a different post");
            }
            if (parent.getDeletedAt() != null) {
                throw AppException.badRequest("INVALID_PARENT", "Cannot reply to a deleted comment");
            }
        }

        CommunityComment comment = CommunityComment.builder()
                .post(post)
                .author(author)
                .parent(parent)
                .content(req.content().trim())
                .build();
        comment = commentRepo.save(comment);
        postRepo.adjustComments(postId, 1);

        int replies = (int) commentRepo.countByParent_Id(comment.getId());
        return toCommentDto(comment, 0, replies);
    }

    @Transactional
    public CommunityCommentDto editComment(UUID postId, UUID commentId, UUID userId, UpdateCommunityCommentRequest req) {
        CommunityComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> AppException.notFound("Comment not found"));
        if (!c.getPost().getId().equals(postId)) {
            throw AppException.badRequest("INVALID_POST", "Comment does not belong to this post");
        }
        if (c.getDeletedAt() != null) {
            throw AppException.badRequest("DELETED", "Cannot edit a deleted comment");
        }
        if (!c.getAuthor().getId().equals(userId)) {
            throw AppException.forbidden("You can only edit your own comments");
        }
        c.setContent(req.content().trim());
        c.setUpdatedAt(Instant.now());
        c = commentRepo.save(c);
        int replies = (int) commentRepo.countByParent_Id(c.getId());
        int myVote = voteRepo.findByComment_IdAndUser_Id(c.getId(), userId).map(CommunityCommentVote::getValue).orElse(0);
        return toCommentDto(c, myVote, replies);
    }

    @Transactional
    public void deleteComment(UUID postId, UUID commentId, UUID requesterId, boolean isAdmin) {
        CommunityComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> AppException.notFound("Comment not found"));
        if (!c.getPost().getId().equals(postId)) {
            throw AppException.badRequest("INVALID_POST", "Comment does not belong to this post");
        }
        if (!isAdmin && !c.getAuthor().getId().equals(requesterId)) {
            throw AppException.forbidden("You can only delete your own comments");
        }
        User deleter = requesterId != null ? userRepo.findById(requesterId).orElse(null) : null;
        softOrHardDelete(c, deleter, isAdmin);
    }

    @Transactional
    public CommentVoteResponseDto voteComment(UUID postId, UUID commentId, UUID userId, VoteCommentRequest req) {
        int value = req.value();
        if (value < -1 || value > 1) {
            throw AppException.badRequest("INVALID_VOTE", "Vote must be -1, 0, or 1");
        }
        CommunityComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> AppException.notFound("Comment not found"));
        if (!c.getPost().getId().equals(postId)) {
            throw AppException.badRequest("INVALID_POST", "Comment does not belong to this post");
        }
        if (c.getDeletedAt() != null) {
            throw AppException.badRequest("DELETED", "Cannot vote on a deleted comment");
        }

        User voter = userRepo.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));

        if (value == 0) {
            voteRepo.deleteByComment_IdAndUser_Id(commentId, userId);
        } else {
            CommunityCommentVote existing = voteRepo.findByComment_IdAndUser_Id(commentId, userId).orElse(null);
            if (existing == null) {
                voteRepo.save(CommunityCommentVote.builder()
                        .comment(c)
                        .user(voter)
                        .value(value)
                        .build());
            } else {
                existing.setValue(value);
                voteRepo.save(existing);
            }
        }

        int sum = voteRepo.sumValueForComment(commentId);
        c.setScore(sum);
        commentRepo.save(c);

        int myVote = voteRepo.findByComment_IdAndUser_Id(commentId, userId).map(CommunityCommentVote::getValue).orElse(0);
        return new CommentVoteResponseDto(sum, myVote);
    }

    @Transactional
    public void moderateComment(UUID commentId, UUID adminId, ModerateCommentRequest req) {
        String action = req.action().trim().toLowerCase();
        CommunityComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> AppException.notFound("Comment not found"));
        User admin = userRepo.findById(adminId)
                .orElseThrow(() -> AppException.notFound("User not found"));

        switch (action) {
            case "hide" -> {
                if (c.getDeletedAt() == null) {
                    softHide(c, admin);
                }
            }
            case "restore" -> {
                if (c.getDeletedAt() == null) return;
                if (c.getContentBackup() != null) {
                    c.setContent(c.getContentBackup());
                    c.setContentBackup(null);
                }
                c.setDeletedAt(null);
                c.setDeletedBy(null);
                commentRepo.save(c);
            }
            case "delete" -> softOrHardDelete(c, admin, true);
            default -> throw AppException.badRequest("INVALID_ACTION", "Unknown action: " + action);
        }
    }

    /** Moderator hide — always preserves row + backup so restore works. Decrements post counter. */
    private void softHide(CommunityComment c, User moderator) {
        if (c.getContent() != null && !c.getContent().isEmpty()) {
            c.setContentBackup(c.getContent());
        }
        c.setContent("");
        c.setDeletedAt(Instant.now());
        c.setDeletedBy(moderator);
        commentRepo.save(c);
        postRepo.adjustComments(c.getPost().getId(), -1);
    }

    @Transactional(readOnly = true)
    public Page<AdminCommentListItemDto> adminListComments(int page, int size) {
        PageRequest pr = PageRequest.of(page, Math.min(size, 50));
        return commentRepo.findAllByOrderByCreatedAtDesc(pr).map(this::toAdminItem);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void collectDescendants(UUID rootId, Map<UUID, List<UUID>> byParent, Set<UUID> visible) {
        Deque<UUID> q = new ArrayDeque<>(byParent.getOrDefault(rootId, List.of()));
        while (!q.isEmpty()) {
            UUID id = q.poll();
            if (!visible.add(id)) continue;
            q.addAll(byParent.getOrDefault(id, List.of()));
        }
    }

    private Map<UUID, Integer> directReplyCounts(List<CommunityComment> all) {
        Map<UUID, Integer> m = new HashMap<>();
        for (CommunityComment c : all) {
            if (c.getParent() != null) {
                m.merge(c.getParent().getId(), 1, Integer::sum);
            }
        }
        return m;
    }

    private Map<UUID, Integer> loadMyVotes(List<CommunityComment> slice, UUID viewerId) {
        if (viewerId == null || slice.isEmpty()) return Map.of();
        List<UUID> ids = slice.stream().map(CommunityComment::getId).toList();
        return voteRepo.findByComment_IdInAndUser_Id(ids, viewerId).stream()
                .collect(Collectors.toMap(v -> v.getComment().getId(), CommunityCommentVote::getValue));
    }

    private void softOrHardDelete(CommunityComment c, User deletedBy, boolean isAdmin) {
        UUID postId = c.getPost().getId();
        long children = commentRepo.countByParent_Id(c.getId());
        if (children > 0) {
            // Soft delete — keep row so children stay intact, but clear content
            if (c.getContent() != null && !c.getContent().isEmpty()) {
                c.setContentBackup(c.getContent());
            }
            c.setContent("");
            c.setDeletedAt(Instant.now());
            c.setDeletedBy(deletedBy);
            commentRepo.save(c);
        } else {
            voteRepo.deleteByComment_Id(c.getId());
            commentRepo.delete(c);
        }
        // Always decrement the cached counter regardless of soft vs hard delete
        postRepo.adjustComments(postId, -1);
    }

    private AdminCommentListItemDto toAdminItem(CommunityComment c) {
        CommunityPost p = c.getPost();
        User a = c.getAuthor();
        String postTitle = p.getTitle();
        String parentStr = c.getParent() != null ? c.getParent().getId().toString() : null;
        boolean deleted = c.getDeletedAt() != null;
        return new AdminCommentListItemDto(
                c.getId().toString(),
                p.getId().toString(),
                postTitle,
                parentStr,
                a.getId().toString(),
                displayName(a),
                deleted ? "[deleted]" : c.getContent(),
                c.getScore(),
                deleted,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private CommunityGroupDto toGroupDto(CommunityGroup g, boolean joinedByMe) {
        return new CommunityGroupDto(
                g.getId().toString(), g.getSlug(), g.getName(), g.getDescription(),
                g.getIconLetter(), g.getIconColor(),
                g.getMembersCount(), g.getPostsCount(), joinedByMe, g.getCreatedAt()
        );
    }

    private CommunityPostDto toDto(CommunityPost p, boolean likedByMe, List<CommunityCommentDto> comments) {
        return toDto(p, likedByMe, comments, p.getCommentsCount());
    }

    private CommunityPostDto toDto(CommunityPost p, boolean likedByMe, List<CommunityCommentDto> comments, int commentsCount) {
        User a = p.getAuthor();
        String name = displayName(a);
        CommunityGroup g = p.getGroup();
        return new CommunityPostDto(
                p.getId().toString(), a.getId().toString(), name, a.getAvatarUrl(),
                g != null ? g.getId().toString() : null,
                g != null ? g.getSlug() : null,
                g != null ? g.getName() : null,
                p.getTitle(), p.getContent(), p.getImageUrl(),
                Math.max(0, p.getLikesCount()), Math.max(0, commentsCount), likedByMe,
                p.getCreatedAt(), p.getUpdatedAt(), comments
        );
    }

    private CommunityCommentDto toCommentDto(CommunityComment c, int myVote, int replyCount) {
        User a = c.getAuthor();
        boolean deleted = c.getDeletedAt() != null;
        String name = deleted ? "[deleted]" : displayName(a);
        String content = deleted ? "[deleted]" : c.getContent();
        String authorId = deleted ? "" : a.getId().toString();
        String avatar = deleted ? null : a.getAvatarUrl();
        String parentId = c.getParent() != null ? c.getParent().getId().toString() : null;
        return new CommunityCommentDto(
                c.getId().toString(),
                parentId,
                authorId,
                name,
                avatar,
                content,
                c.getScore(),
                myVote,
                c.getCreatedAt(),
                c.getUpdatedAt(),
                deleted,
                replyCount
        );
    }

    private static String displayName(User a) {
        String name = (a.getFirstName() + " " + a.getLastName()).trim();
        if (name.isEmpty()) name = a.getEmail();
        return name;
    }
}
