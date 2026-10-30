package com.devopsflow.task;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTaskRepository {
    private final ConcurrentMap<Long, Task> tasks = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public Task create(String title, String description, TaskStatus status) {
        long id = sequence.incrementAndGet();
        Task task = new Task(id, title, description, status);
        tasks.put(id, task);
        return task;
    }

    public List<Task> findAll() {
        return tasks.values().stream()
                .sorted(Comparator.comparingLong(Task::id))
                .toList();
    }

    public Optional<Task> findById(long id) {
        return Optional.ofNullable(tasks.get(id));
    }

    public Optional<Task> update(long id, String title, String description, TaskStatus status) {
        // An update must not recreate a task that another request has deleted.
        return Optional.ofNullable(tasks.computeIfPresent(id,
                (key, previous) -> new Task(key, title, description, status)));
    }

    public boolean delete(long id) {
        return tasks.remove(id) != null;
    }
}
