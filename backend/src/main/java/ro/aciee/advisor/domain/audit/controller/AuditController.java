package ro.aciee.advisor.domain.audit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.aciee.advisor.domain.audit.model.AuditEntry;
import ro.aciee.advisor.domain.audit.service.AuditService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public List<AuditEntry> history(@RequestParam(required = false) String table, @RequestParam(required = false) Integer rowId,
                                    @RequestParam(required = false) Long before) {
        return auditService.history(table, rowId, before);
    }
}
