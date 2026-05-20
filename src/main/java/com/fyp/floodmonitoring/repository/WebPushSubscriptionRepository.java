package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.WebPushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, UUID> {

    List<WebPushSubscription> findByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM WebPushSubscription w WHERE w.endpoint = :endpoint AND w.userId = :userId")
    int deleteByEndpointAndUserId(@Param("endpoint") String endpoint, @Param("userId") UUID userId);

    /**
     * Idempotent delete by endpoint alone — used by the internal push
     * dispatcher when an endpoint returns 410 Gone (expired). The
     * subscription is fundamentally tied to the endpoint so dropping
     * by endpoint is safe regardless of which user originally owned it.
     */
    @Modifying
    @Query("DELETE FROM WebPushSubscription w WHERE w.endpoint = :endpoint")
    int deleteByEndpoint(@Param("endpoint") String endpoint);

    boolean existsByEndpoint(String endpoint);
}
