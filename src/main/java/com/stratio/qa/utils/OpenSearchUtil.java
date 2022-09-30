package com.stratio.qa.utils;

public enum OpenSearchUtil {

    INSTANCE;

    private final OpenSearchUtils cUtils = new OpenSearchUtils();

    public OpenSearchUtils getOpenSearchUtils() {
        return cUtils;
    }
}
