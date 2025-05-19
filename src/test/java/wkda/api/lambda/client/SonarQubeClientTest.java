package wkda.api.lambda.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import wkda.api.lambda.model.SonarQubeMetrics;

import java.io.IOException;
import java.util.Map;

import static com.auto1.core.lambda.serialization.ObjectMapperConfiguration.MAPPER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SonarQubeClientTest {

    private OkHttpClient httpClient;
    private SonarQubeClient sonarQubeClient;
    private ObjectMapper objectMapper;
    private Call call;
    private Response response;
    private ResponseBody responseBody;

    @BeforeEach
    void setUp() {
        httpClient = mock(OkHttpClient.class);
        call = mock(Call.class);
        response = mock(Response.class);
        responseBody = mock(ResponseBody.class);
        objectMapper = MAPPER;

        when(httpClient.newCall(any(Request.class))).thenReturn(call);
        when(response.body()).thenReturn(responseBody);
        when(response.isSuccessful()).thenReturn(true);

        sonarQubeClient = new SonarQubeClient(httpClient, "http://sonarqube.example.com", "test-token");
    }

    @Test
    void shouldFetchMetricsSuccessfully() throws IOException {
        // Given
        String projectId = "test-project";
        
        // Mock project info response
        String projectInfoJson = createProjectInfoJson(projectId, "Test Project");
        when(responseBody.string()).thenReturn(projectInfoJson);
        when(call.execute()).thenReturn(response);
        
        // Mock component measures response
        String measuresJson = createComponentMeasuresJson();
        Response measuresResponse = mock(Response.class);
        ResponseBody measuresResponseBody = mock(ResponseBody.class);
        Call measuresCall = mock(Call.class);
        when(measuresResponse.body()).thenReturn(measuresResponseBody);
        when(measuresResponse.isSuccessful()).thenReturn(true);
        when(measuresResponseBody.string()).thenReturn(measuresJson);
        when(measuresCall.execute()).thenReturn(measuresResponse);
        
        // Mock quality gate status response
        String qualityGateJson = createQualityGateJson("PASSED");
        Response qualityGateResponse = mock(Response.class);
        ResponseBody qualityGateResponseBody = mock(ResponseBody.class);
        Call qualityGateCall = mock(Call.class);
        when(qualityGateResponse.body()).thenReturn(qualityGateResponseBody);
        when(qualityGateResponse.isSuccessful()).thenReturn(true);
        when(qualityGateResponseBody.string()).thenReturn(qualityGateJson);
        when(qualityGateCall.execute()).thenReturn(qualityGateResponse);
        
        // Mock issues by severity response
        String issuesJson = createIssuesJson(10);
        Response issuesResponse = mock(Response.class);
        ResponseBody issuesResponseBody = mock(ResponseBody.class);
        Call issuesCall = mock(Call.class);
        when(issuesResponse.body()).thenReturn(issuesResponseBody);
        when(issuesResponse.isSuccessful()).thenReturn(true);
        when(issuesResponseBody.string()).thenReturn(issuesJson);
        when(issuesCall.execute()).thenReturn(issuesResponse);
        
        // Set up call sequence
        when(httpClient.newCall(any(Request.class)))
                .thenReturn(call)
                .thenReturn(measuresCall)
                .thenReturn(qualityGateCall)
                .thenReturn(issuesCall)
                .thenReturn(issuesCall)
                .thenReturn(issuesCall)
                .thenReturn(issuesCall)
                .thenReturn(issuesCall)
                .thenReturn(issuesCall)
                .thenReturn(issuesCall)
                .thenReturn(issuesCall);

        // When
        SonarQubeMetrics metrics = sonarQubeClient.fetchMetrics(projectId);

        // Then
        assertNotNull(metrics);
        assertEquals(projectId, metrics.getProjectId());
        assertEquals("Test Project", metrics.getProjectName());
        assertEquals("PASSED", metrics.getQualityGateStatus());
        
        // Verify measures
        Map<String, Object> measures = metrics.getMeasures();
        assertNotNull(measures);
        assertEquals(1000, measures.get("ncloc"));
        assertEquals(80.5, measures.get("coverage"));
        
        // Verify API calls
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(httpClient, atLeastOnce()).newCall(requestCaptor.capture());
        
        // Verify first request is to projects API
        Request firstRequest = requestCaptor.getAllValues().get(0);
        assertTrue(firstRequest.url().toString().contains("/api/projects/search"));
        assertTrue(firstRequest.url().toString().contains("projects=" + projectId));
    }

    private String createProjectInfoJson(String projectId, String projectName) throws IOException {
        ObjectNode rootNode = objectMapper.createObjectNode();
        ArrayNode componentsNode = objectMapper.createArrayNode();
        ObjectNode componentNode = objectMapper.createObjectNode();
        
        componentNode.put("key", projectId);
        componentNode.put("name", projectName);
        componentsNode.add(componentNode);
        rootNode.set("components", componentsNode);
        
        return objectMapper.writeValueAsString(rootNode);
    }

    private String createComponentMeasuresJson() throws IOException {
        ObjectNode rootNode = objectMapper.createObjectNode();
        ObjectNode componentNode = objectMapper.createObjectNode();
        ArrayNode measuresNode = objectMapper.createArrayNode();
        
        // Add ncloc measure
        ObjectNode nclocNode = objectMapper.createObjectNode();
        nclocNode.put("metric", "ncloc");
        nclocNode.put("value", "1000");
        measuresNode.add(nclocNode);
        
        // Add coverage measure
        ObjectNode coverageNode = objectMapper.createObjectNode();
        coverageNode.put("metric", "coverage");
        coverageNode.put("value", "80.5");
        measuresNode.add(coverageNode);
        
        // Add duplicated_lines_density measure
        ObjectNode duplicatedNode = objectMapper.createObjectNode();
        duplicatedNode.put("metric", "duplicated_lines_density");
        duplicatedNode.put("value", "5.2");
        measuresNode.add(duplicatedNode);
        
        // Add bugs measure
        ObjectNode bugsNode = objectMapper.createObjectNode();
        bugsNode.put("metric", "bugs");
        bugsNode.put("value", "10");
        measuresNode.add(bugsNode);
        
        componentNode.set("measures", measuresNode);
        rootNode.set("component", componentNode);
        
        return objectMapper.writeValueAsString(rootNode);
    }

    private String createQualityGateJson(String status) throws IOException {
        ObjectNode rootNode = objectMapper.createObjectNode();
        ObjectNode projectStatusNode = objectMapper.createObjectNode();
        
        projectStatusNode.put("status", status);
        rootNode.set("projectStatus", projectStatusNode);
        
        return objectMapper.writeValueAsString(rootNode);
    }

    private String createIssuesJson(int total) throws IOException {
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("total", total);
        
        return objectMapper.writeValueAsString(rootNode);
    }
}