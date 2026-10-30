package com.devopsflow.task;

public record Task(long id, String title, String description, TaskStatus status) {
}
