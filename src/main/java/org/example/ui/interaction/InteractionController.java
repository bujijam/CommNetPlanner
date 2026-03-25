package org.example.ui.interaction;

import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;

public class InteractionController {
    private double scale = 1.0;
    private double offsetX;
    private double offsetY;
    private double dragStartX;
    private double dragStartY;
    private boolean dragging;

    public void attach(Canvas canvas, Runnable onViewportChanged) {
        canvas.addEventHandler(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() == 0) {
                return;
            }
            double zoomFactor = Math.pow(1.0015, -event.getDeltaY());
            double newScale = clamp(scale * zoomFactor, 0.35, 5.0);
            double ratio = newScale / scale;
            offsetX = event.getX() - ratio * (event.getX() - offsetX);
            offsetY = event.getY() - ratio * (event.getY() - offsetY);
            scale = newScale;
            onViewportChanged.run();
            event.consume();
        });

        canvas.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            dragging = true;
            dragStartX = event.getSceneX();
            dragStartY = event.getSceneY();
        });

        canvas.setOnMouseDragged(event -> {
            if (!dragging) {
                return;
            }
            double dx = event.getSceneX() - dragStartX;
            double dy = event.getSceneY() - dragStartY;
            offsetX += dx;
            offsetY += dy;
            dragStartX = event.getSceneX();
            dragStartY = event.getSceneY();
            onViewportChanged.run();
        });

        canvas.setOnMouseReleased(event -> dragging = false);
    }

    public void resetView() {
        scale = 1.0;
        offsetX = 0;
        offsetY = 0;
    }

    public double scale() {
        return scale;
    }

    public double offsetX() {
        return offsetX;
    }

    public double offsetY() {
        return offsetY;
    }

    public void setScale(double targetScale, double anchorX, double anchorY) {
        double clamped = clamp(targetScale, 0.35, 5.0);
        if (Math.abs(clamped - scale) < 1e-9) {
            return;
        }
        double ratio = clamped / scale;
        offsetX = anchorX - ratio * (anchorX - offsetX);
        offsetY = anchorY - ratio * (anchorY - offsetY);
        scale = clamped;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
