package io.otel.maturity.agent.tracing.model;

public record ChatRequest(String conversationId, String clusterName,
                          String projectName, String projectUrl,
                          String message) {}
