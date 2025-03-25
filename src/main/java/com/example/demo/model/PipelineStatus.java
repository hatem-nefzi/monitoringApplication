package com.example.demo.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PipelineStatus {
    private String pipelineName;
    private String status; // RUNNING, SUCCESS, FAILURE, ABORTED
    private LocalDateTime lastRunTime;
    private String duration;
    private String triggeredBy;
    private String commitId;
    private String branch;
    private String buildUrl;
}