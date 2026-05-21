package io.otel.maturity.agent.stability.quarkus;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.otel.maturity.agent.stability.quarkus.tools.FileSystemTools;
import io.otel.maturity.agent.stability.quarkus.tools.ShellTools;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(
        systemMessageProviderSupplier = StabilitySystemMessageProvider.class,
        tools = { FileSystemTools.class, ShellTools.class }
)
@ApplicationScoped
public interface StabilityAgent {

    Multi<String> chatStream(@MemoryId String conversationId, @UserMessage String userPrompt);

    String evaluate(@UserMessage String userPrompt);
}
