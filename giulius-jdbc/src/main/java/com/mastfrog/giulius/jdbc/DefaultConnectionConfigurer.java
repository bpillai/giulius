/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.giulius.jdbc;

import com.google.inject.Inject;
import static com.mastfrog.giulius.jdbc.JdbcModule.HINT_SCROLLABLE_CURSORS;
import com.mastfrog.settings.Settings;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultConnectionConfigurer implements ConnectionConfigurer {

    private final Settings settings;

    @Inject
    public DefaultConnectionConfigurer(Settings settings) {
        this.settings = settings;
    }

    @Override
    public Connection onProvideConnection(Connection connection) throws SQLException {
        if (settings.getBoolean(HINT_SCROLLABLE_CURSORS, false)) {
            connection.setAutoCommit(false);
            connection.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
            connection.setReadOnly(true);
        }
        return connection;
    }
}
