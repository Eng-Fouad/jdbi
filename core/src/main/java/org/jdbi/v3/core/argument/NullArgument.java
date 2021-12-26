/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.core.argument;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.jdbi.v3.core.statement.StatementContext;

/**
 * A typed SQL null argument.
 */
public class NullArgument implements Argument {
    private final Integer sqlType;

    /**
     * @param sqlType the SQL type of the Null
     * @see java.sql.Types
     */
    public NullArgument(int sqlType) {
        this.sqlType = sqlType;
    }

    /**
     * @param sqlType the SQL type of the Null
     * @see java.sql.Types
     */
    public NullArgument(Integer sqlType) {
        this.sqlType = sqlType;
    }

    @Override
    public void apply(final int position, PreparedStatement statement, StatementContext ctx) throws SQLException {
        if (sqlType == null) {
            ctx.getConfig(Arguments.class).getUntypedNullArgument().apply(position, statement, ctx);
        } else {
            statement.setNull(position, sqlType);
        }
    }

    /**
     * Returns the {@link java.sql.Types} value that is used.
     *
     * @return the SQL type of the null.
     * @see java.sql.Types
     */
    public Integer getSqlType() {
        return sqlType;
    }

    @Override
    public String toString() {
        return "NULL";
    }
}
