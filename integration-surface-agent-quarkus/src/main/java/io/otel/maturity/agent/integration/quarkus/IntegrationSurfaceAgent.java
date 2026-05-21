package io.otel.maturity.agent.integration.quarkus;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.otel.maturity.agent.integration.quarkus.tools.FileSystemTools;
import io.otel.maturity.agent.integration.quarkus.tools.ShellTools;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.skills.SkillsSystemMessageProvider;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(
        systemMessageProviderSupplier = SkillsSystemMessageProvider.class,
        tools = { FileSystemTools.class, ShellTools.class }
)
@ApplicationScoped
public interface IntegrationSurfaceAgent {

    Multi<String> chatStream(@MemoryId String conversationId, @UserMessage String userPrompt);

    String evaluate(@UserMessage String userPrompt);
}
