package io.otel.maturity.agent.integration.quarkus.model;

public record ChatRequest(String conversationId,
                          String clusterName,
                          String projectName,
                          String projectUrl,
                          String message) {}
