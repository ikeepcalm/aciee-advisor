package ro.aciee.advisor.config;

import org.postgresql.util.PSQLException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DatabaseErrorAdvice {

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handle(DataAccessException e) {
        if (!(NestedExceptionUtils.getMostSpecificCause(e) instanceof PSQLException psql) || psql.getServerErrorMessage() == null) {
            throw e;
        }
        var state = psql.getSQLState();
        var problem = switch (state) {
            case "23503" -> ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "foreign_key");
            case "23505", "23P01" -> ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "duplicate");
            case "23514" -> ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "check_failed");
            case "23502" -> ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "required");
            case "57014" -> ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "timeout");
            default -> state.startsWith("22") ? ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "invalid_value") : null;
        };
        if (problem == null) {
            throw e;
        }
        var message = psql.getServerErrorMessage();
        problem.setProperty("constraint", message.getConstraint());
        problem.setProperty("column", message.getColumn());
        problem.setProperty("table", message.getTable());
        return problem;
    }
}
