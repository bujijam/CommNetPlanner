package org.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.model.DisasterScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ScenarioStore {
    private static final Logger log = LoggerFactory.getLogger(ScenarioStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String[] PRESET_FILES = {"/scenarios/wenchuan.json", "/scenarios/zhengzhou.json"};
    private static final String USER_SCENARIOS_DIR = "data/scenarios";

    private final Map<String, DisasterScenario> store = Collections.synchronizedMap(new LinkedHashMap<>());

    public ScenarioStore() {
        // Load preset scenarios from classpath
        for (String path : PRESET_FILES) {
            try (InputStream is = getClass().getResourceAsStream(path)) {
                if (is == null) continue;
                DisasterScenario s = MAPPER.readValue(is, DisasterScenario.class);
                store.put(s.getScenarioId(), s);
                log.info("Loaded preset scenario: {}", s.getName());
            } catch (Exception e) {
                log.warn("Failed to load preset scenario {}: {}", path, e.getMessage());
            }
        }
        // Load user-created scenarios from disk
        loadUserScenarios();
    }

    private void loadUserScenarios() {
        Path dir = Paths.get(USER_SCENARIOS_DIR);
        if (!Files.exists(dir)) {
            try { Files.createDirectories(dir); } catch (IOException e) { log.warn("Cannot create scenarios dir: {}", e.getMessage()); }
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                  .forEach(p -> {
                      try {
                          DisasterScenario s = MAPPER.readValue(p.toFile(), DisasterScenario.class);
                          if (!store.containsKey(s.getScenarioId())) {
                              store.put(s.getScenarioId(), s);
                              log.info("Loaded user scenario: {}", s.getName());
                          }
                      } catch (Exception e) {
                          log.warn("Failed to load user scenario {}: {}", p, e.getMessage());
                      }
                  });
        } catch (IOException e) {
            log.warn("Cannot list user scenarios dir: {}", e.getMessage());
        }
    }

    public void put(String id, DisasterScenario scenario) {
        store.put(id, scenario);
        saveToDisk(scenario);
    }

    private void saveToDisk(DisasterScenario scenario) {
        Path dir = Paths.get(USER_SCENARIOS_DIR);
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(scenario.getScenarioId() + ".json");
            try (Writer w = Files.newBufferedWriter(file)) {
                MAPPER.writeValue(w, scenario);
            }
            log.info("Saved scenario to disk: {}", scenario.getName());
        } catch (IOException e) {
            log.warn("Failed to save scenario {} to disk: {}", scenario.getName(), e.getMessage());
        }
    }

    public void remove(String id) {
        store.remove(id);
        // Remove from disk
        Path file = Paths.get(USER_SCENARIOS_DIR).resolve(id + ".json");
        try { Files.deleteIfExists(file); } catch (IOException ignored) {}
    }

    public Map<String, DisasterScenario> getAll() {
        return Collections.unmodifiableMap(store);
    }

    public DisasterScenario get(String id) {
        return store.get(id);
    }
}
