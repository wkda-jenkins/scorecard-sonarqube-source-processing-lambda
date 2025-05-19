package wkda.api.lambda.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents the message received from SQS that triggers the processing of SonarQube metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProcessingEventMessage {
    
    /**
     * The source of the message, should be "sonarqube" for this lambda to process it.
     */
    private String source;
    
    /**
     * The ID of the project in SonarQube whose metrics should be processed.
     */
    private String projectId;
    
    /**
     * The timestamp when the message was generated.
     */
    private Instant timestamp;
}