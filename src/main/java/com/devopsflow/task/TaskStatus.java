package com.devopsflow.task;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static TaskStatus fromJson(Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Status must be TODO, IN_PROGRESS, or DONE");
        }
        return TaskStatus.valueOf(text);
    }
}
