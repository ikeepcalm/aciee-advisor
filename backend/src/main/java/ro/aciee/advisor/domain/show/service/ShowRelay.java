package ro.aciee.advisor.domain.show.service;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@Service
@Profile("show")
public class ShowRelay {

    private static final int MAX_ROOMS = 20;
    private static final int MAX_LISTENERS = 6;
    private static final long LISTEN_MILLIS = Duration.ofHours(2).toMillis();

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public SseEmitter listen(String room) {
        Room target = room(room);
        if (target.listeners.size() >= MAX_LISTENERS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }
        SseEmitter emitter = new SseEmitter(LISTEN_MILLIS);
        target.listeners.add(emitter);
        emitter.onCompletion(() -> target.listeners.remove(emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> target.listeners.remove(emitter));
        if (target.last != null) {
            send(target, emitter, target.last);
        }
        return emitter;
    }

    public void publish(String room, String message) {
        Room target = room(room);
        target.last = message;
        target.listeners.forEach(emitter -> send(target, emitter, message));
    }

    @Scheduled(fixedDelay = 20, timeUnit = TimeUnit.SECONDS)
    public void heartbeat() {
        rooms.values().forEach(room -> room.listeners.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                room.listeners.remove(emitter);
            }
        }));
        rooms.values().removeIf(room -> room.listeners.isEmpty() && room.last == null);
    }

    private Room room(String name) {
        if (!rooms.containsKey(name) && rooms.size() >= MAX_ROOMS) {
            rooms.values().removeIf(room -> room.listeners.isEmpty());
            if (rooms.size() >= MAX_ROOMS) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            }
        }
        return rooms.computeIfAbsent(name, key -> new Room());
    }

    private void send(Room room, SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().data(message));
        } catch (IOException | IllegalStateException e) {
            room.listeners.remove(emitter);
        }
    }

    private static class Room {
        private final List<SseEmitter> listeners = new CopyOnWriteArrayList<>();
        private volatile String last;
    }
}
