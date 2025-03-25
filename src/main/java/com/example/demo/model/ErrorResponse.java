package com.example.demo.model;

import lombok.Data;

@Data
public class ErrorResponse {
    private boolean success = false;
    private String error;
    private int statusCode;
    
    public ErrorResponse(String error, int statusCode) {
        this.error = error;
        this.statusCode = statusCode;
    }
}