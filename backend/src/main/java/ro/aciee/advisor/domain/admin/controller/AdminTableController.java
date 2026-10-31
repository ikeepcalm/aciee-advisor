package ro.aciee.advisor.domain.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.admin.model.source.AdminTable;
import ro.aciee.advisor.domain.admin.service.AdminTableService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/tables")
@RequiredArgsConstructor
public class AdminTableController {

    private final AdminTableService adminTableService;

    @GetMapping
    public List<AdminTable> tables() {
        return Arrays.stream(AdminTable.values()).filter(AdminTable::isEditable).toList();
    }

    @GetMapping("/{table}")
    public List<Map<String, Object>> list(@PathVariable String table, @RequestParam(required = false) String search,
                                          @RequestParam(required = false) String after) {
        return adminTableService.list(AdminTable.of(table), search, after);
    }

    @GetMapping("/{table}/options")
    public List<Map<String, Object>> options(@PathVariable String table, @RequestParam(required = false) String search) {
        return adminTableService.options(AdminTable.of(table), search);
    }

    @PostMapping("/{table}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> create(@PathVariable String table, @RequestBody Map<String, String> values) {
        return Map.of("id", adminTableService.create(AdminTable.of(table), values));
    }

    @PutMapping("/{table}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable String table, @PathVariable String id, @RequestBody Map<String, String> values) {
        adminTableService.update(AdminTable.of(table), id, values);
    }

    @DeleteMapping("/{table}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String table, @PathVariable String id) {
        adminTableService.delete(AdminTable.of(table), id);
    }

    @PostMapping("/professor/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveProfessor(@PathVariable int id) {
        adminTableService.archiveProfessor(id);
    }

    @PostMapping("/study_group/{id}/offerings")
    public Map<String, Integer> createOfferings(@PathVariable int id) {
        return Map.of("created", adminTableService.createOfferings(id));
    }
}
