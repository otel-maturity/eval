package io.otel.maturity.agent.stability.model;

public record ChatRequest(String conversationId, String clusterName,
                          String projectName, String projectUrl,
                          String message) {}
