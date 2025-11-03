package com.example.demo.service.Cost;

import com.example.demo.model.Cost.CostForecast;
import com.example.demo.model.Cost.CostSnapshot;
import com.example.demo.repository.CostSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 📈 COST PREDICTION SERVICE
 * Uses simple linear regression on historical snapshots to predict future costs
 */
@Service
public class CostPredictionService {
    private static final Logger logger = LoggerFactory.getLogger(CostPredictionService.class);

    @Autowired
    private CostSnapshotRepository snapshotRepository;

    /**
     * Predict future costs for a namespace
     */
    public CostForecast predictCost(String namespace, int daysAhead) {
        logger.info("🔮 Predicting costs for namespace '{}' ({} days ahead)", namespace, daysAhead);

        List<CostSnapshot> history = snapshotRepository
            .findByNamespaceOrderByTimestampDesc(namespace);

        if (history.isEmpty()) {
            return createNoDataForecast(namespace);
        }

        if (history.size() < 2) {
            return createInsufficientDataForecast(namespace, history.get(0));
        }

        // Prepare data for linear regression
        double[][] data = prepareTimeSeriesData(history);
        double[] timestamps = data[0];
        double[] costs = data[1];

        // Calculate linear regression (y = mx + b)
        double[] regression = calculateLinearRegression(timestamps, costs);
        double slope = regression[0]; // m
        double intercept = regression[1]; // b

        // Current cost (most recent snapshot)
        double currentCost = history.get(0).getTotalMonthlyCost();

        // Predict future cost
        double lastTimestamp = timestamps[timestamps.length - 1];
        double futureTimestamp = lastTimestamp + daysAhead;
        double predictedCost = slope * futureTimestamp + intercept;

        // Ensure prediction is not negative
        predictedCost = Math.max(0, predictedCost);

        // Calculate confidence based on data quality
        double confidence = calculateConfidence(history, slope, intercept, timestamps, costs);

        // Determine trend
        String trend = determineTrend(slope, currentCost);

        // Calculate risk level
        String riskLevel = calculateRiskLevel(currentCost, predictedCost, confidence);

        // Build forecast
        CostForecast forecast = new CostForecast(namespace, currentCost, predictedCost, daysAhead);
        forecast.setPredictedWeeklyCost(predictedCost / 4.0);
        forecast.setTrend(trend);
        forecast.setConfidence(confidence);
        forecast.setSnapshotsUsed(history.size());
        forecast.setRiskLevel(riskLevel);
        forecast.setMessage(generateForecastMessage(trend, currentCost, predictedCost, daysAhead));

        logger.info("✅ Forecast: ${} → ${} in {} days ({}% confidence, {} trend)",
            String.format("%.2f", currentCost),
            String.format("%.2f", predictedCost),
            daysAhead,
            String.format("%.0f", confidence),
            trend);

        return forecast;
    }

    /**
     * Prepare time series data for regression
     * Returns [timestamps[], costs[]]
     */
    private double[][] prepareTimeSeriesData(List<CostSnapshot> history) {
        int n = history.size();
        double[] timestamps = new double[n];
        double[] costs = new double[n];

        // Use oldest snapshot as time zero
        LocalDateTime baseline = history.get(n - 1).getTimestamp();

        for (int i = 0; i < n; i++) {
            CostSnapshot snapshot = history.get(n - 1 - i); // Reverse order (oldest first)
            timestamps[i] = ChronoUnit.DAYS.between(baseline, snapshot.getTimestamp());
            costs[i] = snapshot.getTotalMonthlyCost();
        }

        return new double[][] { timestamps, costs };
    }

    /**
     * Calculate linear regression: y = mx + b
     * Returns [slope, intercept]
     */
    private double[] calculateLinearRegression(double[] x, double[] y) {
        int n = x.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;

        return new double[] { slope, intercept };
    }

    /**
     * Calculate R² (coefficient of determination) for confidence
     */
    private double calculateConfidence(List<CostSnapshot> history, double slope, 
                                      double intercept, double[] x, double[] y) {
        int n = y.length;
        
        // Calculate mean of y
        double meanY = 0;
        for (double val : y) meanY += val;
        meanY /= n;

        // Calculate total sum of squares and residual sum of squares
        double ssTot = 0, ssRes = 0;
        for (int i = 0; i < n; i++) {
            double predicted = slope * x[i] + intercept;
            ssTot += Math.pow(y[i] - meanY, 2);
            ssRes += Math.pow(y[i] - predicted, 2);
        }

        // R² = 1 - (SS_res / SS_tot)
        double r2 = ssTot > 0 ? 1 - (ssRes / ssTot) : 0;
        r2 = Math.max(0, Math.min(1, r2)); // Clamp between 0-1

        // Convert to percentage and adjust based on data points
        double baseConfidence = r2 * 100;
        
        // Penalty for insufficient data
        if (history.size() < 5) {
            baseConfidence *= 0.7;
        } else if (history.size() < 10) {
            baseConfidence *= 0.85;
        }

        // Bonus for recency (if last snapshot is within 48 hours)
        LocalDateTime lastSnapshot = history.get(0).getTimestamp();
        long hoursSinceLastSnapshot = ChronoUnit.HOURS.between(lastSnapshot, LocalDateTime.now());
        if (hoursSinceLastSnapshot < 48) {
            baseConfidence = Math.min(100, baseConfidence * 1.1);
        }

        return Math.round(baseConfidence);
    }

    /**
     * Determine trend based on slope
     */
    private String determineTrend(double slope, double currentCost) {
        double dailyChange = slope;
        double monthlyChange = slope * 30;
        double percentChange = currentCost > 0 ? (monthlyChange / currentCost) * 100 : 0;

        if (percentChange > 10) {
            return "increasing";
        } else if (percentChange < -10) {
            return "decreasing";
        } else {
            return "stable";
        }
    }

    /**
     * Calculate risk level
     */
    private String calculateRiskLevel(double current, double predicted, double confidence) {
        double changePercent = current > 0 ? ((predicted - current) / current) * 100 : 0;

        if (changePercent > 50 && confidence > 70) {
            return "HIGH";
        } else if (changePercent > 30 || (changePercent > 20 && confidence > 80)) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * Generate human-readable forecast message
     */
    private String generateForecastMessage(String trend, double current, 
                                          double predicted, int daysAhead) {
        double change = predicted - current;
        double changePercent = current > 0 ? (change / current) * 100 : 0;

        if (trend.equals("stable")) {
            return String.format("Costs expected to remain stable around $%.2f/month", predicted);
        } else if (trend.equals("increasing")) {
            return String.format("Costs projected to increase by $%.2f (%.1f%%) over next %d days",
                Math.abs(change), Math.abs(changePercent), daysAhead);
        } else {
            return String.format("Costs projected to decrease by $%.2f (%.1f%%) over next %d days",
                Math.abs(change), Math.abs(changePercent), daysAhead);
        }
    }

    /**
     * Fallback for no historical data
     */
    private CostForecast createNoDataForecast(String namespace) {
        CostForecast forecast = new CostForecast();
        forecast.setNamespace(namespace);
        forecast.setTrend("unknown");
        forecast.setConfidence(0);
        forecast.setMessage("No historical data available. Need at least 2 snapshots to predict.");
        forecast.setSnapshotsUsed(0);
        return forecast;
    }

    /**
     * Fallback for insufficient data
     */
    private CostForecast createInsufficientDataForecast(String namespace, CostSnapshot current) {
        CostForecast forecast = new CostForecast();
        forecast.setNamespace(namespace);
        forecast.setCurrentMonthlyCost(current.getTotalMonthlyCost());
        forecast.setPredictedMonthlyCost(current.getTotalMonthlyCost());
        forecast.setTrend("unknown");
        forecast.setConfidence(0);
        forecast.setMessage("Only 1 snapshot available. Need at least 2 data points to predict trends.");
        forecast.setSnapshotsUsed(1);
        return forecast;
    }
}