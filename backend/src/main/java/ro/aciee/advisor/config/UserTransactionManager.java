package ro.aciee.advisor.config;

import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Primary
@Component("transactionManager")
public class UserTransactionManager extends JdbcTransactionManager {

    public UserTransactionManager(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected void prepareTransactionalConnection(Connection connection, TransactionDefinition definition) throws SQLException {
        super.prepareTransactionalConnection(connection, definition);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Integer userId)) {
            return;
        }
        try (var statement = connection.prepareStatement("SELECT set_config('app.user_id', ?, true)")) {
            statement.setString(1, userId.toString());
            statement.execute();
        }
    }
}
