package org.example.ui.render;

import org.example.graph.Graph;
import org.example.model.City;
import org.example.model.Edge;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TopologyCanvasRenderer {
    public void drawGraph(
            Canvas canvas,
            Graph graph,
            Integer selectedCityId,
            double scale,
            double offsetX,
            double offsetY,
            Set<String> pathEdgeKeys,
            List<Edge> overlayHighlightedEdges,
            List<Edge> suggestedEdges
    ) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        int gridStep = chooseGridStep(scale);
        gc.setStroke(Color.web("#1e293b"));
        for (int x = 0; x <= canvas.getWidth(); x += gridStep) {
            gc.strokeLine(x, 0, x, canvas.getHeight());
        }
        for (int y = 0; y <= canvas.getHeight(); y += gridStep) {
            gc.strokeLine(0, y, canvas.getWidth(), y);
        }

        gc.setFill(Color.web("#e2e8f0"));
        gc.setFont(Font.font("Consolas", 16));
        gc.fillText("CommNet Planner Canvas", 24, 36);
        gc.setFont(Font.font("Consolas", 12));
        gc.setFill(Color.web("#94a3b8"));
        gc.fillText(String.format("Zoom: %.0f%%  Grid: %dpx  Pan: (%.0f, %.0f)", scale * 100, gridStep, offsetX, offsetY), 24, 58);

        if (graph.cities().isEmpty()) {
            gc.setFill(Color.web("#94a3b8"));
            gc.setFont(Font.font("Consolas", 14));
            gc.fillText("暂无城市数据，请在右侧面板添加城市。", 24, 80);
            return;
        }

        double minX = graph.cities().stream().mapToInt(City::x).min().orElse(0);
        double maxX = graph.cities().stream().mapToInt(City::x).max().orElse(1);
        double minY = graph.cities().stream().mapToInt(City::y).min().orElse(0);
        double maxY = graph.cities().stream().mapToInt(City::y).max().orElse(1);

        double margin = 64;
        double drawableWidth = Math.max(1, canvas.getWidth() - margin * 2);
        double drawableHeight = Math.max(1, canvas.getHeight() - margin * 2);
        double worldWidth = Math.max(1, maxX - minX);
        double worldHeight = Math.max(1, maxY - minY);
        double fitScale = Math.min(drawableWidth / worldWidth, drawableHeight / worldHeight);
        double centeredLeft = margin + (drawableWidth - worldWidth * fitScale) / 2.0;
        double centeredTop = margin + (drawableHeight - worldHeight * fitScale) / 2.0;

        Map<Integer, Point> points = new HashMap<>();
        for (City city : graph.cities()) {
            double px = centeredLeft + (city.x() - minX) * fitScale;
            double py = centeredTop + (maxY - city.y()) * fitScale;
            px = px * scale + offsetX;
            py = py * scale + offsetY;
            points.put(city.id(), new Point(px, py));
        }

        boolean hasHighlights = !pathEdgeKeys.isEmpty() || !overlayHighlightedEdges.isEmpty();
        gc.setLineWidth(2);
        for (Edge edge : graph.edges()) {
            Point from = points.get(edge.fromId());
            Point to = points.get(edge.toId());
            if (from == null || to == null) {
                continue;
            }
            String edgeKey = Graph.normalizedEdgeKey(edge.fromId(), edge.toId());
            if (pathEdgeKeys.contains(edgeKey)) {
                gc.setStroke(Color.web("#22c55e"));
                gc.setLineWidth(3.5);
            } else {
                boolean highlighted = selectedCityId != null && (edge.fromId() == selectedCityId || edge.toId() == selectedCityId);
                Color normal = hasHighlights ? Color.color(0.13, 0.83, 0.93, 0.26) : Color.web("#22d3ee");
                gc.setStroke(highlighted ? Color.web("#f59e0b") : normal);
                gc.setLineWidth(highlighted ? 2.8 : 2);
            }
            gc.strokeLine(from.x, from.y, to.x, to.y);
        }

        gc.setStroke(Color.web("#22c55e"));
        gc.setLineWidth(3.5);
        for (Edge edge : overlayHighlightedEdges) {
            Point from = points.get(edge.fromId());
            Point to = points.get(edge.toId());
            if (from == null || to == null) {
                continue;
            }
            gc.strokeLine(from.x, from.y, to.x, to.y);
        }

        gc.setLineDashes(10, 8);
        gc.setLineWidth(2.8);
        gc.setStroke(Color.web("#f97316"));
        for (Edge suggested : suggestedEdges) {
            Point from = points.get(suggested.fromId());
            Point to = points.get(suggested.toId());
            if (from == null || to == null) {
                continue;
            }
            gc.strokeLine(from.x, from.y, to.x, to.y);
        }
        gc.setLineDashes();

        gc.setFont(Font.font("Consolas", 12));
        for (City city : graph.cities()) {
            Point p = points.get(city.id());
            boolean selected = selectedCityId != null && city.id() == selectedCityId;
            double radius = selected ? 9 : 7;

            gc.setFill(selected ? Color.web("#f97316") : Color.web("#38bdf8"));
            gc.fillOval(p.x - radius, p.y - radius, radius * 2, radius * 2);
            gc.setStroke(Color.web("#e2e8f0"));
            gc.setLineWidth(1.2);
            gc.strokeOval(p.x - radius, p.y - radius, radius * 2, radius * 2);

            gc.setFill(Color.web("#e2e8f0"));
            gc.fillText(city.name() + "(" + city.id() + ")", p.x + 10, p.y - 10);
        }
    }

    private record Point(double x, double y) {
    }

    private int chooseGridStep(double scale) {
        double[] candidates = {16, 24, 32, 40, 50, 64, 80, 96, 120, 160, 200};
        double target = 40 / Math.max(0.35, scale);
        double best = candidates[0];
        double bestDiff = Math.abs(best - target);
        for (double c : candidates) {
            double diff = Math.abs(c - target);
            if (diff < bestDiff) {
                best = c;
                bestDiff = diff;
            }
        }
        return (int) Math.round(best);
    }
}
