package com.videoapp.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Service
public class PythonWorkerClient {

    private static final Logger log = LoggerFactory.getLogger(PythonWorkerClient.class);

    private final RestClient restClient;

    public PythonWorkerClient(RestClient pythonWorkerRestClient) {
        this.restClient = pythonWorkerRestClient;
    }

    public ProbeResult probe(String mediaPath) {
        return post("/probe", Map.of("mediaPath", mediaPath), ProbeResult.class);
    }

    public TranscribeResult transcribe(String mediaPath, String language) {
        return post("/transcribe", Map.of(
                "mediaPath", mediaPath,
                "language", language == null ? "" : language), TranscribeResult.class);
    }

    public MatchAssetsResult matchAssets(List<Map<String, Object>> segments) {
        return post("/match-assets", Map.of("segments", segments), MatchAssetsResult.class);
    }

    public RenderSegmentResult renderSegment(Map<String, Object> payload) {
        return post("/render-segment", payload, RenderSegmentResult.class);
    }

    public RenderFinalResult renderFinal(Map<String, Object> payload) {
        return post("/render-final", payload, RenderFinalResult.class);
    }

    public HealthResult health() {
        try {
            return restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(HealthResult.class);
        } catch (RestClientException ex) {
            log.warn("Worker health check failed: {}", ex.getMessage());
            return new HealthResult("down", false, false);
        }
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (RestClientException ex) {
            log.error("Worker call failed {} : {}", path, ex.getMessage());
            throw new IllegalStateException("Python worker request failed: " + path + " - " + ex.getMessage(), ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HealthResult(String status, boolean ffmpeg, boolean whisper) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProbeResult(
            boolean valid,
            String error,
            Double duration,
            Integer width,
            Integer height,
            String codec,
            boolean hasAudio,
            boolean hasVideo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WordStamp(String word, double start, double end) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TranscriptSegmentResult(
            double start,
            double end,
            String text,
            String language,
            List<WordStamp> words) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TranscribeResult(
            String language,
            Double duration,
            List<TranscriptSegmentResult> segments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchedAsset(
            @JsonProperty("segmentId") String segmentId,
            String keyword,
            String visualType,
            String assetSource,
            String assetUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchAssetsResult(List<MatchedAsset> assets) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderSegmentResult(String clipPath, boolean captionOnly) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RenderFinalResult(String outputPath, Double duration) {
    }
}
