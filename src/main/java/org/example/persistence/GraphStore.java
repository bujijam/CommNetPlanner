package org.example.persistence;

import org.example.graph.Graph;
import org.example.model.City;
import org.example.model.Edge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class GraphStore {
    // 表头
    private static final String CITIES_HEADER = "id,name,x,y,description";
    private static final String EDGES_HEADER = "fromId,toId";

    private final Path citiesFile;
    private final Path edgesFile;

    public GraphStore(Path citiesFile, Path edgesFile){
        this.citiesFile = Objects.requireNonNull(citiesFile, "城市文件路径不能为空");
        this.edgesFile = Objects.requireNonNull(edgesFile, "边文件路径不能为空");
    }

    public Path citiesFile() {
        return citiesFile;
    }

    public Path edgesFile() {
        return edgesFile;
    }

    // 从csv文件读取并构建图
    public Graph load(){
        Graph graph = new Graph();
        if(Files.exists(citiesFile)){
            List<String> cityLines = readAllLines(citiesFile);
            // i = 1跳过表头
            for(int i = 1; i < cityLines.size(); i++){
                String line = cityLines.get(i).trim();  // 去掉首尾空白行
                if(line.isEmpty()){
                    // 跳过空行继续处理
                    continue;
                }
                // 按csv规则切分字段
                List<String> fields = parseCsvLine(line);
                // 正常的城市记录应该有5个字段（id，名字，x，y，描述）
                if(fields.size() < 5){
                    throw new IllegalArgumentException("非法的城市记录：" + line + "，位于第" + (i+1) + "行");
                }
                int id = parseInt(fields.get(0), "city.id", i+1);
                String name = fields.get(1);
                int x = parseInt(fields.get(2), "city.x", i+1);
                int y = parseInt(fields.get(3), "city.y", i+1);
                String description = fields.get(4);
                graph.upsertCity(new City(id, name, x, y, description));
            }
        }
        if(Files.exists(edgesFile)){
            List<String> edgeLines = readAllLines(edgesFile);
            for(int i = 1; i < edgeLines.size(); i++){
                String line = edgeLines.get(i).trim();
                if(line.isEmpty()) continue;
                List<String> fields = parseCsvLine(line);
                if(fields.size() < 2){
                    throw new IllegalArgumentException("非法的边记录：" + line + "，位于第" + (i+1) + "行");
                }
                int fromId = parseInt(fields.get(0), "edge.fromId", i+1);
                int toId = parseInt(fields.get(1), "edge.toId", i+1);
                graph.upsertEdge(fromId, toId);
            }
        }
        return graph;
    }
    
    public void save(Graph graph){
        Objects.requireNonNull(graph, "图不能为空");
        // 若目录不存在先创建
        createParentDirectoryIfNeeded(citiesFile);
        createParentDirectoryIfNeeded(edgesFile);
        
        List<String> cityLines = new ArrayList<>();
        // 添加表头
        cityLines.add(CITIES_HEADER);
        // 获取所有城市并转为流处理
        graph.cities().stream()
                // 按id升序排序
                .sorted(Comparator.comparingInt(City::id))
                // 组装一行csv，写入列表
                .forEach(city -> cityLines.add(escape(city.id()) + "," + escape(city.name()) + "," + escape(city.x())
                        + "," + escape(city.y()) + "," + escape(city.description())));
        writeAllLines(citiesFile, cityLines);

        List<String> edgeLines = new ArrayList<>();
        edgeLines.add(EDGES_HEADER);
        graph.edges().stream()
                .sorted(Comparator.comparingInt(Edge::fromId).thenComparingInt(Edge::toId))
                .forEach(edge -> edgeLines.add(escape(edge.fromId()) + "," + escape(edge.toId())));
        writeAllLines(edgesFile, edgeLines);
    }

    // 将列表中的行写入文件
    private static void writeAllLines(Path path, List<String> lines) {
        try {
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("写入文件失败：" + path, e);
        }
    }

    // 将任意对象的值转义为符合CSV格式的字段内容
    private static String escape(Object value) {
        String raw = String.valueOf(value);
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n")) {
            // 用双引号包裹整个字符串，并将字符串内部的每个双引号替换为两个双引号（""），实现转义。
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
        return raw;
    }

    // 创建父目录
    private static void createParentDirectoryIfNeeded(Path filePath) {
        Path parent = filePath.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new IllegalStateException("创建目录失败: " + parent, e);
        }
    }

    // 字符串转数字
    private static int parseInt(String value, String fieldName, int lineNo) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("在行" + lineNo + "发现非法的" + fieldName + "字段：" + value, e);
        }
    }

    // 解析单行csv
    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        // 标记是否位于引号字段内
        boolean inQuotes = false;
        for(int i = 0; i < line.length(); i++){
            char ch = line.charAt(i);
            if(ch == '"'){
                // 如果在引号内，而且后面还是引号，那么就是转义的引号
                if(inQuotes && i+1 < line.length() && line.charAt(i+1) == '"'){
                    current.append('"');    // 真实的引号
                    i++;    // 跳过下一个引号
                } else{
                    inQuotes = !inQuotes;
                }
                continue;
            }
            // 不在引号内的逗号才是分隔符
            if(ch == ',' && !inQuotes){
                fields.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        // 把最后一个字段加入列表
        fields.add(current.toString());
        return fields;
    }

    // 读取文件
    private static List<String> readAllLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取文件失败：" + path, e);
        }
    }
}
