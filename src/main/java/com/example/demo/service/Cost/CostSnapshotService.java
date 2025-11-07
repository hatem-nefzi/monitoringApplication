// src/main/java/com/example/demo/service/Cost/CostSnapshotService.java
package com.example.demo.service.Cost;

import com.example.demo.model.Cost.CostAnalysis;
import com.example.demo.model.Cost.CostSnapshot;
import com.example.demo.model.Cost.ResourceCost;
import com.example.demo.repository.CostSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * SNAPSHOT CREATION SERVICE
 * 
 * Responsible ONLY for creating and saving snapshots.
 * This breaks the circular dependency by separating snapshot logic
 * from analysis logic.
 */
@Service
public class CostSnapshotService {
    private static final Logger logger = LoggerFactory.getLogger(CostSnapshotService.class);
    
    @Autowired
    private CostSnapshotRepository snapshotRepository;
    
    /**
     * Create and save a cost snapshot from an analysis
     * 
     * @param namespace The namespace being analyzed
     * @param analysis The cost analysis result
     * @return The saved snapshot
     */
    @Transactional
    public CostSnapshot createSnapshot(String namespace, CostAnalysis analysis) {
        logger.info("📸 Creating cost snapshot for namespace: {}", namespace);
        
        try {
            // Create snapshot
            CostSnapshot snapshot = new CostSnapshot(namespace);
            snapshot.setTotalMonthlyCost(analysis.getMonthlyCost());
            snapshot.setTotalHourlyCost(analysis.getHourlyCost());
            snapshot.setTotalCpuCores(analysis.getTotalCpuCores());
            snapshot.setTotalMemoryGb(analysis.getTotalMemoryGb());
            snapshot.setTotalPods(analysis.getTotalPods());
            snapshot.setEfficiencyScore(analysis.getEfficiencyScore());
            
            // Store individual pod costs (create detached copies)
            List<ResourceCost> podCosts = new ArrayList<>();
            for (ResourceCost originalCost : analysis.getPodCosts()) {
                ResourceCost podCost = new ResourceCost();
                podCost.setPodName(originalCost.getPodName());
                podCost.setDeploymentName(originalCost.getDeploymentName());
                podCost.setCpuRequest(originalCost.getCpuRequest());
                podCost.setCpuUsage(originalCost.getCpuUsage());
                podCost.setMemoryRequest(originalCost.getMemoryRequest());
                podCost.setMemoryUsage(originalCost.getMemoryUsage());
                podCost.setHourlyCost(originalCost.getHourlyCost());
                podCost.setMonthlyCost(originalCost.getMonthlyCost());
                podCost.setWastedCost(originalCost.getWastedCost());
                podCost.setStatus(originalCost.getStatus());
                podCosts.add(podCost);
            }
            snapshot.setPodCosts(podCosts);
            
            // Save to database
            CostSnapshot saved = snapshotRepository.save(snapshot);
            
            logger.info("✅ Snapshot saved: {} (${}/month, {} pods)", 
                saved.getId(), 
                String.format("%.2f", saved.getTotalMonthlyCost()),
                saved.getTotalPods());
            
            return saved;
        } catch (Exception e) {
            logger.error("❌ Failed to create snapshot for {}: {}", namespace, e.getMessage(), e);
            throw new RuntimeException("Failed to create cost snapshot: " + e.getMessage(), e);
        }
    }
}