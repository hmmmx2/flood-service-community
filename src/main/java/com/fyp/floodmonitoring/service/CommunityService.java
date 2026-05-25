package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.*;
import com.fyp.floodmonitoring.dto.response.*;
import com.fyp.floodmonitoring.entity.*;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.*;
import com.fyp.floodmonitoring.service.notifications.InAppProvider;
import com.fyp.floodmonitoring.service.notifications.NotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityPostRepository postRepo;
    private final CommunityCommentRepository commentRepo;
    private final CommunityCommentVoteRepository voteRepo;
    private final CommunityPostLikeRepository likeRepo;
    private final ContentReportRepository contentReportRepo;
    private final CommunityGroupRepository groupRepo;
    private final CommunityGroupMemberRepository memberRepo;
    private final UserRepository userRepo;
    private final UserSettingRepository userSettingRepo;
    private final InAppProvider inAppProvider;
    private final EmailService emailService;

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

    /**
     * Partial update of a group's editable fields (name / description /
     * icon colour). Slug is immutable. The avatar letter is re-derived from
     * the name so it stays in sync. Only non-null/non-blank values are
     * applied, so the caller can patch a single field.
     */
    @Transactional
    public CommunityGroupDto updateGroup(UUID groupId, UpdateGroupRequest req) {
        CommunityGroup group = groupRepo.findById(groupId)
                .orElseThrow(() -> AppException.notFound("Group not found"));
        if (req.name() != null && !req.name().isBlank()) {
            String name = req.name().trim();
            group.setName(name);
            group.setIconLetter(name.substring(0, 1).toUpperCase());
        }
        if (req.description() != null) {
            group.setDescription(req.description());
        }
        if (req.iconColor() != null && !req.iconColor().isBlank()) {
            group.setIconColor(req.iconColor());
        }
        group = groupRepo.save(group);
        return toGroupDto(group, false);
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

    // ── Public user profile ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PublicUserProfileDto getPublicUserProfile(UUID userId) {
        User u = userRepo.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));
        long posts = postRepo.countByAuthor_Id(userId);
        long comments = commentRepo.countActiveByAuthorId(userId);
        return new PublicUserProfileDto(
                u.getId().toString(),
                displayName(u),
                u.getAvatarUrl(),
                u.getRole(),
                u.getCreatedAt(),
                posts,
                comments);
    }

    @Transactional(readOnly = true)
    public Page<CommunityPostDto> listPostsByUser(UUID authorId, UUID viewerId, int page, int size) {
        if (!userRepo.existsById(authorId)) {
            throw AppException.notFound("User not found");
        }
        PageRequest pageable = PageRequest.of(page, Math.min(size, 50));
        Page<CommunityPost> posts = postRepo.findByAuthor_IdOrderByCreatedAtDesc(authorId, pageable);

        Set<UUID> likedIds = viewerId != null
                ? Set.copyOf(likeRepo.findPostIdByUserId(viewerId))
                : Set.of();

        List<UUID> postIds = posts.getContent().stream().map(CommunityPost::getId).toList();
        Map<UUID, Integer> liveCounts = new HashMap<>();
        if (!postIds.isEmpty()) {
            for (Object[] row : commentRepo.countByPostIdIn(postIds)) {
                liveCounts.put((UUID) row[0], ((Long) row[1]).intValue());
            }
        }
        return posts.map(p -> toDto(p, likedIds.contains(p.getId()), null,
                liveCounts.getOrDefault(p.getId(), 0)));
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

        // Always trust the live count from the comments table — the denormalized
        // community_posts.comments_count drifts whenever a comment is removed
        // outside the soft-delete path (e.g. parent hard-deleted via FK SET NULL,
        // manual cleanup, prior bug versions). Falling back to it caused the
        // listing badge to disagree with the post-detail badge.
        return posts.map(p -> toDto(p, likedIds.contains(p.getId()), null,
                liveCounts.getOrDefault(p.getId(), 0)));
    }

    @Transactional(readOnly = true)
    public CommunityPostDto getPost(UUID postId, UUID viewerId) {
        CommunityPost post = postRepo.findById(postId)
                .orElseThrow(() -> AppException.notFound("Post not found"));
        boolean liked = viewerId != null && likeRepo.existsByPostIdAndUserId(postId, viewerId);
        // Always count comments live for the detail view so the badge
        // matches the actual rows shown in the comment list. The
        // denormalized community_posts.comments_count drifts when comments
        // are removed outside the soft-delete path (e.g. seed data, manual
        // SQL, hard delete cascades) — we'd otherwise show "5 Comments"
        // above an empty list.
        int liveCount = (int) commentRepo.countByPost_Id(postId);
        return toDto(post, liked, null, liveCount);
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
        List<CommunityComment> all = commentRepo.findByPostIdOrderByCreatedAtAsc(postId);
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

            // Notify every member of the group (except the author) that
            // a new post landed. Best-effort — failures don't block the
            // post itself.
            try {
                List<UUID> recipients = memberRepo.findUserIdByGroupId(group.getId());
                String authorName = displayName(author);
                String snippet = trimSnippet(post.getContent());
                String link = "/post/" + post.getId();
                String title = "New post in " + group.getName();
                String body = authorName + ": " + truncate(post.getTitle(), 80)
                        + (snippet.isEmpty() ? "" : " — " + snippet);
                for (UUID memberId : recipients) {
                    if (memberId == null || memberId.equals(userId)) continue;
                    inAppProvider.deliver(memberId, new NotificationPayload(
                            "community.post",
                            "info",
                            title,
                            body,
                            null,
                            link));
                }
            } catch (Exception e) {
                log.warn("[Community] Failed to dispatch new-post notifications: {}", e.getMessage());
            }
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

        // Application-level cascade. The child FKs are NO ACTION (no DB-level
        // ON DELETE CASCADE — and any cascade from a fresh schema did not
        // survive the Neon→Railway migration), so deleting a post that has
        // any comment / like / vote otherwise raises a constraint violation
        // that surfaces to the user as a 500. Remove dependants in
        // dependency order, then the post:
        //   1. moderation reports on the post + its comments (no FK — hygiene,
        //      and the comment query must run before the comments are gone)
        //   2. votes on the post's comments (FK community_comment_votes.comment_id)
        //   3. the comments                 (FK community_comments.post_id)
        //   4. likes on the post            (FK community_post_likes.post_id)
        final UUID groupId = post.getGroup() != null ? post.getGroup().getId() : null;
        contentReportRepo.deleteCommentReportsForPost(postId);
        contentReportRepo.deleteByTarget("POST", postId);
        voteRepo.deleteByPostId(postId);
        commentRepo.deleteAllByPostId(postId);
        likeRepo.deleteByPostId(postId);
        if (groupId != null) {
            groupRepo.adjustPosts(groupId, -1);
        }
        // deleteById re-fetches a managed instance — the bulk @Modifying
        // deletes above clear the persistence context, detaching `post`.
        postRepo.deleteById(postId);
    }

    /**
     * Idempotent like toggle. Catches the composite-PK unique violation so
     * concurrent clicks from the same user (double-tap, network retry,
     * two browser tabs) don't surface as a 500. Whichever transaction
     * wins the insert "owns" the side-effect; the loser silently treats
     * the conflict as "already liked, nothing more to do" and returns the
     * canonical count from the database.
     */
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
        }
        try {
            likeRepo.save(CommunityPostLike.builder().postId(postId).userId(userId).build());
            postRepo.adjustLikes(postId, 1);
            return new LikeToggleDto(true, currentCount + 1);
        } catch (DataIntegrityViolationException e) {
            // Another concurrent click already inserted the row — treat
            // this side as a no-op and return the canonical state.
            log.debug("[Community] toggleLike race for postId={} userId={} — treated as already liked",
                    postId, userId);
            return new LikeToggleDto(true, Math.max(0, currentCount));
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
            UUID parentId;
            try {
                parentId = UUID.fromString(req.parentId().trim());
            } catch (IllegalArgumentException ex) {
                throw AppException.badRequest("INVALID_PARENT", "Parent comment id is not a valid UUID");
            }
            parent = commentRepo.findById(parentId)
                    .orElseThrow(() -> AppException.notFound("Parent comment not found"));
            if (!parent.getPost().getId().equals(postId)) {
                throw AppException.badRequest("INVALID_PARENT", "Parent comment belongs to a different post");
            }
            if (parent.getDeletedAt() != null) {
                throw AppException.badRequest("INVALID_PARENT", "Cannot reply to a deleted comment");
            }
        }

        String content = req.content().trim();

        // Soft duplicate-comment guard. If the same user posted the exact
        // same content on this post within the last 10 seconds, reject as
        // a probable double-tap or "spam Enter" attack. The rate limiter
        // catches volume; this catches the "spam Enter, same text" case
        // that doesn't trip the per-minute window because it's only one
        // duplicate per accidental click.
        commentRepo.findMostRecentByAuthorOnPost(postId, userId, 10)
                .filter(prev -> content.equals(prev.getContent()))
                .ifPresent(prev -> {
                    throw AppException.tooManyRequests(
                            "DUPLICATE_COMMENT",
                            "You just posted that. Wait a few seconds before sending again.");
                });

        CommunityComment comment = CommunityComment.builder()
                .post(post)
                .author(author)
                .parent(parent)
                .content(content)
                .build();
        // saveAndFlush guarantees the INSERT hits the DB inside this transaction
        // before any subsequent bulk UPDATE / persistence-context manipulation can
        // discard it. Plain save() left the INSERT pending; combined with the old
        // adjustComments(clearAutomatically=true) that silently dropped the row.
        comment = commentRepo.saveAndFlush(comment);
        postRepo.adjustComments(postId, 1);
        log.info("[Community] Saved comment id={} postId={} authorId={} parentId={}",
                comment.getId(), postId, userId, parent != null ? parent.getId() : null);

        // Notify the post author when someone comments on their post,
        // and notify the parent comment's author when someone replies
        // to their comment. Both skip self-notifications. Failures are
        // swallowed — the comment itself is the source of truth.
        try {
            String snippet = trimSnippet(comment.getContent());
            String postLink = "/post/" + postId;
            String authorName = displayName(author);

            if (parent == null) {
                User postAuthor = post.getAuthor();
                if (postAuthor != null && !postAuthor.getId().equals(userId)) {
                    String commentDeepLink = postLink + "#comment-" + comment.getId();
                    inAppProvider.deliver(postAuthor.getId(), new NotificationPayload(
                            "community.comment",
                            "info",
                            authorName + " commented on \"" + truncate(post.getTitle(), 60) + "\"",
                            snippet,
                            null,
                            commentDeepLink));
                    maybeSendInteractionEmail(postAuthor, authorName,
                            "commented on your post", snippet,
                            post.getTitle(), commentDeepLink);
                }
            } else {
                User parentAuthor = parent.getAuthor();
                String replyDeepLink = postLink + "#comment-" + comment.getId();
                if (parentAuthor != null && !parentAuthor.getId().equals(userId)) {
                    inAppProvider.deliver(parentAuthor.getId(), new NotificationPayload(
                            "community.reply",
                            "info",
                            authorName + " replied to your comment",
                            snippet,
                            null,
                            replyDeepLink));
                    maybeSendInteractionEmail(parentAuthor, authorName,
                            "replied to your comment", snippet,
                            post.getTitle(), replyDeepLink);
                }
                // Also let the post author know their thread is active —
                // unless they are the replier or already the parent author.
                User postAuthor = post.getAuthor();
                if (postAuthor != null
                        && !postAuthor.getId().equals(userId)
                        && (parentAuthor == null || !postAuthor.getId().equals(parentAuthor.getId()))) {
                    inAppProvider.deliver(postAuthor.getId(), new NotificationPayload(
                            "community.comment",
                            "info",
                            "New reply on \"" + truncate(post.getTitle(), 60) + "\"",
                            snippet,
                            null,
                            replyDeepLink));
                    maybeSendInteractionEmail(postAuthor, authorName,
                            "replied on your post", snippet,
                            post.getTitle(), replyDeepLink);
                }
            }
        } catch (Exception e) {
            log.warn("[Community] Failed to dispatch comment notification: {}", e.getMessage());
        }

        int replies = (int) commentRepo.countByParent_Id(comment.getId());
        return toCommentDto(comment, 0, replies);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String trimSnippet(String s) {
        return truncate(s == null ? "" : s.replaceAll("\\s+", " ").trim(), 140);
    }

    /**
     * Sends a "someone interacted with your content" email if the recipient
     * has emailAlerts enabled. Best-effort — exceptions are swallowed so an
     * email failure can never affect the comment transaction. The actual
     * Resend HTTP call already runs @Async; the gating check is cheap.
     */
    private void maybeSendInteractionEmail(
            User recipient, String actorName, String verb,
            String snippet, String postTitle, String relativePath) {
        try {
            if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) return;
            boolean wantsEmail = userSettingRepo
                    .findByUserIdAndKey(recipient.getId(), "emailAlerts")
                    .map(s -> Boolean.TRUE.equals(s.getEnabled()))
                    .orElse(true); // opt-out model — default ON until the user disables it
            if (!wantsEmail) return;
            emailService.sendSocialInteractionEmail(
                    recipient.getEmail(), actorName, verb, snippet, postTitle, relativePath);
        } catch (Exception e) {
            log.warn("[Community] Failed to dispatch interaction email to userId={}: {}",
                    recipient != null ? recipient.getId() : null, e.getMessage());
        }
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
        c = commentRepo.saveAndFlush(c);
        int replies = (int) commentRepo.countByParent_Id(c.getId());
        int myVote = voteRepo.findByComment_IdAndUser_Id(c.getId(), userId).map(CommunityCommentVote::getValue).orElse(0);
        log.info("[Community] Edited comment id={} postId={} authorId={}", c.getId(), postId, userId);
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
                try {
                    voteRepo.save(CommunityCommentVote.builder()
                            .comment(c)
                            .user(voter)
                            .value(value)
                            .build());
                } catch (DataIntegrityViolationException e) {
                    // Another concurrent click already inserted the vote.
                    // Read it back and update with the latest value instead.
                    CommunityCommentVote latest = voteRepo
                            .findByComment_IdAndUser_Id(commentId, userId)
                            .orElseThrow(() -> e);
                    latest.setValue(value);
                    voteRepo.save(latest);
                }
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
        UUID commentId = c.getId();
        long children = commentRepo.countByParent_Id(commentId);
        if (children > 0) {
            // Soft delete — keep row so children stay intact, but clear content
            if (c.getContent() != null && !c.getContent().isEmpty()) {
                c.setContentBackup(c.getContent());
            }
            c.setContent("");
            c.setDeletedAt(Instant.now());
            c.setDeletedBy(deletedBy);
            commentRepo.saveAndFlush(c);
            log.info("[Community] Soft-deleted comment id={} postId={} (kept {} children)",
                    commentId, postId, children);
        } else {
            voteRepo.deleteByComment_Id(commentId);
            commentRepo.delete(c);
            commentRepo.flush();
            log.info("[Community] Hard-deleted comment id={} postId={}", commentId, postId);
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
        // Report LIVE counts from the source tables instead of the
        // denormalized members_count / posts_count columns. Those columns
        // are maintained by +/- increments and drift out of sync whenever
        // rows are created outside that path — e.g. posts seeded directly
        // into the DB (which is exactly why a group could show "0 posts"
        // while its feed clearly has posts). Counting live keeps the
        // About panel honest regardless of how the rows got there.
        int liveMembers = (int) memberRepo.countByGroupId(g.getId());
        int livePosts = (int) postRepo.countByGroupId(g.getId());
        return new CommunityGroupDto(
                g.getId().toString(), g.getSlug(), g.getName(), g.getDescription(),
                g.getIconLetter(), g.getIconColor(),
                liveMembers, livePosts, joinedByMe, g.getCreatedAt()
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
