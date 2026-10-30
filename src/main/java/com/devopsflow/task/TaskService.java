package com.devopsflow.task;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
    private final InMemoryTaskRepository repository;

    public TaskService(InMemoryTaskRepository repository) {
        this.repository = repository;
    }

    public Task create(CreateTaskRequest request) {
        TaskStatus status = request.status() == null ? TaskStatus.TODO : request.status();
        return repository.create(request.title().strip(), request.description(), status);
    }

    public List<Task> findAll() {
        return repository.findAll();
    }

    public Task findById(long id) {
        return repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    public Task update(long id, UpdateTaskRequest request) {
        return repository.update(id, request.title().strip(), request.description(), request.status())
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    public void delete(long id) {
        if (!repository.delete(id)) {
            throw new TaskNotFoundException(id);
        }
    }
}
