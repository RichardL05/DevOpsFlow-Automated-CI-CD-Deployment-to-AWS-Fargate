package com.devopsflow.task;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Task> create(@Valid @RequestBody CreateTaskRequest request) {
        Task task = service.create(request);
        return ResponseEntity.created(URI.create("/api/tasks/" + task.id())).body(task);
    }

    @GetMapping
    public List<Task> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Task findById(@PathVariable("id") long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public Task update(@PathVariable("id") long id, @Valid @RequestBody UpdateTaskRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
