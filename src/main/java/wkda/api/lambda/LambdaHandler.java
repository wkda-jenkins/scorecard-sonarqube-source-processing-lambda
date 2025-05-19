package wkda.api.lambda;

import com.auto1.core.lambda.tracing.WithLambdaTracing;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import wkda.api.lambda.model.ProcessingEventMessage;
import wkda.api.lambda.model.SonarQubeMetrics;
import wkda.api.lambda.service.SonarQubeService;

/**
 * Handler class that coordinates the processing of SonarQube metrics.
 * Separates the Lambda-specific code from the business logic.
 */
@Log4j2
@RequiredArgsConstructor
public class LambdaHandler {

    private final SonarQubeService sonarQubeService;

    /**
     * Processes SonarQube metrics for a project and stores them in InfluxDB.
     * 
     * @param message The processing event message containing project information
     * @throws Exception if processing fails
     */
    @WithLambdaTracing
    public void processSonarQubeMetrics(ProcessingEventMessage message) throws Exception {
        log.info("Processing SonarQube metrics for project: {}", message.getProjectId());
        
        try {
            // Fetch metrics from SonarQube
            SonarQubeMetrics metrics = sonarQubeService.fetchMetrics(message.getProjectId());
            
            // Calculate score based on quality gate status
            int score = sonarQubeService.calculateScore(metrics);
            
            // Store metrics and score in InfluxDB
            sonarQubeService.storeMetricsInInfluxDB(message.getProjectId(), metrics, score, message.getTimestamp());
            
            log.info("Successfully processed SonarQube metrics for project: {}", message.getProjectId());
        } catch (Exception e) {
            log.error("Failed to process SonarQube metrics for project: {}", message.getProjectId(), e);
            throw e;
        }
    }
}