package wkda.api.lambda.client;

import com.auto1.core.lambda.http.client.HttpClientFactory;
import com.auto1.core.lambda.secret.SecretManager;
import com.auto1.core.lambda.tracing.WithLambdaTracing;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import wkda.api.lambda.model.SonarQubeMetrics;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.auto1.core.lambda.serialization.ObjectMapperConfiguration.MAPPER;

/**
 * Client for interacting with the SonarQube API.
 */
@Log4j2
public class SonarQubeClient {

    private static final String SONARQUBE_URL_ENV_VAR = "SONARQUBE_URL";
    private static final String SONARQUBE_TOKEN_SECRET_NAME = "sonarqube/api-token";
    private static final int CONNECTION_TIMEOUT_SECONDS = 10;
    private static final int READ_TIMEOUT_SECONDS = 30;
    private static final int WRITE_TIMEOUT_SECONDS = 10;
    
    private final OkHttpClient httpClient;
    private final String sonarQubeUrl;
    private final String sonarQubeToken;
    private final ObjectMapper objectMapper;
    
    /**
     * Creates a new SonarQubeClient with default configuration.
     */
    public SonarQubeClient() {
        this.httpClient = createHttpClient();
        this.sonarQubeUrl = System.getenv(SONARQUBE_URL_ENV_VAR);
        this.sonarQubeToken = fetchSonarQubeToken();
        this.objectMapper = MAPPER;
    }
    
    /**
     * Creates a new SonarQubeClient with custom configuration (for testing).
     */
    public SonarQubeClient(OkHttpClient httpClient, String sonarQubeUrl, String sonarQubeToken) {
        this.httpClient = httpClient;
        this.sonarQubeUrl = sonarQubeUrl;
        this.sonarQubeToken = sonarQubeToken;
        this.objectMapper = MAPPER;
    }
    
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
        
        // Fetch project information
        JsonNode projectInfo = fetchProjectInfo(projectId);
        String projectName = projectInfo.path("name").asText();
        
        // Fetch component measures
        Map<String, Object> measures = fetchComponentMeasures(projectId);
        
        // Fetch quality gate status
        String qualityGateStatus = fetchQualityGateStatus(projectId);
        
        // Fetch issues summary
        Map<String, Integer> issuesBySeverity = fetchIssuesBySeverity(projectId);
        Map<String, Integer> issuesByType = fetchIssuesByType(projectId);
        
        return SonarQubeMetrics.builder()
                .projectId(projectId)
                .projectName(projectName)
                .qualityGateStatus(qualityGateStatus)
                .measures(measures)
                .issuesBySeverity(issuesBySeverity)
                .issuesByType(issuesByType)
                .build();
    }
    
    /**
     * Fetches project information from SonarQube.
     * 
     * @param projectId The ID of the project in SonarQube
     * @return The project information as a JsonNode
     * @throws IOException if the API call fails
     */
    private JsonNode fetchProjectInfo(String projectId) throws IOException {
        HttpUrl url = HttpUrl.parse(sonarQubeUrl + "/api/projects/search")
                .newBuilder()
                .addQueryParameter("projects", projectId)
                .build();
        
        Request request = createAuthenticatedRequest(url);
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch project info: " + response.code());
            }
            
            JsonNode responseJson = objectMapper.readTree(response.body().string());
            return responseJson.path("components").get(0);
        }
    }
    
    /**
     * Fetches component measures from SonarQube.
     * 
     * @param projectId The ID of the project in SonarQube
     * @return A map of metric keys to their values
     * @throws IOException if the API call fails
     */
    private Map<String, Object> fetchComponentMeasures(String projectId) throws IOException {
        String metricKeys = "ncloc,coverage,duplicated_lines_density,bugs,vulnerabilities,code_smells,security_hotspots";
        
        HttpUrl url = HttpUrl.parse(sonarQubeUrl + "/api/measures/component")
                .newBuilder()
                .addQueryParameter("component", projectId)
                .addQueryParameter("metricKeys", metricKeys)
                .build();
        
        Request request = createAuthenticatedRequest(url);
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch component measures: " + response.code());
            }
            
            JsonNode responseJson = objectMapper.readTree(response.body().string());
            JsonNode measuresNode = responseJson.path("component").path("measures");
            
            Map<String, Object> measures = new HashMap<>();
            for (JsonNode measure : measuresNode) {
                String metricKey = measure.path("metric").asText();
                String value = measure.path("value").asText();
                
                // Try to parse as number if possible
                try {
                    if (value.contains(".")) {
                        measures.put(metricKey, Double.parseDouble(value));
                    } else {
                        measures.put(metricKey, Integer.parseInt(value));
                    }
                } catch (NumberFormatException e) {
                    measures.put(metricKey, value);
                }
            }
            
            return measures;
        }
    }
    
    /**
     * Fetches quality gate status from SonarQube.
     * 
     * @param projectId The ID of the project in SonarQube
     * @return The quality gate status (e.g., "PASSED", "FAILED")
     * @throws IOException if the API call fails
     */
    private String fetchQualityGateStatus(String projectId) throws IOException {
        HttpUrl url = HttpUrl.parse(sonarQubeUrl + "/api/qualitygates/project_status")
                .newBuilder()
                .addQueryParameter("projectKey", projectId)
                .build();
        
        Request request = createAuthenticatedRequest(url);
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch quality gate status: " + response.code());
            }
            
            JsonNode responseJson = objectMapper.readTree(response.body().string());
            return responseJson.path("projectStatus").path("status").asText();
        }
    }
    
    /**
     * Fetches issues by severity from SonarQube.
     * 
     * @param projectId The ID of the project in SonarQube
     * @return A map of severity to issue count
     * @throws IOException if the API call fails
     */
    private Map<String, Integer> fetchIssuesBySeverity(String projectId) throws IOException {
        Map<String, Integer> issuesBySeverity = new HashMap<>();
        String[] severities = {"BLOCKER", "CRITICAL", "MAJOR", "MINOR", "INFO"};
        
        for (String severity : severities) {
            HttpUrl url = HttpUrl.parse(sonarQubeUrl + "/api/issues/search")
                    .newBuilder()
                    .addQueryParameter("componentKeys", projectId)
                    .addQueryParameter("severities", severity)
                    .addQueryParameter("ps", "1") // Page size 1, we only need the total
                    .build();
            
            Request request = createAuthenticatedRequest(url);
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to fetch issues by severity: " + response.code());
                }
                
                JsonNode responseJson = objectMapper.readTree(response.body().string());
                int total = responseJson.path("total").asInt();
                issuesBySeverity.put(severity, total);
            }
        }
        
        return issuesBySeverity;
    }
    
    /**
     * Fetches issues by type from SonarQube.
     * 
     * @param projectId The ID of the project in SonarQube
     * @return A map of type to issue count
     * @throws IOException if the API call fails
     */
    private Map<String, Integer> fetchIssuesByType(String projectId) throws IOException {
        Map<String, Integer> issuesByType = new HashMap<>();
        String[] types = {"BUG", "VULNERABILITY", "CODE_SMELL", "SECURITY_HOTSPOT"};
        
        for (String type : types) {
            HttpUrl url = HttpUrl.parse(sonarQubeUrl + "/api/issues/search")
                    .newBuilder()
                    .addQueryParameter("componentKeys", projectId)
                    .addQueryParameter("types", type)
                    .addQueryParameter("ps", "1") // Page size 1, we only need the total
                    .build();
            
            Request request = createAuthenticatedRequest(url);
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to fetch issues by type: " + response.code());
                }
                
                JsonNode responseJson = objectMapper.readTree(response.body().string());
                int total = responseJson.path("total").asInt();
                issuesByType.put(type, total);
            }
        }
        
        return issuesByType;
    }
    
    /**
     * Creates an authenticated request for the SonarQube API.
     * 
     * @param url The URL to request
     * @return The authenticated request
     */
    private Request createAuthenticatedRequest(HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString((sonarQubeToken + ":").getBytes()))
                .build();
    }
    
    /**
     * Creates an HTTP client with connection pooling and timeouts.
     * 
     * @return The HTTP client
     */
    private OkHttpClient createHttpClient() {
        return HttpClientFactory.createClient()
                .newBuilder()
                .connectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Fetches the SonarQube API token from AWS Secrets Manager.
     * 
     * @return The SonarQube API token
     */
    private String fetchSonarQubeToken() {
        try {
            return SecretManager.getSecretString(SONARQUBE_TOKEN_SECRET_NAME);
        } catch (Exception e) {
            log.error("Failed to fetch SonarQube token from Secrets Manager", e);
            throw new RuntimeException("Failed to fetch SonarQube token", e);
        }
    }
}