package ro.aciee.advisor.domain.preview.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.preview.model.PreviewCard;
import ro.aciee.advisor.domain.preview.service.PreviewCardRenderer;
import ro.aciee.advisor.domain.preview.service.PreviewService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class PreviewController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final PreviewService previewService;
    private final PreviewCardRenderer renderer;

    @GetMapping("/og/img/courses/{itemId}.png")
    public ResponseEntity<byte[]> courseImage(@PathVariable int itemId) {
        return image(previewService.courseCard(itemId));
    }

    @GetMapping("/og/img/professors/{slug}.png")
    public ResponseEntity<byte[]> professorImage(@PathVariable String slug) {
        return image(previewService.professorCard(slug));
    }

    @GetMapping("/og/img/timetable/{group}.png")
    public ResponseEntity<byte[]> timetableImage(@PathVariable String group) {
        return image(previewService.timetableCard("/timetable", group));
    }

    @GetMapping("/og/img/pages/{page}.png")
    public ResponseEntity<byte[]> pageImage(@PathVariable String page) {
        return image(previewService.cardForPath("/" + page));
    }

    @GetMapping("/og/img/default.png")
    public ResponseEntity<byte[]> defaultImage() {
        return image(previewService.cardForPath("/"));
    }

    @GetMapping("/og/**")
    public ResponseEntity<String> page(HttpServletRequest request) {
        String path = request.getRequestURI().substring("/og".length());
        return ResponseEntity.ok()
                .cacheControl(CACHE)
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(previewService.html(previewService.cardForPath(path.isEmpty() ? "/" : path, request.getParameter("g"))));
    }

    private ResponseEntity<byte[]> image(PreviewCard card) {
        return ResponseEntity.ok().cacheControl(CACHE).contentType(MediaType.IMAGE_PNG).body(renderer.render(card));
    }
}
