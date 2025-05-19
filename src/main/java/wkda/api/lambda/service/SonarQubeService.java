package wkda.api.lambda.service;

import com.auto1.core.lambda.tracing.WithLambdaTracing;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import wkda.api.lambda.client.SonarQubeClient;
import wkda.api.lambda.model.SonarQubeMetrics;
import wkda.api.lambda.repository.SonarQubeInfluxRepository;

import java.io.IOException;
import java.time.Instant;

/**
 * Service for processing SonarQube metrics.
 */
@Log4j2
@RequiredArgsConstructor
public class SonarQubeService {

    private final SonarQubeClient sonarQubeClient;
    private final SonarQubeInfluxRepository sonarQubeInfluxRepository;
    
    /**
     * Fetches metrics for a project from SonarQube.
     * 
     * @param projectId The ID of the project in SonarQube
     * @return The metrics for the project
     * @throws IOException if the API call fails
     */
    @WithLambdaTracing
    public SonarQubeMetrics fetchMetrics(String projectId) throws IOException {
        log.info("Fetching metrics for project: {}", projectId);
        return sonarQubeClient.fetchMetrics(projectId);
    }
    
    /**
     * Calculates a score based on the SonarQube quality gate status.
     * Uses Pass0rFailScoringStrategy: PASSED = 100, FAILED = 0.
     * 
     * @param metrics The SonarQube metrics
     * @return The calculated score
     */
    @WithLambdaTracing
    public int calculateScore(SonarQubeMetrics metrics) {
        log.info("Calculating score for project: {} with quality gate status: {}", 
                metrics.getProjectId(), metrics.getQualityGateStatus());
        
        // Pass0rFailScoringStrategy: PASSED = 100, FAILED = 0
        if ("PASSED".equalsIgnoreCase(metrics.getQualityGateStatus())) {
            return 100;
        } else {
            return 0;
        }
    }
    
    /**
     * Stores metrics and score in InfluxDB.
     * 
     * @param projectId The ID of the project
     * @param metrics The SonarQube metrics
     * @param score The calculated score
     * @param timestamp The timestamp for the metrics
     */
    @WithLambdaTracing
    public void storeMetricsInInfluxDB(String projectId, SonarQubeMetrics metrics, int score, Instant timestamp) {
        log.info("Storing metrics for project: {} with score: {}", projectId, score);
        sonarQubeInfluxRepository.storeMetrics(projectId, metrics, score, timestamp);
    }
}