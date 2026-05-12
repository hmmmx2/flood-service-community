package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.Blog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BlogRepository extends JpaRepository<Blog, UUID> {

    List<Blog> findByIsFeaturedTrueOrderByCreatedAtDesc();

    Page<Blog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Case-insensitive, whitespace-tolerant category filter.
     *
     * <p>Switched from JPQL to a native query because the JPQL form
     * ({@code WHERE LOWER(TRIM(b.category)) = LOWER(TRIM(:category))})
     * was silently returning only 1 of N matching rows on production
     * Postgres — the same pathology that earlier hit the community
     * comments listing. Native SQL bypasses the JPQL-to-SQL translator
     * and goes straight to the column, which both repros locally and
     * fixes the filter.</p>
     */
    @Query(
        value = "SELECT * FROM blogs WHERE LOWER(TRIM(category)) = LOWER(TRIM(:category)) ORDER BY created_at DESC",
        countQuery = "SELECT COUNT(*) FROM blogs WHERE LOWER(TRIM(category)) = LOWER(TRIM(:category))",
        nativeQuery = true
    )
    Page<Blog> findByCategoryNormalized(@Param("category") String category, Pageable pageable);

    @Query(value = "SELECT DISTINCT TRIM(category) FROM blogs WHERE category IS NOT NULL AND TRIM(category) <> '' ORDER BY 1",
            nativeQuery = true)
    List<String> findDistinctCategoriesTrimmed();
}
