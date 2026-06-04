package com.example.lightweight.repository;

public interface ProcessedEventRepository {
    boolean existsByEventId(String eventId);

    void markProcessed(String eventId);
}
