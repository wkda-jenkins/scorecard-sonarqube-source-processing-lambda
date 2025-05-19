package wkda.api.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.auto1.core.lambda.tracing.converter.WrappedMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.influxdb.client.InfluxDBClientOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import wkda.api.lambda.client.SonarQubeClient;
import wkda.api.lambda.model.ProcessingEventMessage;
import wkda.api.lambda.repository.SonarQubeInfluxRepository;
import wkda.api.lambda.service.SonarQubeService;

import java.time.Instant;
import java.util.List;

import static com.auto1.core.lambda.serialization.ObjectMapperConfiguration.MAPPER;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class LambdaFunctionTest {

    private SonarQubeService sonarQubeService;
    private LambdaFunction lambdaFunction;
    private SonarQubeClient sonarQubeClient;
    private SonarQubeInfluxRepository sonarQubeInfluxRepository;
    private InfluxDBClientOptions influxDBClientOptions;

    @BeforeEach
    public void setUp() {
        sonarQubeService = mock(SonarQubeService.class);
        sonarQubeClient = mock(SonarQubeClient.class);
        sonarQubeInfluxRepository = mock(SonarQubeInfluxRepository.class);
        influxDBClientOptions = mock(InfluxDBClientOptions.class);
        
        lambdaFunction = new LambdaFunction(sonarQubeService, influxDBClientOptions, sonarQubeInfluxRepository, sonarQubeClient);
    }

    @Test
    void shouldProcessAllMessagesSuccessfully() throws Exception {
        // Given
        SQSEvent event = createSQSEvent();
        Context context = mock(Context.class);

        // When
        assertDoesNotThrow(() -> lambdaFunction.handleRequest(event, context));

        // Then
        verify(sonarQubeService, times(1)).fetchMetrics(eq("test-project"));
        verify(sonarQubeService, times(1)).calculateScore(any());
        verify(sonarQubeService, times(1)).storeMetricsInInfluxDB(eq("test-project"), any(), anyInt(), any(Instant.class));
    }

    @Test
    void shouldHandleFailureGracefully() throws Exception {
        // Given
        SQSEvent event = createSQSEvent();
        Context context = mock(Context.class);

        // When
        doThrow(new RuntimeException("Failed to process SonarQube metrics")).when(sonarQubeService).fetchMetrics(any());
        var result = lambdaFunction.handleRequest(event, context);

        // Then
        assertEquals(1, result.getBatchItemFailures().size());
        assertEquals("123", result.getBatchItemFailures().getFirst().getItemIdentifier());
    }

    private SQSEvent createSQSEvent() throws JsonProcessingException {
        ProcessingEventMessage processingEventMessage = new ProcessingEventMessage();
        processingEventMessage.setSource("sonarqube");
        processingEventMessage.setProjectId("test-project");
        processingEventMessage.setTimestamp(Instant.now());

        WrappedMessage wrappedMessage = new WrappedMessage();
        wrappedMessage.setMessage(MAPPER.writeValueAsString(processingEventMessage));
        
        SQSEvent event = new SQSEvent();
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setBody(MAPPER.writeValueAsString(wrappedMessage));
        message.setMessageId("123");
        event.setRecords(List.of(message));
        
        return event;
    }
}