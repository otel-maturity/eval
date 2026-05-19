package io.otel.maturity.agent.progress.model;

public record ChatRequest(String conversationId, String projectName, String projectUrl, String message) {}
