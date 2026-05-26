package org.example.api;

import org.example.config.ScenarioStore;
import org.example.model.DisasterScenario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ScenarioStore scenarioStore;

    public ScenarioController(ScenarioStore scenarioStore) {
        this.scenarioStore = scenarioStore;
    }

    @GetMapping
    public ResponseEntity<List<DisasterScenario>> listScenarios() {
        return ResponseEntity.ok(new ArrayList<>(scenarioStore.getAll().values()));
    }

    @PostMapping
    public ResponseEntity<DisasterScenario> createScenario(@RequestBody DisasterScenario scenario) {
        scenarioStore.put(scenario.getScenarioId(), scenario);
        return ResponseEntity.ok(scenario);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScenario(@PathVariable String id) {
        scenarioStore.remove(id);
        return ResponseEntity.noContent().build();
    }
}
