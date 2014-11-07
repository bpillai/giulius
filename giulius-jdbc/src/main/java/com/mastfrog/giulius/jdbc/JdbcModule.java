package com.mastfrog.giulius.jdbc;

import com.google.inject.AbstractModule;
import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;
import java.sql.Connection;

/**
 * Binds Connection and related classes using BoneCP for a connection pool, and
 * gettings settings from Settings.  Constants on this class define settings
 * keys and default values that correspond to BoneCPConfig setters.
 *
 * @author Tim Boudreau
 */
public class JdbcModule extends AbstractModule {

    public static final String JDBC_URL = "jdbc.url";
    public static final String JDBC_USER = "jdbc.user";
    public static final String JDBC_PASSWORD = "jdbc.password";
    public static final String PARTITION_COUNT = "jdbc.partition.count";
    public static final String MIN_CONNECTIONS_PER_PARTITION = "jdbc.min.connections.per.partition";
    public static final String CONNECTION_TIMEOUT_MINUTES = "jdbc.connection.timeout.minutes";
    public static final String MAX_CONNECTIONS_PER_PARTITION = "jdbc.max.connections.per.partition";
    public static final String MAX_CONNECTION_AGE_MINUTES = "jdbc.max.connection.age.minutes";
    public static final String CLOSE_OPEN_STATEMENTS = "jdbc.close.open.statements";
    public static final String CLOSE_CONNECTION_WATCH = "jdbc.close.connection.watch";
    public static final String ACQUIRE_RETRY_ATTEMPTS = "jdbc.acquire.retry.attempts";
    public static final String READ_ONLY = "jdbc.read.only";
    public static final String IDLE_MAX_AGE_SECONDS = "jdbc.idle.max.age";
    public static final String HINT_SCROLLABLE_CURSORS = "jdbc.scrollable.cursors";
    public static final String POSTGRES_LOG_UNCLOSED_CONNECTIONS = "jdbc.postgres.log.unclosed";
    public static final String POSTGRES_LOGIN_TIMEOUT = "jdbc.postgres.login.timeout";
    public static final String POSTGRES_SOCKET_TIMEOUT = "jdbc.postgres.socket.timeout";
    public static final String POSTGRES_SEND_BUFFER_SIZE = "jdbc.postgres.send.buffer.size";
    public static final String POSTGRES_RECEIVE_BUFFER_SIZE = "jdbc.postgres.receive.buffer.size";
    public static final String POSTGRES_KEEP_ALIVE = "jdbc.postgres.keep.alive";

    public static final boolean DEFAULT_CLOSE_OPEN_STATEMENTS = true;
    public static final boolean DEFAULT_CLOSE_CONNECTION_WATCH = true;
    public static final boolean DEFAULT_READ_ONLY = false;
    public static final int DEFAULT_ACQUIRE_RETRY_ATTEMPTS = 2;
    public static final String DEFAULT_JDBC_URL = "jdbc:postgresql://localhost:5432/postgres";
    public static final int DEFAULT_PARTITION_COUNT = 1;
    public static final int DEFAULT_MIN_CONNECTIONS_PER_PARTITION = 1;
    public static final int DEFAULT_MAX_CONNECTIONS_PER_PARTITION = 6;
    public static final int DEFAULT_CONNECTION_TIMEOUT_MINUTES = 3;
    public static final String DEFAULT_JDBC_USER = "postgres";

    @Override
    protected void configure() {
        bind(BoneCPConfig.class).toProvider(ConnectionPoolConfigProvider.class);
        bind(BoneCP.class).toProvider(ConnectionPoolProvider.class);
        bind(BoneCPConfig.class).toProvider(ConnectionPoolConfigProvider.class);
        bind(Connection.class).toProvider(ConnectionProvider.class);
    }
}
