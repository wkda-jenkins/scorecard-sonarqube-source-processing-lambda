package wkda.api.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.auto1.core.lambda.tracing.WithLambdaTracing;
import com.auto1.core.lambda.tracing.converter.WrappedMessage;
import com.influxdb.client.InfluxDBClientOptions;
import lombok.extern.log4j.Log4j2;
import wkda.api.lambda.client.SonarQubeClient;
import wkda.api.lambda.model.ProcessingEventMessage;
import wkda.api.lambda.repository.SonarQubeInfluxRepository;
import wkda.api.lambda.service.SonarQubeService;

import java.util.ArrayList;
import java.util.List;

import static com.auto1.core.lambda.serialization.ObjectMapperConfiguration.MAPPER;

// Initialize the Lambda function
class Initializer {
    static {
        // Load the LambdaInitializer to perform initialization
        try {
            Class.forName(LambdaInitializer.class.getName());
        } catch (ClassNotFoundException e) {
            // This should never happen as the class is in the same package
        }
    }
    
    private static final Initializer INSTANCE = new Initializer();
    
    private Initializer() {
        // Private constructor to prevent instantiation
    }
    
    static void ensureInitialized() {
        // This method is called to ensure the static initializer is executed
    }
}

/**
 * AWS Lambda function that processes SonarQube metrics from SQS messages
 * and stores them in InfluxDB for the scorecard system.
 */
@Log4j2
public class LambdaFunction implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final LambdaHandler lambdaHandler;

    /**
     * Default constructor used by AWS Lambda runtime.
     * Initializes all required services with default configurations.
     */
    public LambdaFunction() {
        // Ensure initialization is performed
        Initializer.ensureInitialized();
        
        SonarQubeClient sonarQubeClient = new SonarQubeClient();
        InfluxDBClientOptions influxDBClientOptions = SonarQubeInfluxRepository.createInfluxDBClientOptions();
        SonarQubeInfluxRepository sonarQubeInfluxRepository = new SonarQubeInfluxRepository(influxDBClientOptions);
        SonarQubeService sonarQubeService = new SonarQubeService(sonarQubeClient, sonarQubeInfluxRepository);
        this.lambdaHandler = new LambdaHandler(sonarQubeService);
    }

    /**
     * Constructor with dependency injection for testing.
     */
    public LambdaFunction(SonarQubeService sonarQubeService, 
                          InfluxDBClientOptions influxDBClientOptions,
                          SonarQubeInfluxRepository sonarQubeInfluxRepository,
                          SonarQubeClient sonarQubeClient) {
        this.lambdaHandler = new LambdaHandler(sonarQubeService);
    }

    /**
     * Handles SQS batch events, processing each message and returning failures for retry.
     * Implements partial batch processing as per AWS Lambda best practices.
     *
     * @param request SQS event containing messages to process
     * @param context Lambda execution context
     * @return SQSBatchResponse with any failed message IDs for retry
     */
    @Override
    public SQSBatchResponse handleRequest(SQSEvent request, Context context) {
        List<SQSBatchResponse.BatchItemFailure> batchItemFailures = new ArrayList<>();
        for (SQSEvent.SQSMessage message : request.getRecords()) {
            String messageId = message.getMessageId();
            log.info("Processing message: {}", messageId);
            try {
                handleMessage(message);
                log.info("Message {} processed successfully", messageId);
            } catch (Exception e) {
                log.error("Error processing SonarQube metrics for message id " + messageId + ", will retry", e);
                batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(messageId));
            }
        }
        return new SQSBatchResponse(batchItemFailures);
    }

    /**
     * Processes a single SQS message, extracting the ProcessingEventMessage and
     * forwarding it to the appropriate service.
     * 
     * @param message SQS message to process
     * @throws Exception if processing fails
     */
    @WithLambdaTracing
    public void handleMessage(SQSEvent.SQSMessage message) throws Exception {
        String body = message.getBody();
        WrappedMessage wrappedMessage = MAPPER.readValue(body, WrappedMessage.class);
        ProcessingEventMessage processingEventMessage = MAPPER.readValue(wrappedMessage.getMessage(), ProcessingEventMessage.class);
        
        log.debug("Processing SonarQube metrics for project: {}", processingEventMessage.getProjectId());
        lambdaHandler.processSonarQubeMetrics(processingEventMessage);
    }
}