package com.devopsflow.task;

import jakarta.validation.constraints.NotBlank;

public record CreateTaskRequest(
        @NotBlank(message = "Title must not be blank") String title,
        String description,
        TaskStatus status) {
}
