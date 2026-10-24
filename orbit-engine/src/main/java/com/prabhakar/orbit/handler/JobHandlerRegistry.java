package com.prabhakar.orbit.handler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlersByType;

    public JobHandlerRegistry(List<JobHandler> handlers) {
        this.handlersByType = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(JobHandler::type, Function.identity()));
    }

    public boolean supports(String type) {
        return handlersByType.containsKey(type);
    }

    public Optional<JobHandler> find(String type) {
        return Optional.ofNullable(handlersByType.get(type));
    }
}
