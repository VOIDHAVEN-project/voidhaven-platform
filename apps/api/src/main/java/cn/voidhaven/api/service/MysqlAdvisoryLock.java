package cn.voidhaven.api.service;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Component
public class MysqlAdvisoryLock {
    private final DataSource dataSource;

    public MysqlAdvisoryLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean runExclusive(String name, Runnable work) {
        try (Connection connection = dataSource.getConnection()) {
            if (!acquire(connection, name)) return false;
            try {
                work.run();
                return true;
            } finally {
                release(connection, name);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Unable to run exclusive MySQL task " + name, error);
        }
    }

    private boolean acquire(Connection connection, String name) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("select get_lock(?, 0)")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }

    private void release(Connection connection, String name) {
        try (PreparedStatement statement = connection.prepareStatement("select release_lock(?)")) {
            statement.setString(1, name);
            statement.execute();
        } catch (Exception ignored) {
            // Connection close releases connection-scoped advisory locks.
        }
    }
}
