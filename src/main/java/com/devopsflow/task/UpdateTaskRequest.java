package com.devopsflow.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskRequest(
        @NotBlank(message = "Title must not be blank") String title,
        String description,
        @NotNull(message = "Status is required") TaskStatus status) {
}
