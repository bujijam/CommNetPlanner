package org.example.ui.viewmodel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class MainViewModel {
    // StringProperty可以监听值的变化，支持与其他属性进行绑定
    private final StringProperty statusText = new SimpleStringProperty("就位");

    // getter
    public StringProperty statusTextProperty() {
        return statusText;
    }

    // setter
    public void setStatusText(String text) {
        statusText.set(text);
    }
}
