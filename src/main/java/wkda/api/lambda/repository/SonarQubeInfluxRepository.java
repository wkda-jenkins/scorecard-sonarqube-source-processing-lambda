package wkda.api.lambda.repository;

import com.auto1.core.lambda.secret.SecretManager;
import com.auto1.core.lambda.tracing.WithLambdaTracing;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.InfluxDBClientOptions;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import wkda.api.lambda.model.SonarQubeMetrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Repository for storing SonarQube metrics in InfluxDB.
 */
@Log4j2
public class SonarQubeInfluxRepository {

    private static final String INFLUXDB_URL_ENV_VAR = "INFLUXDB_URL";
    private static final String INFLUXDB_ORG_ENV_VAR = "INFLUXDB_ORG";
    private static final String INFLUXDB_BUCKET_ENV_VAR = "INFLUXDB_BUCKET";
    private static final String INFLUXDB_TOKEN_SECRET_NAME = "influxdb/api-token";
    private static final int CONNECTION_TIMEOUT_SECONDS = 10;
    private static final int READ_TIMEOUT_SECONDS = 30;
    private static final int WRITE_TIMEOUT_SECONDS = 10;
    
    private final InfluxDBClient influxDBClient;
    private final String bucket;
    
    /**
     * Creates a new SonarQubeInfluxRepository with the provided InfluxDB client options.
     * 
     * @param options The InfluxDB client options
     */
    public SonarQubeInfluxRepository(InfluxDBClientOptions options) {
        this.influxDBClient = InfluxDBClientFactory.create(options);
        this.bucket = System.getenv(INFLUXDB_BUCKET_ENV_VAR);
    }
    
    /**
     * Creates InfluxDB client options with authentication and timeouts.
     * 
     * @return The InfluxDB client options
     */
    public static InfluxDBClientOptions createInfluxDBClientOptions() {
        String influxDbUrl = System.getenv(INFLUXDB_URL_ENV_VAR);
        String influxDbOrg = System.getenv(INFLUXDB_ORG_ENV_VAR);
        String influxDbToken = fetchInfluxDBToken();
        
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
                .connectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        return InfluxDBClientOptions.builder()
                .url(influxDbUrl)
                .authenticateToken(influxDbToken.toCharArray())
                .org(influxDbOrg)
                .okHttpClient(httpClientBuilder)
                .build();
    }
    
    /**
     * Stores SonarQube metrics in InfluxDB.
     * 
     * @param projectId The ID of the project
     * @param metrics The SonarQube metrics
     * @param score The calculated score
     * @param timestamp The timestamp for the metrics
     */
    @WithLambdaTracing
    public void storeMetrics(String projectId, SonarQubeMetrics metrics, int score, Instant timestamp) {
        log.info("Storing metrics for project: {} with score: {}", projectId, score);
        
        List<Point> points = new ArrayList<>();
        
        // Create main point with score and quality gate status
        Point mainPoint = Point.measurement("sonarqube_metrics")
                .addTag("project_id", projectId)
                .addTag("project_name", metrics.getProjectName())
                .addField("score", score)
                .addField("quality_gate_status", metrics.getQualityGateStatus())
                .time(timestamp, WritePrecision.NS);
        
        points.add(mainPoint);
        
        // Add measures as separate points
        for (Map.Entry<String, Object> measure : metrics.getMeasures().entrySet()) {
            Point measurePoint = Point.measurement("sonarqube_measure")
                    .addTag("project_id", projectId)
                    .addTag("metric", measure.getKey());
            
            Object value = measure.getValue();
            if (value instanceof Integer) {
                measurePoint.addField("value", (Integer) value);
            } else if (value instanceof Double) {
                measurePoint.addField("value", (Double) value);
            } else {
                measurePoint.addField("value_string", value.toString());
            }
            
            measurePoint.time(timestamp, WritePrecision.NS);
            points.add(measurePoint);
        }
        
        // Add issues by severity as separate points
        for (Map.Entry<String, Integer> entry : metrics.getIssuesBySeverity().entrySet()) {
            Point issuePoint = Point.measurement("sonarqube_issues_severity")
                    .addTag("project_id", projectId)
                    .addTag("severity", entry.getKey())
                    .addField("count", entry.getValue())
                    .time(timestamp, WritePrecision.NS);
            
            points.add(issuePoint);
        }
        
        // Add issues by type as separate points
        for (Map.Entry<String, Integer> entry : metrics.getIssuesByType().entrySet()) {
            Point issuePoint = Point.measurement("sonarqube_issues_type")
                    .addTag("project_id", projectId)
                    .addTag("type", entry.getKey())
                    .addField("count", entry.getValue())
                    .time(timestamp, WritePrecision.NS);
            
            points.add(issuePoint);
        }
        
        // Write all points in a batch
        try (WriteApi writeApi = influxDBClient.getWriteApi()) {
            writeApi.writePoints(bucket, points);
            log.info("Successfully stored {} points for project: {}", points.size(), projectId);
        } catch (Exception e) {
            log.error("Failed to store metrics in InfluxDB for project: {}", projectId, e);
            throw new RuntimeException("Failed to store metrics in InfluxDB", e);
        }
    }
    
    /**
     * Fetches the InfluxDB API token from AWS Secrets Manager.
     * 
     * @return The InfluxDB API token
     */
    private static String fetchInfluxDBToken() {
        try {
            return SecretManager.getSecretString(INFLUXDB_TOKEN_SECRET_NAME);
        } catch (Exception e) {
            log.error("Failed to fetch InfluxDB token from Secrets Manager", e);
            throw new RuntimeException("Failed to fetch InfluxDB token", e);
        }
    }
}