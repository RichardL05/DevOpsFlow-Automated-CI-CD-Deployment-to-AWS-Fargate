package com.devopsflow.task;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(long id) {
        super("Task not found: " + id);
    }
}
