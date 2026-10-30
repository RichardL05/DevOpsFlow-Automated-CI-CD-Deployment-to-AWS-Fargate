package com.devopsflow.task;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskServiceTest {
    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(new InMemoryTaskRepository());
    }

    @Test
    void createsTasksWithDefaultStatusAndGeneratedIds() {
        Task first = service.create(new CreateTaskRequest("  Learn Docker  ", null, null));
        Task second = service.create(new CreateTaskRequest("Learn Jenkins", "Build the API", TaskStatus.IN_PROGRESS));
        assertTrue(first.id() > 0);
        assertNotEquals(first.id(), second.id());
        assertEquals("Learn Docker", first.title());
        assertNull(first.description());
        assertEquals(TaskStatus.TODO, first.status());
        assertEquals(TaskStatus.IN_PROGRESS, second.status());
        assertEquals(List.of(first, second), service.findAll());
    }

    @Test
    void updatePreservesIdAndDeleteMakesTheTaskUnavailable() {
        Task task = service.create(new CreateTaskRequest("Before", "Old description", null));
        Task updated = service.update(task.id(), new UpdateTaskRequest(" After ", null, TaskStatus.DONE));
        assertEquals(task.id(), updated.id());
        assertEquals("After", updated.title());
        assertNull(updated.description());
        assertEquals(TaskStatus.DONE, service.findById(task.id()).status());
        service.delete(task.id());
        assertTrue(service.findAll().isEmpty());
        assertThrows(TaskNotFoundException.class, () -> service.findById(task.id()));
        assertThrows(TaskNotFoundException.class,
                () -> service.update(task.id(), new UpdateTaskRequest("Recreate", null, TaskStatus.TODO)));
        assertThrows(TaskNotFoundException.class, () -> service.delete(task.id()));
    }

    @Test
    void aFreshRepositoryHasNoPreviouslyStoredTasks() {
        service.create(new CreateTaskRequest("Temporary", null, null));
        TaskService freshInstance = new TaskService(new InMemoryTaskRepository());
        assertTrue(freshInstance.findAll().isEmpty());
    }

    @Test
    void concurrentCreatesHaveUniqueIdsAndRetainEveryTask() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Long>> creates = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                int index = i;
                creates.add(() -> service.create(new CreateTaskRequest("Task " + index, null, null)).id());
            }
            var ids = new HashSet<Long>();
            for (var future : executor.invokeAll(creates)) {
                ids.add(future.get());
            }
            assertEquals(100, ids.size());
            assertEquals(100, service.findAll().size());
            assertEquals(service.findAll().stream().map(Task::id).sorted().toList(),
                    service.findAll().stream().map(Task::id).toList());
        } finally {
            executor.shutdownNow();
        }
    }
}
