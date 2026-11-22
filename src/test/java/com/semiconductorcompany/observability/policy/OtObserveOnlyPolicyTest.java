package com.semiconductorcompany.observability.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semiconductorcompany.observability.agents.ReadOnlyTools;
import com.semiconductorcompany.observability.graph.GraphReader;
import com.semiconductorcompany.observability.telemetry.TelemetryReader;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Build-time half of hard constraint 1: the build fails if a write path to L0 appears. */
class OtObserveOnlyPolicyTest {

    record Tool(String name, String mode, List<String> zones, String note) {
    }

    record Manifest(List<Tool> tools) {
    }

    private static Manifest manifest() throws Exception {
        try (InputStream in = OtObserveOnlyPolicyTest.class.getResourceAsStream("/policy/agent-tools.json")) {
            return new ObjectMapper().readValue(in, Manifest.class);
        }
    }

    @Test
    void noToolWritesToAnL0Zone() throws Exception {
        assertThat(manifest().tools())
                .as("tools that are not read-only must not list L0")
                .noneMatch(t -> !"read".equals(t.mode()) && t.zones().contains("L0"));
    }

    @Test
    void everyAgentToolIsDeclaredReadOnly() throws Exception {
        Set<String> readTools = manifest().tools().stream()
                .filter(t -> "read".equals(t.mode()))
                .map(Tool::name)
                .collect(Collectors.toSet());
        List<String> agentTools = Arrays.stream(ReadOnlyTools.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(m -> "agent." + m.getName())
                .toList();
        assertThat(agentTools).isNotEmpty();
        assertThat(readTools).as("every public ReadOnlyTools method must be declared read-only").containsAll(agentTools);
    }

    @Test
    void agentToolsHoldOnlyReadOnlyDependencies() {
        Set<Class<?>> allowed = Set.of(String.class, GraphReader.class, TelemetryReader.class, Instant.class, List.class);
        assertThat(ReadOnlyTools.class.getDeclaredFields())
                .extracting(Field::getType)
                .allMatch(allowed::contains);
    }
}
