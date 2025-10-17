// src/main/java/com/example/demo/repository/CostSnapshotRepository.java
package com.example.demo.repository;

import com.example.demo.model.Cost.CostSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CostSnapshotRepository extends JpaRepository<CostSnapshot, String> {
    
    /**
     * Find all snapshots for a namespace ordered by timestamp descending
     */
    List<CostSnapshot> findByNamespaceOrderByTimestampDesc(String namespace);
    
    /**
     * Find snapshots for a namespace within a time range
     */
    List<CostSnapshot> findByNamespaceAndTimestampBetweenOrderByTimestampAsc(
        String namespace, 
        LocalDateTime startTime, 
        LocalDateTime endTime
    );
    
    /**
     * Find the most recent snapshot for a namespace
     */
    Optional<CostSnapshot> findFirstByNamespaceOrderByTimestampDesc(String namespace);
    
    /**
     * Find all snapshots within a time range (for cluster-wide analysis)
     */
    List<CostSnapshot> findByTimestampBetweenOrderByTimestampAsc(
        LocalDateTime startTime, 
        LocalDateTime endTime
    );
    
    /**
     * Get snapshots for the last N days for a namespace
     */
    @Query("SELECT cs FROM CostSnapshot cs WHERE cs.namespace = :namespace " +
           "AND cs.timestamp >= :since ORDER BY cs.timestamp ASC")
    List<CostSnapshot> findRecentByNamespace(
        @Param("namespace") String namespace, 
        @Param("since") LocalDateTime since
    );
    
    /**
     * Get the oldest snapshot for a namespace (baseline for savings calculation)
     */
    Optional<CostSnapshot> findFirstByNamespaceOrderByTimestampAsc(String namespace);
    
    /**
     * Count snapshots for a namespace
     */
    long countByNamespace(String namespace);
    
    /**
     * Delete old snapshots (for cleanup)
     */
    void deleteByTimestampBefore(LocalDateTime cutoffTime);
}