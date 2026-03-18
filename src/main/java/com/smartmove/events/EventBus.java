package com.smartmove.events;

import com.smartmove.config.LoggerFactory;
import java.util.logging.Logger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central Event Bus for decoupling system components.
 * Implements Event-Driven Architecture pattern.
 * 
 * Benefits:
 * - Loose coupling between publishers and subscribers
 * - Easy to add new event handlers without modifying existing code
 * - Testability - can mock event handlers
 * - Flexibility - runtime subscription/unsubscription
 */
public class EventBus {
    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);

    private static final EventBus INSTANCE = new EventBus();
    
    private final Map<Class<? extends Event>, CopyOnWriteArrayList<Consumer<? extends Event>>> subscribers;
    
    private EventBus() {
        this.subscribers = new ConcurrentHashMap<>();
    }
    
    public static EventBus getInstance() {
        return INSTANCE;
    }
    
    /**
     * Subscribe to events of specific type.
     * Thread-safe - can be called from multiple threads.
     */
    public <T extends Event> void subscribe(Class<T> eventType, Consumer<T> handler) {
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                   .add(handler);
    }
    
    /**
     * Unsubscribe from events.
     */
    public <T extends Event> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        CopyOnWriteArrayList<Consumer<? extends Event>> handlers = subscribers.get(eventType);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }
    
    /**
     * Publish event to all subscribers.
     * Events are delivered synchronously in the calling thread.
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> void publish(T event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        
        CopyOnWriteArrayList<Consumer<? extends Event>> handlers = subscribers.get(event.getClass());
        if (handlers != null) {
            for (Consumer<? extends Event> handler : handlers) {
                try {
                    ((Consumer<T>) handler).accept(event);
                } catch (Exception e) {
                    logger.severe("[EventBus] Handler error for %s".formatted(event.getClass().getSimpleName()) + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Clear all subscriptions (for testing).
     */
    public void clear() {
        subscribers.clear();
    }
    
    /**
     * Get count of subscribers for event type (for testing).
     */
    public int getSubscriberCount(Class<? extends Event> eventType) {
        CopyOnWriteArrayList<Consumer<? extends Event>> handlers = subscribers.get(eventType);
        return handlers != null ? handlers.size() : 0;
    }
}
