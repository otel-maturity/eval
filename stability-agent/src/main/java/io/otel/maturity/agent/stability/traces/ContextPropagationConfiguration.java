package io.otel.maturity.agent.stability.traces;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

@Configuration(proxyBeanMethods = false)
public class ContextPropagationConfiguration {

    @PostConstruct
    void enableReactorContextPropagation() {
        Hooks.enableAutomaticContextPropagation();  // bridges ThreadLocal → Reactor Context
    }

}
