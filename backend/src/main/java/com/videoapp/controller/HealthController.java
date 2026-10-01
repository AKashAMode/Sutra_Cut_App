package com.videoapp.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.videoapp.service.PythonWorkerClient;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final PythonWorkerClient pythonWorkerClient;

    public HealthController(PythonWorkerClient pythonWorkerClient) {
        this.pythonWorkerClient = pythonWorkerClient;
    }

    @GetMapping
    public Map<String, Object> health() {
        PythonWorkerClient.HealthResult worker = pythonWorkerClient.health();
        return Map.of(
                "status", "ok",
                "worker", worker == null ? "unknown" : worker.status());
    }

    @GetMapping("/worker")
    public PythonWorkerClient.HealthResult worker() {
        return pythonWorkerClient.health();
    }
}
