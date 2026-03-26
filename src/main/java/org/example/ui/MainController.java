package org.example.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import org.example.graph.Graph;
import org.example.model.City;
import org.example.model.Edge;
import org.example.persistence.GraphStore;
import org.example.service.*;
import org.example.ui.interaction.InteractionController;
import org.example.ui.render.TopologyCanvasRenderer;
import org.example.ui.viewmodel.MainViewModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
    private final AugmentConnectivityService augmentConnectivityService = new AugmentConnectivityService();
    private final ShortestPathService shortestPathService = new ShortestPathService();
    private final SteinerService steinerService = new SteinerService();
    private final TraversalService traversalService = new TraversalService();
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

    @FXML
    private void onSearchCity() {
        String raw = citySearchField.getText() == null ? "" : citySearchField.getText().trim();
        if (raw.isEmpty()) {
            showError("请输入城市ID或名称。");
            return;
        }
        City matched;
        try {
            int id = Integer.parseInt(raw);
            matched = graph.city(id).orElse(null);
        } catch (NumberFormatException ignored) {
            String q = raw.toLowerCase();
            matched = graph.cities().stream()
                    .filter(city -> city.name() != null && city.name().toLowerCase().contains(q))
                    .min(Comparator.comparingInt(City::id))
                    .orElse(null);
        }
        if (matched == null) {
            showError("未找到城市: " + raw);
            return;
        }
        selectCityInTable(matched.id());
        viewModel.setStatusText("已定位城市: " + matched.name() + "(" + matched.id() + ")");
    }

    @FXML
    private void onClearCities() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确认清空所有城市与线路吗？", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("确认清空城市");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        graph.cities().stream().map(City::id).toList().forEach(graph::removeCity);
        selectedCityId = null;
        refreshViews();
        clearAlgorithmHighlights();
        viewModel.setStatusText("已清空所有城市与线路。");
    }

    @FXML
    // 处理“添加线路”
    private void onAddEdge() {
        try {
            Integer fromId = tryParseInt(edgeFromField.getText(), "线路起点编号");
            Integer toId = tryParseInt(edgeToField.getText(), "线路终点编号");
            if (fromId == null || toId == null) {
                return;
            }
            graph.upsertEdge(fromId, toId);
            refreshViews();
            viewModel.setStatusText("线路已添加: " + fromId + " - " + toId);
            clearAlgorithmHighlights();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onDeleteEdge() {
        try {
            Edge selected = edgeListView.getSelectionModel().getSelectedItem();
            Integer fromId;
            Integer toId;
            if (selected != null) {
                fromId = selected.fromId();
                toId = selected.toId();
            } else {
                fromId = tryParseInt(edgeFromField.getText(), "线路起点编号");
                toId = tryParseInt(edgeToField.getText(), "线路终点编号");
                if (fromId == null || toId == null) {
                    return;
                }
            }
            if (!graph.hasEdge(fromId, toId)) {
                showError("线路不存在: " + fromId + " - " + toId);
                return;
            }
            graph.removeEdge(fromId, toId);
            edgeListView.getSelectionModel().clearSelection();
            refreshViews();
            viewModel.setStatusText("线路已删除: " + fromId + " - " + toId);
            clearAlgorithmHighlights();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    // 处理“清空路线”
    private void onClearEdges() {
        // 创建确认对话框
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确认清空所有路线吗？", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("确认清空路线"); // 设置对话框标题
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            // 若用户未确认，取消清空
            return;
        }
        // 先复制边快照，避免遍历中修改原集合
        graph.edges().stream().map(edge -> new Edge(edge.fromId(), edge.toId(), edge.length())).toList()
                .forEach(edge -> graph.removeEdge(edge.fromId(), edge.toId())); // 逐条删除所有边
        edgeListView.getSelectionModel().clearSelection(); // 清除列表选中状态，避免悬挂选中
        refreshViews();
        clearAlgorithmHighlights();
        viewModel.setStatusText("已清空所有路线。");
    }

    @FXML
    private void onCalculateShortestPath() {
        try {
            Integer sourceId = tryParseInt(shortestSourceField.getText(), "最短路源点ID");
            if (sourceId == null) {
                return;
            }
            ShortestPathResult result = shortestPathService.calculate(graph, sourceId);

            suggestedEdges = List.of();
            highlightedPathEdgeKeys.clear();
            overlayHighlightedEdges = List.of();
            StringBuilder sb = new StringBuilder();
            sb.append("【最短路径查询】源点: ").append(result.sourceId()).append("\n");
            for (ShortestPathResult.PathEntry entry : result.entries()) {
                if (!entry.reachable()) {
                    sb.append("到城市 ").append(entry.targetId()).append(": 不可达\n");
                    continue;
                }
                sb.append("到城市 ").append(entry.targetId())
                        .append(": 距离=").append(entry.distance())
                        .append(", 路径=").append(formatPath(entry.path()))
                        .append("\n");
            }

            ShortestPathResult.PathEntry nearestReachable = result.entries().stream()
                    .filter(ShortestPathResult.PathEntry::reachable)
                    .findFirst()
                    .orElse(null);
            if (nearestReachable != null) {
                highlightedPathEdgeKeys.addAll(toEdgeKeySet(nearestReachable.path()));
                sb.append("\n已在画布高亮最近可达城市路径: ").append(formatPath(nearestReachable.path()));
            }

            algorithmOutputArea.setText(sb.toString());
            renderGraph();
            viewModel.setStatusText("最短路径计算完成。");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onRunSteinerComparison() {
        try {
            SteinerResult result = steinerService.analyze(graph); // 执行 Steiner 近似分析
            highlightedPathEdgeKeys.clear(); // 清空旧路径高亮
            highlightedPathEdgeKeys.addAll(toEdgeKeySetFromEdges(result.improvedEdges())); // 设置改进边为高亮集合
            overlayHighlightedEdges = result.improvedEdges(); // 叠加高亮边用于渲染器加粗显示
            suggestedEdges = List.of(); // 清空补边建议列表避免样式冲突
            renderGraph(); // 触发画布重绘展示结果

            StringBuilder sb = new StringBuilder();
            sb.append("【Steiner 最小树近似对比】\n")
                    .append("终端城市数: ").append(result.terminalCount()).append("\n")
                    .append("MST 基线长度: ").append(result.baselineMstLength()).append("\n")
                    .append("Steiner 近似长度: ").append(result.improvedLength()).append("\n")
                    .append("改进长度: ").append(result.improvement()).append("\n");
            if (result.usedAuxiliaryPoint()) {
                sb.append("辅助点坐标: (")
                        .append(String.format("%.2f", result.auxiliaryX()))
                        .append(", ")
                        .append(String.format("%.2f", result.auxiliaryY()))
                        .append(")\n");
            } else {
                sb.append("本轮未找到可改进的辅助点。\n");
            }
            sb.append("已在画布高亮最短连通路线边数: ").append(result.improvedEdges().size()).append("\n");
            algorithmOutputArea.setText(sb.toString());
            viewModel.setStatusText("Steiner 对比完成。");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onRunEsteinBenchmark() {
        Path file = Path.of("data/estein250.txt");
        // 创建后台任务，返回最终报告文本
        Task<String> benchmarkTask = new Task<>() {
            @Override
            // 在后台线程中执行，不阻塞UI
            protected String call() throws Exception {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                List<SteinerBatchCase> cases = parseEsteinCases(lines);
                if (cases.isEmpty()) {
                    throw new IllegalArgumentException("未解析到测试用例。");
                }
                StringBuilder sb = new StringBuilder();
                sb.append("【Estein250 Steiner 双算法批量测试】\n")
                        .append("用例数量: ").append(cases.size()).append("\n\n");
                List<AlgorithmCaseMetric> metrics = new ArrayList<>();
                int idx = 1;
                for (SteinerBatchCase c : cases) {
                    Graph temp = new Graph();
                    for (City city : c.points) {
                        temp.upsertCity(city);
                    }
                    long t0 = System.nanoTime();
                    SteinerResult steiner = steinerService.analyze(temp);
                    long t1 = System.nanoTime();
                    int mstOnly = mstLengthOnly(c.points);
                    long t2 = System.nanoTime();

                    AlgorithmCaseMetric m = new AlgorithmCaseMetric(
                            idx,
                            steiner.baselineMstLength(),
                            steiner.improvedLength(),
                            mstOnly,
                            nanosToMillis(t1 - t0),
                            nanosToMillis(t2 - t1)
                    );
                    metrics.add(m);
                    sb.append(String.format(Locale.US,
                            "Case %02d: MST=%d | Steiner=%d | Delta=%d | Improve=%.2f%% | T_steiner=%.2fms | T_mst=%.2fms\n",
                            idx,
                            m.mstLength(),
                            m.steinerLength(),
                            (m.mstLength() - m.steinerLength()),
                            m.improvementRatePct(),
                            m.steinerMillis(),
                            m.mstMillis()));
                    idx++;
                    updateProgress(idx - 1, cases.size());
                }

                double avgMst = avg(metrics.stream().map(AlgorithmCaseMetric::mstLength).toList());
                double avgSteiner = avg(metrics.stream().map(AlgorithmCaseMetric::steinerLength).toList());
                double avgDelta = avg(metrics.stream().map(m -> m.mstLength() - m.steinerLength()).toList());
                double avgImprovePct = avg(metrics.stream().map(AlgorithmCaseMetric::improvementRatePct).toList());
                double avgSteinerMs = avg(metrics.stream().map(AlgorithmCaseMetric::steinerMillis).toList());
                double avgMstMs = avg(metrics.stream().map(AlgorithmCaseMetric::mstMillis).toList());
                double p95SteinerMs = percentile(metrics.stream().map(AlgorithmCaseMetric::steinerMillis).toList(), 95.0);
                double p95MstMs = percentile(metrics.stream().map(AlgorithmCaseMetric::mstMillis).toList(), 95.0);

                sb.append("\n===== 汇总指标 =====\n");
                sb.append(String.format(Locale.US, "平均长度: MST=%.2f, Steiner=%.2f, 平均改进值=%.2f\n", avgMst, avgSteiner, avgDelta));
                sb.append(String.format(Locale.US, "平均改进率: %.2f%%\n", avgImprovePct));
                sb.append(String.format(Locale.US, "平均耗时: Steiner=%.2fms, MST=%.2fms\n", avgSteinerMs, avgMstMs));
                sb.append(String.format(Locale.US, "P95耗时: Steiner=%.2fms, MST=%.2fms\n", p95SteinerMs, p95MstMs));
                return sb.toString();
            }
        };
        // 开始时更新状态
        benchmarkTask.setOnRunning(e -> {
            algorithmOutputArea.setText("Estein250 测试运行中，请稍候...");
            viewModel.setStatusText("Estein250 测试运行中...");
        });
        // 完成时回填结果
        benchmarkTask.setOnSucceeded(e -> {
            algorithmOutputArea.setText(benchmarkTask.getValue());
            viewModel.setStatusText("Estein250 测试完成。");
        });
        benchmarkTask.setOnFailed(e -> showError("Estein250 测试失败: " + benchmarkTask.getException().getMessage()));
        // 创建工作线程
        Thread worker = new Thread(benchmarkTask, "estein250-benchmark");
        // 设置守护线程，应用关闭时可自动退出
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void onAnalyzeConnectivity() {
        try {
            ConnectivityResult result = augmentConnectivityService.analyze(graph);
            suggestedEdges = result.suggestedEdges();
            highlightedPathEdgeKeys.clear();
            overlayHighlightedEdges = List.of();
            renderGraph();

            StringBuilder sb = new StringBuilder();
            sb.append("【连通性分析】\n")
                    .append("连通分量数量: ").append(result.componentCount()).append("\n")
                    .append("是否连通: ").append(result.connected() ? "是" : "否").append("\n");

            for (int i = 0; i < result.components().size(); i++) {
                sb.append("分量 ").append(i + 1).append(": ").append(result.components().get(i)).append("\n");
            }

            if (!result.suggestedEdges().isEmpty()) {
                sb.append("建议新增线路（最小总长度）:\n");
                for (Edge edge : result.suggestedEdges()) {
                    sb.append("  ").append(edge.fromId()).append(" <-> ").append(edge.toId())
                            .append(" (len=").append(edge.length()).append(")\n");
                }
                sb.append("新增总长度: ").append(result.suggestedTotalLength()).append("\n");
            }
            algorithmOutputArea.setText(sb.toString());
            viewModel.setStatusText("连通性分析完成。");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void onTraverseAllNoReturn() {
        runTraversal(false);
    }

    @FXML
    private void onTraverseAllReturn() {
        runTraversal(true);
    }

    @FXML
    private void onToggleRightPanel() {
        rightPanelHidden = !rightPanelHidden;
        if (rightPanelHidden) {
            if (mainSplitPane.getItems().contains(rightPanelBox)) {
                lastDividerPosition = mainSplitPane.getDividerPositions()[0];
                mainSplitPane.getItems().remove(rightPanelBox);
            }
        } else {
            if (!mainSplitPane.getItems().contains(rightPanelBox)) {
                mainSplitPane.getItems().add(rightPanelBox);
                mainSplitPane.setDividerPositions(Math.max(0.2, Math.min(0.9, lastDividerPosition)));
            }
        }
    }

    @FXML
    private void onZoomPresetChanged() {
        String value = zoomComboBox.getValue();
        if (value == null || !value.endsWith("%")) {
            return;
        }
        try {
            double targetScale = Double.parseDouble(value.substring(0, value.length() - 1)) / 100.0;
            interactionController.setScale(targetScale, topologyCanvas.getWidth() / 2.0, topologyCanvas.getHeight() / 2.0);
            renderGraph();
        } catch (NumberFormatException ignored) {
            showError("无效缩放比例: " + value);
        }
    }

    @FXML
    private void onGenerateBenchmarkData() {
        final int cityCount = 220;
        final int extraEdges = 420;
        Random random = new Random(123456789L);

        graph.cities().stream().map(City::id).toList().forEach(graph::removeCity);
        for (int id = 1; id <= cityCount; id++) {
            int x = random.nextInt(4500);
            int y = random.nextInt(3200);
            graph.upsertCity(new City(id, "C" + id, x, y, "auto-generated"));
        }
        for (int id = 1; id < cityCount; id++) {
            graph.upsertEdge(id, id + 1);
        }
        int added = 0;
        while (added < extraEdges) {
            int a = 1 + random.nextInt(cityCount);
            int b = 1 + random.nextInt(cityCount);
            if (a == b) {
                continue;
            }
            graph.upsertEdge(a, b);
            added++;
        }

        shortestSourceField.setText("1");
        clearAlgorithmHighlights();
        refreshViews();
        viewModel.setStatusText("已生成 220 城市压测数据。");
        algorithmOutputArea.setText("已生成压测数据：220 城市，" + graph.edges().size() + " 条线路（固定随机种子 123456789）。");
    }

    @FXML
    private void onExportReportAssets() {
        try {
            Path output = Path.of("data/report-assets.txt");
            Files.createDirectories(output.getParent());

            List<String> lines = new ArrayList<>();
            lines.add("CommNet Planner 报告素材导出");
            lines.add("城市数量: " + graph.cities().size());
            lines.add("线路数量: " + graph.edges().size());

            ConnectivityResult connectivity = augmentConnectivityService.analyze(graph);
            lines.add("连通分量: " + connectivity.componentCount());
            lines.add("连通性: " + (connectivity.connected() ? "连通" : "不连通"));
            lines.add("建议新增线路总长度: " + connectivity.suggestedTotalLength());

            if (!graph.cities().isEmpty()) {
                int sourceId = graph.cities().iterator().next().id();
                ShortestPathResult shortest = shortestPathService.calculate(graph, sourceId);
                long reachable = shortest.entries().stream().filter(ShortestPathResult.PathEntry::reachable).count();
                lines.add("样例最短路源点: " + sourceId);
                lines.add("样例可达城市数量: " + reachable);
            }

            SteinerResult steiner = steinerService.analyze(graph);
            lines.add("Steiner基线长度: " + steiner.baselineMstLength());
            lines.add("Steiner近似长度: " + steiner.improvedLength());
            lines.add("Steiner改进值: " + steiner.improvement());
            lines.add("");
            lines.add("===== 当前算法输出面板内容 =====");
            lines.add(algorithmOutputArea.getText() == null ? "" : algorithmOutputArea.getText());

            Files.write(output, lines, StandardCharsets.UTF_8);
            viewModel.setStatusText("报告素材已导出: " + output);
        } catch (IOException e) {
            showError("导出失败: " + e.getMessage());
        }
    }

    private void selectCityInTable(int cityId) {
        for (City city : cityItems) {
            if (city.id() == cityId) {
                cityTable.getSelectionModel().select(city);
                if (hasVisibleVerticalTableScrollBar()) {
                    cityTable.scrollTo(city);
                }
                return;
            }
        }
    }

    private boolean hasVisibleVerticalTableScrollBar() {
        for (javafx.scene.Node node : cityTable.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL && bar.isVisible()) {
                return true;
            }
        }
        return false;
    }

    private void runTraversal(boolean returnToSource) {
        try {
            Integer sourceId = tryParseInt(shortestSourceField.getText(), "遍历源点ID");
            if (sourceId == null) {
                return;
            }
            TraversalResult result = traversalService.solve(graph, sourceId, returnToSource);
            suggestedEdges = List.of();
            highlightedPathEdgeKeys.clear();
            overlayHighlightedEdges = List.of();
            highlightedPathEdgeKeys.addAll(toEdgeKeySet(result.expandedRoute()));
            renderGraph();

            StringBuilder sb = new StringBuilder();
            sb.append(returnToSource ? "【遍历全部城市并回源】\n" : "【遍历全部城市（不回源）】\n")
                    .append("求解方式: ").append(result.exactUsed() ? "精确(位压DP)" : "启发式(最近邻+2-opt)").append("\n")
                    .append("总距离: ").append(result.totalDistance()).append("\n")
                    .append("终端访问序列: ").append(formatPath(result.terminalVisitOrder())).append("\n")
                    .append("展开后路径: ").append(formatPath(result.expandedRoute())).append("\n");
            algorithmOutputArea.setText(sb.toString());
            viewModel.setStatusText("全城市遍历求解完成。");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private double percentile(List<Double> values, double p) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int idx = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return sorted.get(idx);
    }

    private double avg(Collection<? extends Number> numbers) {
        if (numbers.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Number number : numbers) {
            sum += number.doubleValue();
        }
        return sum / numbers.size();
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private int mstLengthOnly(List<City> points) {
        int n = points.size();
        if (n < 2) {
            return 0;
        }
        boolean[] used = new boolean[n];
        double[] minDist = new double[n];
        for (int i = 0; i < n; i++) {
            minDist[i] = Double.POSITIVE_INFINITY;
        }
        minDist[0] = 0;
        int total = 0;
        for (int step = 0; step < n; step++) {
            int u = -1;
            for (int i = 0; i < n; i++) {
                if (!used[i] && (u == -1 || minDist[i] < minDist[u])) {
                    u = i;
                }
            }
            used[u] = true;
            total += (int) Math.round(minDist[u]);
            for (int v = 0; v < n; v++) {
                if (used[v]) {
                    continue;
                }
                double d = Math.hypot(points.get(u).x() - points.get(v).x(), points.get(u).y() - points.get(v).y());
                if (d < minDist[v]) {
                    minDist[v] = d;
                }
            }
        }
        return total;
    }

    private record AlgorithmCaseMetric(
            int caseIndex,
            int baselineLength,
            int steinerLength,
            int mstLength,
            double steinerMillis,
            double mstMillis
    ) {
        private double improvementRatePct() {
            if (mstLength == 0) {
                return 0;
            }
            return (mstLength - steinerLength) * 100.0 / mstLength;
        }
    }

    private List<SteinerBatchCase> parseEsteinCases(List<String> lines) {
        List<String> tokens = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                tokens.addAll(List.of(trimmed.split("\\s+")));
            }
        }
        if (tokens.size() < 2) {
            return List.of();
        }
        int caseCount = Integer.parseInt(tokens.get(0));
        int pointsPerCase = Integer.parseInt(tokens.get(1));
        int cursor = 2;
        List<SteinerBatchCase> cases = new ArrayList<>();
        for (int c = 0; c < caseCount; c++) {
            List<City> points = new ArrayList<>();
            for (int i = 0; i < pointsPerCase; i++) {
                if (cursor + 1 >= tokens.size()) {
                    throw new IllegalArgumentException("文件格式不完整，读取到第 " + (c + 1) + " 组。");
                }
                double x = Double.parseDouble(tokens.get(cursor++));
                double y = Double.parseDouble(tokens.get(cursor++));
                int px = (int) Math.round(x * 10000);
                int py = (int) Math.round(y * 10000);
                points.add(new City(i + 1, "P" + (i + 1), px, py, "estein"));
            }
            cases.add(new SteinerBatchCase(points));
        }
        return cases;
    }

    private record SteinerBatchCase(List<City> points) {
    }

    private Set<String> toEdgeKeySetFromEdges(List<Edge> edges) {
        Set<String> edgeKeys = new HashSet<>();
        for (Edge edge : edges) {
            edgeKeys.add(Graph.normalizedEdgeKey(edge.fromId(), edge.toId()));
        }
        return edgeKeys;
    }

    private Set<String> toEdgeKeySet(List<Integer> path) {
        Set<String> edgeKeys = new HashSet<>();
        for (int i = 1; i < path.size(); i++) {
            edgeKeys.add(Graph.normalizedEdgeKey(path.get(i - 1), path.get(i)));
        }
        return edgeKeys;
    }

    private String formatPath(List<Integer> path) {
        return String.join(" -> ", path.stream().map(String::valueOf).toList());
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

    private record Point(double x, double y) {
    }

    // 把画布坐标反算为世界坐标
    private Point canvasToWorld(double canvasX, double canvasY) {
        double sceneX = (canvasX - interactionController.offsetX()) / interactionController.scale();
        double sceneY = (canvasY - interactionController.offsetY()) / interactionController.scale();
        double worldX = (sceneX - viewCenteredLeft) / viewFitScale + worldMinX;
        double worldY = worldMaxY - (sceneY - viewCenteredTop) / viewFitScale;
        return new Point(worldX, worldY);
    }

    private void showError(String message) {
        viewModel.setStatusText("错误: " + message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("操作失败");
        alert.showAndWait();
    }
}
