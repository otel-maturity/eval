package io.otel.maturity.agent.quality.quarkus.model;

public record ChatRequest(String conversationId,
                          String clusterName,
                          String projectName,
                          String projectUrl,
                          String message) {}
