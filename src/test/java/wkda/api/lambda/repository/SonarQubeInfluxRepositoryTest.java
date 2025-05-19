package wkda.api.lambda.repository;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.InfluxDBClientOptions;
import com.influxdb.client.WriteApi;
import com.influxdb.client.write.Point;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import wkda.api.lambda.model.SonarQubeMetrics;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class SonarQubeInfluxRepositoryTest {

    private InfluxDBClientOptions influxDBClientOptions;
    private InfluxDBClient influxDBClient;
    private WriteApi writeApi;
    private SonarQubeInfluxRepository repository;

    @BeforeEach
    void setUp() {
        influxDBClientOptions = mock(InfluxDBClientOptions.class);
        influxDBClient = mock(InfluxDBClient.class);
        writeApi = mock(WriteApi.class);
        
        // Mock static method
        try (var factory = mockStatic(InfluxDBClientFactory.class)) {
            factory.when(() -> InfluxDBClientFactory.create(any(InfluxDBClientOptions.class)))
                    .thenReturn(influxDBClient);
            
            repository = new SonarQubeInfluxRepository(influxDBClientOptions);
        }
        
        when(influxDBClient.getWriteApi()).thenReturn(writeApi);
    }

    @Test
    void shouldStoreMetricsAsPoints() {
        // Given
        String projectId = "test-project";
        SonarQubeMetrics metrics = createSampleMetrics(projectId, "PASSED");
        int score = 100;
        Instant timestamp = Instant.now();
        
        // When
        repository.storeMetrics(projectId, metrics, score, timestamp);
        
        // Then
        ArgumentCaptor<List<Point>> pointsCaptor = ArgumentCaptor.forClass(List.class);
        verify(writeApi).writePoints(anyString(), pointsCaptor.capture());
        
        List<Point> points = pointsCaptor.getValue();
        
        // Verify we have points for main metrics, measures, and issues
        assertTrue(points.size() > 1);
        
        // Verify main point has correct tags and fields
        Point mainPoint = points.get(0);
        assertEquals("sonarqube_metrics", mainPoint.getMeasurement());
        
        // Verify we have points for issues by severity
        boolean hasIssuesBySeverity = points.stream()
                .anyMatch(p -> p.getMeasurement().equals("sonarqube_issues_severity"));
        assertTrue(hasIssuesBySeverity);
        
        // Verify we have points for issues by type
        boolean hasIssuesByType = points.stream()
                .anyMatch(p -> p.getMeasurement().equals("sonarqube_issues_type"));
        assertTrue(hasIssuesByType);
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