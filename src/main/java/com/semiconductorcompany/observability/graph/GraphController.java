package com.semiconductorcompany.observability.graph;

import com.semiconductorcompany.observability.config.PlatformProperties;
import com.semiconductorcompany.observability.web.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only query API for agents and the console (G-4). Only GETs, backed by {@link GraphReader}. */
@RestController
@RequestMapping("/api/graph")
class GraphController {

    private final GraphReader graph;
    private final PlatformProperties props;

    GraphController(GraphReader graph, PlatformProperties props) {
        this.graph = graph;
        this.props = props;
    }

    record Neighbour(int hops, Node node) {
    }

    @GetMapping("/nodes")
    List<Node> nodes(@RequestParam(required = false) NodeType type) {
        return graph.nodes(type);
    }

    @GetMapping("/nodes/{id}")
    Node node(@PathVariable String id) {
        return graph.node(id).orElseThrow(() -> new NotFoundException("Node " + id));
    }

    @GetMapping("/nodes/{id}/neighbours")
    List<Neighbour> neighbours(@PathVariable String id,
                               @RequestParam(defaultValue = "2") int hops,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        node(id);
        return graph.neighbourhood(List.of(id), hops, atOrNow(at)).entrySet().stream()
                .map(e -> new Neighbour(e.getValue(), graph.node(e.getKey()).orElseThrow()))
                .toList();
    }

    /** "What changed within two hops of this asset in the last 7 days?" */
    @GetMapping("/nodes/{id}/changes")
    List<ChangeHit> changes(@PathVariable String id,
                            @RequestParam(required = false) Integer hops,
                            @RequestParam(required = false) Integer days,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        node(id);
        Duration lookback = days == null ? props.changeLookback() : Duration.ofDays(days);
        return graph.changesNear(List.of(id), Optional.ofNullable(hops).orElse(props.scopeHops()), lookback, atOrNow(at));
    }

    @GetMapping("/nodes/{id}/dependents")
    List<Node> dependents(@PathVariable String id) {
        node(id);
        return graph.dependents(id, Instant.now());
    }

    @GetMapping("/nodes/{id}/zone-tier")
    ZoneTier zoneTier(@PathVariable String id) {
        node(id);
        return graph.zoneTier(id, Instant.now());
    }

    private static Instant atOrNow(Instant at) {
        return at != null ? at : Instant.now();
    }
}
