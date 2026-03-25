package org.example.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import org.example.graph.Graph;
import org.example.model.City;
import org.example.model.Edge;
import org.example.persistence.GraphStore;
import org.example.ui.interaction.InteractionController;
import org.example.ui.render.TopologyCanvasRenderer;
import org.example.ui.viewmodel.MainViewModel;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainController {
    // 控件id声明
    @FXML   // 标记该方法可被FXML事件系统调用
    private Canvas topologyCanvas;
    @FXML
    private Label statusLabel;
    @FXML
    private Label mouseCoordLabel;
    @FXML
    private SplitPane mainSplitPane;
    @FXML
    private StackPane canvasContainer;
    @FXML
    private javafx.scene.layout.VBox rightPanelBox;
    @FXML
    private TableView<City> cityTable;
    @FXML
    private TableColumn<City, Number> cityIdColumn;
    @FXML
    private TableColumn<City, String> cityNameColumn;
    @FXML
    private TableColumn<City, Number> cityXColumn;
    @FXML
    private TableColumn<City, Number> cityYColumn;
    @FXML
    private TableColumn<City, String> cityDescColumn;
    @FXML
    private TextField cityIdField;
    @FXML
    private TextField cityNameField;
    @FXML
    private TextField cityXField;
    @FXML
    private TextField cityYField;
    @FXML
    private TextArea cityDescArea;
    @FXML
    private TextField edgeFromField;
    @FXML
    private TextField edgeToField;
    @FXML
    private ListView<Edge> edgeListView;
    @FXML
    private TextField shortestSourceField;
    @FXML
    private TextArea algorithmOutputArea;
    @FXML
    private TextField citySearchField;
    @FXML
    private ComboBox<String> zoomComboBox;

    private final Graph graph = new Graph();
    private final GraphStore graphStore = new GraphStore(Path.of("data/cities.csv"), Path.of("data/edges.csv"));
    private final InteractionController interactionController = new InteractionController();
    private final TopologyCanvasRenderer renderer = new TopologyCanvasRenderer();
    private final MainViewModel viewModel = new MainViewModel();
    private final ObservableList<City> cityItems = FXCollections.observableArrayList();
    private final ObservableList<Edge> edgeItems = FXCollections.observableArrayList();
    private final Set<String> highlightedPathEdgeKeys = new HashSet<>();
    private List<Edge> overlayHighlightedEdges = List.of();
    private List<Edge> suggestedEdges = List.of();
    private Integer selectedCityId;
    private double worldMinX;
    private double worldMaxX;
    private double worldMinY;
    private double worldMaxY;
    private double viewMargin = 64;
    private double viewFitScale = 1;
    private double viewCenteredLeft = 64;
    private double viewCenteredTop = 64;
    private boolean rightPanelHidden;
    private double lastDividerPosition = 0.67;
    private final ObservableList<String> zoomPresets = FXCollections.observableArrayList(
            "35%", "50%", "75%", "100%", "125%", "150%", "200%", "300%", "500%"
    );

    @FXML
    // 处理“导入数据”
    private void onLoadData() {
        loadFromDisk(); // 从 GraphStore 读取文件并恢复到当前图对象
        refreshViews(); // 刷新表格、列表和画布，确保界面与新数据一致
        viewModel.setStatusText("数据导入成功。");
    }

    @FXML
    // 保存数据
    private void onSaveData() {
        try {
            graphStore.save(graph);     // 写入两个csv文件
            viewModel.setStatusText("数据保存成功: " + graphStore.citiesFile() + " ; " + graphStore.edgesFile());
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    // 处理“添加城市”
    private void onAddCity() {
        try {
            // 从表单读取并校验城市字段
            City city = readCityFromForm();
            if (graph.city(city.id()).isPresent()) {
                showError("城市编号已存在，请使用“更新城市”。");
                return;
            }
            graph.upsertCity(city);
            refreshViews();
            viewModel.setStatusText("城市已添加: " + city.name());
            // 数据变化后清除历史算法高亮，避免误导
            clearAlgorithmHighlights();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    // 处理“更新城市”
    private void onUpdateCity() {
        try {
            City city = readCityFromForm();
            graph.upsertCity(city);
            refreshViews();
            viewModel.setStatusText("城市已更新: " + city.name());
            clearAlgorithmHighlights();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    // 处理“删除城市”
    private void onDeleteCity() {
        // 解析编号输入，失败会弹错并返回 null
        Integer id = tryParseInt(cityIdField.getText(), "城市编号");
        if (id == null) {
            return;
        }
        if (!graph.hasCity(id)) {
            showError("城市不存在: " + id);
            return;
        }
        graph.removeCity(id); // 从图中删除城市及关联边
        refreshViews();
        viewModel.setStatusText("城市已删除: " + id);
        clearAlgorithmHighlights();
    }

    private Integer tryParseInt(String raw, String fieldName) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            showError(fieldName + "必须是整数。");
            return null;
        }
    }

    // 清除历史算法高亮
    private void clearAlgorithmHighlights() {
        highlightedPathEdgeKeys.clear();
        overlayHighlightedEdges = List.of();
        suggestedEdges = List.of();
        algorithmOutputArea.clear();
        renderGraph();
    }

    private City readCityFromForm() {
        Integer id = tryParseInt(cityIdField.getText(), "城市编号");
        Integer x = tryParseInt(cityXField.getText(), "X 坐标");
        Integer y = tryParseInt(cityYField.getText(), "Y 坐标");
        String name = cityNameField.getText() == null ? "" : cityNameField.getText().trim();
        String desc = cityDescArea.getText() == null ? "" : cityDescArea.getText().trim();

        if (id == null || x == null || y == null) {
            throw new IllegalArgumentException("请输入完整且合法的城市编号与坐标。");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("城市名称不能为空。");
        }
        return new City(id, name, x, y, desc);
    }

    private void loadFromDisk() {
        try {
            Graph loaded = graphStore.load();
            // 清空当前图中的所有城市
            graph.cities().stream().map(City::id).toList().forEach(graph::removeCity);
            // 将加载的城市和边添加到当前图
            loaded.cities().forEach(graph::upsertCity);
            loaded.edges().forEach(graph::upsertEdge);
            viewModel.setStatusText("已从文件加载数据。");
        } catch (Exception e) {
            showError("加载失败: " + e.getMessage());
        }
    }

    private void refreshViews() {
        cityItems.setAll(graph.cities().stream().sorted(Comparator.comparingInt(City::id)).toList());
        edgeItems.setAll(graph.edges().stream()
                .sorted(Comparator.comparingInt(Edge::fromId).thenComparingInt(Edge::toId))
                .toList());
        renderGraph();
    }

    private void renderGraph() {
        updateViewportMapping();
        renderer.drawGraph(
                topologyCanvas,
                graph,
                selectedCityId,
                interactionController.scale(),
                interactionController.offsetX(),
                interactionController.offsetY(),
                highlightedPathEdgeKeys,
                overlayHighlightedEdges,
                suggestedEdges
        );
    }

    private void updateViewportMapping() {
        if (graph.cities().isEmpty()) {
            worldMinX = 0;
            worldMaxX = Math.max(1, topologyCanvas.getWidth() / interactionController.scale());
            worldMinY = 0;
            worldMaxY = Math.max(1, topologyCanvas.getHeight() / interactionController.scale());
            viewFitScale = 1;
            viewCenteredLeft = 0;
            viewCenteredTop = 0;
            return;
        }
        worldMinX = graph.cities().stream().mapToInt(City::x).min().orElse(0);
        worldMaxX = graph.cities().stream().mapToInt(City::x).max().orElse(1);
        worldMinY = graph.cities().stream().mapToInt(City::y).min().orElse(0);
        worldMaxY = graph.cities().stream().mapToInt(City::y).max().orElse(1);
        double drawableWidth = Math.max(1, topologyCanvas.getWidth() - viewMargin * 2);
        double drawableHeight = Math.max(1, topologyCanvas.getHeight() - viewMargin * 2);
        double worldWidth = Math.max(1, worldMaxX - worldMinX);
        double worldHeight = Math.max(1, worldMaxY - worldMinY);
        viewFitScale = Math.min(drawableWidth / worldWidth, drawableHeight / worldHeight);
        viewCenteredLeft = viewMargin + (drawableWidth - worldWidth * viewFitScale) / 2.0;
        viewCenteredTop = viewMargin + (drawableHeight - worldHeight * viewFitScale) / 2.0;
    }

    private void showError(String message) {
        viewModel.setStatusText("错误: " + message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("操作失败");
        alert.showAndWait();
    }
}
