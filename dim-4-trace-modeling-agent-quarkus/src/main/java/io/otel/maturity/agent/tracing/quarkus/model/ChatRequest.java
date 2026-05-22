package io.otel.maturity.agent.tracing.quarkus.model;

public record ChatRequest(String conversationId,
                          String clusterName,
                          String projectName,
                          String projectUrl,
                          String message) {}
