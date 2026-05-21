package io.otel.maturity.agent.report.quarkus;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.otel.maturity.agent.report.quarkus.tools.FileSystemTools;
import io.otel.maturity.agent.report.quarkus.tools.ShellTools;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(
        systemMessageProviderSupplier = ReportSystemMessageProvider.class,
        tools = { FileSystemTools.class, ShellTools.class }
)
@ApplicationScoped
public interface ReportAgent {

    Multi<String> chatStream(@MemoryId String conversationId, @UserMessage String userPrompt);
}
