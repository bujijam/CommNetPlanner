package org.example.model;

// record：专门用来存数据且不可变的类，编译器自动生成构造器和访问器等方法
public record City(int id, String name, int x, int y, String description) {
}
