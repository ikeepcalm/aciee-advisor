package ro.aciee.advisor.domain.show.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ro.aciee.advisor.domain.show.service.ShowRelay;

@RestController
@Profile("show")
@RequiredArgsConstructor
public class ShowController {

    private static final String ROOM = "[a-z0-9-]{8,40}";

    private final ShowRelay showRelay;

    @GetMapping(path = "/api/show/{room}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable @Pattern(regexp = ROOM) String room, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return showRelay.listen(room);
    }

    @PostMapping(path = "/api/show/{room}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publish(@PathVariable @Pattern(regexp = ROOM) String room, @RequestBody @Size(max = 2000) String message) {
        showRelay.publish(room, message);
    }
}
