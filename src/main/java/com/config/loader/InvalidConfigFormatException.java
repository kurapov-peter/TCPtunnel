package com.config.loader;

public class InvalidConfigFormatException extends Exception {
    private final String config;
    InvalidConfigFormatException(String config) {
        this.config = config;
    }

    public String getJsonString() {
        return config;
    }
}