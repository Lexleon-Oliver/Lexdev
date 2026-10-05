package db.migration;

import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V12__create_refresh_token_expiry_index
        extends BaseJavaMigration {

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                CREATE INDEX CONCURRENTLY
                idx_tb_refresh_tokens_expiry_date_id
                ON public.tb_refresh_tokens (expiry_date ASC, id ASC)
                """);
        }
    }
}
