package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.Blog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BlogRepository extends JpaRepository<Blog, UUID> {

    List<Blog> findByIsFeaturedTrueOrderByCreatedAtDesc();

    Page<Blog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Blog> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);
}
