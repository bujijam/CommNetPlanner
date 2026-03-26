package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;  // 舞台：最外层窗口
import java.io.IOException; // 处理FXML加载异常

public class CommNetApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(CommNetApp.class.getResource("/org/example/ui/MainView.fxml"));
        // 读取 FXML 得到根节点，再构造指定尺寸场景
        Scene scene = new Scene(loader.load(), 1280, 800);
        stage.setTitle("通信网络规划器 - CommNetPlanner");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        Application.launch(args);
    }
}
