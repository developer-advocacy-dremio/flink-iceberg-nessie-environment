package com.dremio_developer_advocacy;

public class ExampleData {
    private Long id;
    private String data;

    public ExampleData() {
    }

    public ExampleData(Long id, String data) {
        this.id = id;
        this.data = data;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
