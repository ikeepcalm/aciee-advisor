package ro.aciee.advisor.domain.catalog.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.catalog.model.CatalogItem;
import ro.aciee.advisor.domain.catalog.model.CoursePage;
import ro.aciee.advisor.domain.catalog.model.Lookups;
import ro.aciee.advisor.domain.catalog.model.ProfessorListItem;
import ro.aciee.advisor.domain.catalog.model.ProfessorPage;
import ro.aciee.advisor.domain.catalog.model.Specialization;
import ro.aciee.advisor.domain.catalog.model.Stats;
import ro.aciee.advisor.domain.catalog.service.CatalogService;

import java.time.Duration;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/api/catalog/stats")
    public ResponseEntity<Stats> stats() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(catalogService.stats());
    }

    @GetMapping("/api/catalog/lookups")
    public Lookups lookups() {
        return catalogService.lookups();
    }

    @GetMapping("/api/catalog/specializations")
    public List<Specialization> specializations() {
        return catalogService.specializations();
    }

    @GetMapping("/api/catalog/plans/{planId}/years/{studyYear}")
    public List<CatalogItem> items(@PathVariable int planId, @PathVariable int studyYear) {
        return catalogService.items(planId, studyYear);
    }

    @GetMapping("/api/catalog/courses/{itemId}")
    public CoursePage course(@PathVariable int itemId, @RequestParam(required = false) Integer year) {
        return catalogService.course(itemId, year);
    }

    @GetMapping("/api/catalog/professors")
    public List<ProfessorListItem> professors() {
        return catalogService.professors();
    }

    @GetMapping("/api/professors/{slug}")
    public ProfessorPage professor(@PathVariable String slug) {
        return catalogService.professor(slug);
    }
}
