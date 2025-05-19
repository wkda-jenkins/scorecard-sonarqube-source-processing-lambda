package wkda.api.lambda.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import wkda.api.lambda.client.SonarQubeClient;
import wkda.api.lambda.model.SonarQubeMetrics;
import wkda.api.lambda.repository.SonarQubeInfluxRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class SonarQubeServiceTest {

    private SonarQubeClient sonarQubeClient;
    private SonarQubeInfluxRepository sonarQubeInfluxRepository;
    private SonarQubeService sonarQubeService;

    @BeforeEach
    void setUp() {
        sonarQubeClient = mock(SonarQubeClient.class);
        sonarQubeInfluxRepository = mock(SonarQubeInfluxRepository.class);
        sonarQubeService = new SonarQubeService(sonarQubeClient, sonarQubeInfluxRepository);
    }

    @Test
    void shouldFetchMetricsFromClient() throws IOException {
        // Given
        String projectId = "test-project";
        SonarQubeMetrics expectedMetrics = createSampleMetrics(projectId, "PASSED");
        when(sonarQubeClient.fetchMetrics(projectId)).thenReturn(expectedMetrics);

        // When
        SonarQubeMetrics actualMetrics = sonarQubeService.fetchMetrics(projectId);

        // Then
        assertEquals(expectedMetrics, actualMetrics);
        verify(sonarQubeClient, times(1)).fetchMetrics(projectId);
    }

    @Test
    void shouldCalculateScoreOf100ForPassedQualityGate() {
        // Given
        SonarQubeMetrics metrics = createSampleMetrics("test-project", "PASSED");

        // When
        int score = sonarQubeService.calculateScore(metrics);

        // Then
        assertEquals(100, score);
    }

    @Test
    void shouldCalculateScoreOf0ForFailedQualityGate() {
        // Given
        SonarQubeMetrics metrics = createSampleMetrics("test-project", "FAILED");

        // When
        int score = sonarQubeService.calculateScore(metrics);

        // Then
        assertEquals(0, score);
    }

    @Test
    void shouldStoreMetricsInInfluxDB() {
        // Given
        String projectId = "test-project";
        SonarQubeMetrics metrics = createSampleMetrics(projectId, "PASSED");
        int score = 100;
        Instant timestamp = Instant.now();

        // When
        sonarQubeService.storeMetricsInInfluxDB(projectId, metrics, score, timestamp);

        // Then
        verify(sonarQubeInfluxRepository, times(1)).storeMetrics(projectId, metrics, score, timestamp);
    }

    private SonarQubeMetrics createSampleMetrics(String projectId, String qualityGateStatus) {
        Map<String, Object> measures = new HashMap<>();
        measures.put("ncloc", 1000);
        measures.put("coverage", 80.5);
        measures.put("duplicated_lines_density", 5.2);
        measures.put("bugs", 10);
        measures.put("vulnerabilities", 5);
        measures.put("code_smells", 20);
        measures.put("security_hotspots", 3);

        Map<String, Integer> issuesBySeverity = new HashMap<>();
        issuesBySeverity.put("BLOCKER", 1);
        issuesBySeverity.put("CRITICAL", 4);
        issuesBySeverity.put("MAJOR", 15);
        issuesBySeverity.put("MINOR", 10);
        issuesBySeverity.put("INFO", 5);

        Map<String, Integer> issuesByType = new HashMap<>();
        issuesByType.put("BUG", 10);
        issuesByType.put("VULNERABILITY", 5);
        issuesByType.put("CODE_SMELL", 20);
        issuesByType.put("SECURITY_HOTSPOT", 3);

        return SonarQubeMetrics.builder()
                .projectId(projectId)
                .projectName("Test Project")
                .qualityGateStatus(qualityGateStatus)
                .measures(measures)
                .issuesBySeverity(issuesBySeverity)
                .issuesByType(issuesByType)
                .build();
    }
}