package com.example.demo.model.Cost;

import java.time.LocalDateTime;

/**
 * 📈 Cost Forecast Model
 * Represents predicted future costs based on historical trends
 */
public class CostForecast {
    private String namespace;
    private double currentMonthlyCost;
    private double predictedMonthlyCost;
    private double predictedWeeklyCost;
    private int daysAhead;
    private String trend; // "increasing", "decreasing", "stable"
    private double confidence; // 0-100
    private double changePercent;
    private LocalDateTime forecastDate;
    private int snapshotsUsed;
    private String riskLevel; // "LOW", "MEDIUM", "HIGH"
    private String message;

    // Constructors
    public CostForecast() {
        this.forecastDate = LocalDateTime.now();
    }

    public CostForecast(String namespace, double currentCost, double predictedCost, int daysAhead) {
        this.namespace = namespace;
        this.currentMonthlyCost = currentCost;
        this.predictedMonthlyCost = predictedCost;
        this.daysAhead = daysAhead;
        this.forecastDate = LocalDateTime.now();
        this.changePercent = currentCost > 0 ? ((predictedCost - currentCost) / currentCost) * 100 : 0;
    }

    // Getters and Setters
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public double getCurrentMonthlyCost() {
        return currentMonthlyCost;
    }

    public void setCurrentMonthlyCost(double currentMonthlyCost) {
        this.currentMonthlyCost = currentMonthlyCost;
    }

    public double getPredictedMonthlyCost() {
        return predictedMonthlyCost;
    }

    public void setPredictedMonthlyCost(double predictedMonthlyCost) {
        this.predictedMonthlyCost = predictedMonthlyCost;
    }

    public double getPredictedWeeklyCost() {
        return predictedWeeklyCost;
    }

    public void setPredictedWeeklyCost(double predictedWeeklyCost) {
        this.predictedWeeklyCost = predictedWeeklyCost;
    }

    public int getDaysAhead() {
        return daysAhead;
    }

    public void setDaysAhead(int daysAhead) {
        this.daysAhead = daysAhead;
    }

    public String getTrend() {
        return trend;
    }

    public void setTrend(String trend) {
        this.trend = trend;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public double getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(double changePercent) {
        this.changePercent = changePercent;
    }

    public LocalDateTime getForecastDate() {
        return forecastDate;
    }

    public void setForecastDate(LocalDateTime forecastDate) {
        this.forecastDate = forecastDate;
    }

    public int getSnapshotsUsed() {
        return snapshotsUsed;
    }

    public void setSnapshotsUsed(int snapshotsUsed) {
        this.snapshotsUsed = snapshotsUsed;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}