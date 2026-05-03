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

    @Query("SELECT b FROM Blog b WHERE LOWER(TRIM(b.category)) = LOWER(TRIM(:category)) ORDER BY b.createdAt DESC")
    Page<Blog> findByCategoryNormalized(@Param("category") String category, Pageable pageable);

    @Query(value = "SELECT DISTINCT TRIM(category) FROM blogs WHERE category IS NOT NULL AND TRIM(category) <> '' ORDER BY 1",
            nativeQuery = true)
    List<String> findDistinctCategoriesTrimmed();
}
