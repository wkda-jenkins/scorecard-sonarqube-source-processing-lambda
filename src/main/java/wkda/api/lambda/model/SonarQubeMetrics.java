package wkda.api.lambda.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents the metrics fetched from SonarQube for a project.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SonarQubeMetrics {
    
    /**
     * The ID of the project in SonarQube.
     */
    private String projectId;
    
    /**
     * The name of the project in SonarQube.
     */
    private String projectName;
    
    /**
     * The status of the quality gate (e.g., "PASSED", "FAILED").
     */
    private String qualityGateStatus;
    
    /**
     * A map of metric keys to their values.
     */
    private Map<String, Object> measures;
    
    /**
     * The number of issues by severity.
     */
    private Map<String, Integer> issuesBySeverity;
    
    /**
     * The number of issues by type.
     */
    private Map<String, Integer> issuesByType;
}