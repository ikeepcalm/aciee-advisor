package ro.aciee.advisor.domain.report.model.source;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum ReportFormat {
    CSV("text/csv; charset=UTF-8"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PDF("application/pdf");

    private final String mediaType;

    public static ReportFormat of(String key) {
        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
    }
}
