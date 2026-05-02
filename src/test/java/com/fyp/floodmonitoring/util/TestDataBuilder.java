package com.fyp.floodmonitoring.util;

import com.fyp.floodmonitoring.dto.response.*;
import com.fyp.floodmonitoring.entity.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Centralised builder for test fixture objects.
 *
 * <p>All factory methods return fully-populated instances that are ready for use
 * in unit and integration tests without requiring a database. Call the fluent
 * setter methods on the returned entity to override specific fields before use.</p>
 *
 * <pre>{@code
 * User admin = TestDataBuilder.buildAdmin();
 * admin.setEmail("custom@test.com");
 *
 * Blog blog = TestDataBuilder.buildBlog();
 * }</pre>
 */
public final class TestDataBuilder {

    private TestDataBuilder() {}

    // ── Users ─────────────────────────────────────────────────────────────────

    /** Returns a {@code customer}-role {@link User} with populated fields. */
    public static User buildUser() {
        User user = new User();
        user.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john.doe@test.com");
        user.setPasswordHash("$2a$12$hashedpassword");
        user.setRole("customer");
        user.setPhone("+60111234567");
        user.setLocationLabel("Kuching, Sarawak");
        user.setAvatarUrl(null);
        user.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        user.setUpdatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        return user;
    }

    /** Returns an {@code admin}-role {@link User}. */
    public static User buildAdmin() {
        User admin = new User();
        admin.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setEmail("admin@test.com");
        admin.setPasswordHash("$2a$12$hashedadmin");
        admin.setRole("admin");
        admin.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        admin.setUpdatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        return admin;
    }

    /** Returns a {@link UserProfileDto} matching {@link #buildUser()}. */
    public static UserProfileDto buildUserProfileDto() {
        return new UserProfileDto(
                "00000000-0000-0000-0000-000000000001",
                "john.doe@test.com",
                "John",
                "Doe",
                "John Doe",
                "Customer",
                "+60111234567",
                "Kuching, Sarawak",
                null
        );
    }

    // ── Blogs ─────────────────────────────────────────────────────────────────

    /** Returns a persisted-looking {@link Blog} entity. */
    public static Blog buildBlog() {
        Blog blog = new Blog();
        blog.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        blog.setTitle("Flood Safety Tips");
        blog.setBody("Stay safe during floods by following these guidelines...");
        blog.setImageKey("blog-1");
        blog.setImageUrl(null);
        blog.setCategory("Safety Tips");
        blog.setIsFeatured(false);
        blog.setCreatedAt(Instant.parse("2025-06-01T00:00:00Z"));
        blog.setUpdatedAt(Instant.parse("2025-06-01T00:00:00Z"));
        return blog;
    }

    /** Returns a featured {@link Blog} entity. */
    public static Blog buildFeaturedBlog() {
        Blog blog = buildBlog();
        blog.setId(UUID.fromString("00000000-0000-0000-0000-000000000011"));
        blog.setTitle("Understanding Flood Levels");
        blog.setIsFeatured(true);
        return blog;
    }

    /** Returns a {@link BlogDto} matching {@link #buildBlog()}. */
    public static BlogDto buildBlogDto() {
        return new BlogDto(
                "00000000-0000-0000-0000-000000000010",
                "blog-1",
                null,
                "Safety Tips",
                "Flood Safety Tips",
                "Stay safe during floods by following these guidelines...",
                false,
                "2025-06-01T00:00:00Z",
                "2025-06-01T00:00:00Z"
        );
    }

    /** Returns a featured {@link BlogDto}. */
    public static BlogDto buildFeaturedBlogDto() {
        return new BlogDto(
                "00000000-0000-0000-0000-000000000011",
                "blog-1",
                null,
                "Safety Tips",
                "Understanding Flood Levels",
                "Floods are categorised into four levels...",
                true,
                "2025-06-01T00:00:00Z",
                "2025-06-01T00:00:00Z"
        );
    }

    // ── Community ─────────────────────────────────────────────────────────────

    /** Returns a {@link CommunityGroup} entity. */
    public static CommunityGroup buildGroup() {
        User creator = buildAdmin();
        CommunityGroup group = new CommunityGroup();
        group.setId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        group.setSlug("flood-alerts");
        group.setName("Flood Alerts");
        group.setDescription("Community discussion about flood alerts in Kuching");
        group.setIconLetter("F");
        group.setIconColor("#ed1c24");
        group.setMembersCount(10);
        group.setPostsCount(5);
        group.setCreatedBy(creator);
        group.setCreatedAt(Instant.parse("2025-01-15T00:00:00Z"));
        return group;
    }

    /** Returns a {@link CommunityGroupDto} matching {@link #buildGroup()}. */
    public static CommunityGroupDto buildGroupDto() {
        return new CommunityGroupDto(
                "00000000-0000-0000-0000-000000000020",
                "flood-alerts",
                "Flood Alerts",
                "Community discussion about flood alerts in Kuching",
                "F",
                "#ed1c24",
                10,
                5,
                false,
                Instant.parse("2025-01-15T00:00:00Z")
        );
    }

    /** Returns a {@link CommunityPost} entity with a populated author. */
    public static CommunityPost buildPost() {
        User author = buildUser();
        CommunityPost post = new CommunityPost();
        post.setId(UUID.fromString("00000000-0000-0000-0000-000000000030"));
        post.setAuthor(author);
        post.setGroup(null);
        post.setTitle("Flood update in Kuching area");
        post.setContent("Water levels have risen significantly near the river banks...");
        post.setImageUrl(null);
        post.setLikesCount(3);
        post.setCommentsCount(1);
        post.setCreatedAt(Instant.parse("2025-06-15T10:00:00Z"));
        post.setUpdatedAt(Instant.parse("2025-06-15T10:00:00Z"));
        return post;
    }

    /** Returns a {@link CommunityPostDto}. */
    public static CommunityPostDto buildPostDto() {
        return new CommunityPostDto(
                "00000000-0000-0000-0000-000000000030",
                "00000000-0000-0000-0000-000000000001",
                "John Doe",
                null,
                null, null, null,
                "Flood update in Kuching area",
                "Water levels have risen significantly near the river banks...",
                null,
                3, 1, false,
                Instant.parse("2025-06-15T10:00:00Z"),
                Instant.parse("2025-06-15T10:00:00Z"),
                List.of()
        );
    }

    /** Returns a {@link CommunityComment} entity. */
    public static CommunityComment buildComment() {
        CommunityComment comment = new CommunityComment();
        comment.setId(UUID.fromString("00000000-0000-0000-0000-000000000040"));
        comment.setAuthor(buildUser());
        comment.setPost(buildPost());
        comment.setContent("Thanks for the update!");
        comment.setCreatedAt(Instant.parse("2025-06-15T11:00:00Z"));
        return comment;
    }

    /** Returns a {@link CommunityCommentDto}. */
    public static CommunityCommentDto buildCommentDto() {
        return new CommunityCommentDto(
                "00000000-0000-0000-0000-000000000040",
                "00000000-0000-0000-0000-000000000001",
                "John Doe",
                null,
                "Thanks for the update!",
                Instant.parse("2025-06-15T11:00:00Z")
        );
    }

    // ── Sensors / Nodes ───────────────────────────────────────────────────────

    /** Returns an active {@link Node} entity with level 1 (normal). */
    public static Node buildNode() {
        Node node = new Node();
        node.setId(UUID.fromString("00000000-0000-0000-0000-000000000050"));
        node.setNodeId("102782478");
        node.setName("Node 102782478");
        node.setLatitude(1.5533);
        node.setLongitude(110.3592);
        node.setCurrentLevel(1);
        node.setIsDead(false);
        node.setArea("Kuching");
        node.setLocation("Sungai Sarawak");
        node.setState("Sarawak");
        node.setLastUpdated(Instant.parse("2025-06-15T08:00:00Z"));
        node.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        return node;
    }

    /** Returns a warning-level {@link Node} (level 2). */
    public static Node buildWarningNode() {
        Node node = buildNode();
        node.setId(UUID.fromString("00000000-0000-0000-0000-000000000051"));
        node.setNodeId("102782479");
        node.setName("Node 102782479");
        node.setCurrentLevel(2);
        return node;
    }

    /** Returns a critical-level {@link Node} (level 3). */
    public static Node buildCriticalNode() {
        Node node = buildNode();
        node.setId(UUID.fromString("00000000-0000-0000-0000-000000000052"));
        node.setNodeId("102782480");
        node.setName("Node 102782480");
        node.setCurrentLevel(3);
        return node;
    }

    /** Returns a dead / inactive {@link Node}. */
    public static Node buildDeadNode() {
        Node node = buildNode();
        node.setId(UUID.fromString("00000000-0000-0000-0000-000000000053"));
        node.setNodeId("102782481");
        node.setIsDead(true);
        node.setCurrentLevel(0);
        return node;
    }

    /** Returns a {@link SensorNodeDto} matching {@link #buildNode()}. */
    public static SensorNodeDto buildSensorNodeDto() {
        return new SensorNodeDto(
                "00000000-0000-0000-0000-000000000050",
                "102782478",
                "Node 102782478",
                "active",
                "1.5 km",
                List.of(110.3592, 1.5533),
                "Kuching",
                "Sungai Sarawak",
                "Sarawak",
                1,
                false,
                "2025-06-15T08:00:00Z",
                "2025-01-01T00:00:00Z",
                1.5533,
                110.3592
        );
    }

    // ── Zones ─────────────────────────────────────────────────────────────────

    /** Returns a {@link Zone} entity. */
    public static Zone buildZone() {
        Zone zone = new Zone();
        zone.setId(UUID.fromString("00000000-0000-0000-0000-000000000060"));
        zone.setName("Kuching River Zone");
        zone.setRiskLevel("high");
        zone.setBoundary("{\"type\":\"Polygon\",\"coordinates\":[[[110.3,1.5],[110.4,1.5],[110.4,1.6],[110.3,1.5]]]}");
        zone.setUpdatedAt(Instant.parse("2025-06-01T00:00:00Z"));
        return zone;
    }

    /** Returns a {@link ZoneDto} matching {@link #buildZone()}. */
    public static ZoneDto buildZoneDto() {
        return new ZoneDto(
                "00000000-0000-0000-0000-000000000060",
                "Kuching River Zone",
                "high",
                "{\"type\":\"Polygon\",\"coordinates\":[[[110.3,1.5],[110.4,1.5],[110.4,1.6],[110.3,1.5]]]}",
                "2025-06-01T00:00:00Z"
        );
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    /** Returns a list of default settings (all disabled). */
    public static List<SettingsDto.SettingItemDto> buildDefaultSettings() {
        return List.of(
                new SettingsDto.SettingItemDto("emailNotifications", false),
                new SettingsDto.SettingItemDto("lowDataMode", false),
                new SettingsDto.SettingItemDto("pushNotifications", false),
                new SettingsDto.SettingItemDto("smsNotifications", false)
        );
    }

    // ── Safety ────────────────────────────────────────────────────────────────

    /** Returns a sample list of safety content items. */
    public static List<SafetyContentDto> buildSafetyContent() {
        return List.of(
                new SafetyContentDto("Before a Flood",
                        "Prepare an emergency kit with essentials...",
                        "2025-06-01T00:00:00Z"),
                new SafetyContentDto("During a Flood",
                        "Stay away from floodwaters and move to higher ground...",
                        "2025-06-01T00:00:00Z"),
                new SafetyContentDto("After a Flood",
                        "Do not return home until authorities declare it safe...",
                        "2025-06-01T00:00:00Z")
        );
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    /** Returns a sample {@link AnalyticsDataDto}. */
    public static AnalyticsDataDto buildAnalyticsData() {
        return new AnalyticsDataDto(
                List.of(
                        new AnalyticsDataDto.StatDto("Active Sensors", "42", "radio-outline", "42"),
                        new AnalyticsDataDto.StatDto("Alerts (24h)", "3", "alert-circle-outline", "3"),
                        new AnalyticsDataDto.StatDto("Data Points", "1.2k", "stats-chart-outline", "+1.2k")
                ),
                List.of(10, 15, 8, 22, 5, 18, 12),
                List.of(50, 70, 45, 90, 60),
                List.of(
                        new AnalyticsDataDto.WaterLevelDto("102782478", 2, "warning"),
                        new AnalyticsDataDto.WaterLevelDto("102782479", 1, "normal")
                ),
                List.of(
                        new AnalyticsDataDto.FloodByStateDto("Sarawak", 5),
                        new AnalyticsDataDto.FloodByStateDto("Sabah", 2)
                ),
                List.of(
                        new AnalyticsDataDto.RecentEventDto("Node 102782478 High Water (Level 2)",
                                "2 min ago", "warning")
                )
        );
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    /** Returns a sample {@link DashboardNodeRowDto}. */
    public static DashboardNodeRowDto buildDashboardRow() {
        return new DashboardNodeRowDto(
                "00000000-0000-0000-0000-000000000050",
                "1",
                "Kuching",
                "Sungai Sarawak",
                "Sarawak",
                "Normal",
                "5m ago",
                "08:00 AM"
        );
    }

    /** Returns a sample {@link DashboardTimeSeriesDto} with 12 monthly and 5 yearly values. */
    public static DashboardTimeSeriesDto buildTimeSeries() {
        return new DashboardTimeSeriesDto(
                List.of(10, 15, 8, 22, 5, 18, 12, 9, 14, 20, 6, 11),
                List.of(50, 70, 45, 90, 60)
        );
    }
}
