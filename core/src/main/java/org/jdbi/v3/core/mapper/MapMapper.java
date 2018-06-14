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
package org.jdbi.v3.core.mapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.jdbi.v3.core.config.JdbiConfig;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.meta.Beta;

/**
 * Yo dawg, I heard you like maps, so I made you a mapper that maps rows into {@code Map<String,Object>}. Map
 * keys are column names, while map values are the values in those columns. Map keys are converted to lowercase by
 * default.
 */
public class MapMapper implements RowMapper<Map<String, Object>> {
    /**
     * @deprecated TODO remove in jdbi4
     */
    @Deprecated
    private final boolean toLowerCase;

    /**
     * Constructs a new MapMapper, with map keys converted to lowercase.
     */
    public MapMapper() {
        this(true);
    }

    /**
     * Constructs a new MapMapper
     * @param toLowerCase if true, column names are converted to lowercase in the mapped {@link Map}.
     *
     * @deprecated use the {@link Config} class instead
     */
    @Deprecated
    public MapMapper(boolean toLowerCase) {
        this.toLowerCase = toLowerCase;
    }

    @Override
    public Map<String, Object> map(ResultSet rs, StatementContext ctx) throws SQLException {
        return specialize(rs, ctx).map(rs, ctx);
    }

    @Override
    public RowMapper<Map<String, Object>> specialize(ResultSet rs, StatementContext ctx) throws SQLException {
        Config config = ctx.getConfig(Config.class);
        List<String> columnNames = getColumnNames(rs, !config.getForceNewApi() && toLowerCase ? Config.CaseChange.LOWER : config.getCaseChange());

        return (r, c) -> {
            Map<String, Object> row = new LinkedHashMap<>(columnNames.size());

            for (int i = 0; i < columnNames.size(); i++) {
                row.put(columnNames.get(i), rs.getObject(i + 1));
            }

            return row;
        };
    }

    private static List<String> getColumnNames(ResultSet rs, Config.CaseChange caseChange) throws SQLException {
        // important: ordered and unique
        Set<String> columnNames = new LinkedHashSet<>();
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        for (int i = 0; i < columnCount; i++) {
            String columnName = meta.getColumnName(i + 1);
            String alias = meta.getColumnLabel(i + 1);

            String name = caseChange.apply(alias == null ? columnName : alias);

            boolean added = columnNames.add(name);
            if (!added) {
                throw new RuntimeException("column " + name + " appeared twice in this resultset!");
            }
        }

        return new ArrayList<>(columnNames);
    }

    @Beta
    public static class Config implements JdbiConfig<Config> {
        private CaseChange caseChange;
        private boolean forceNewApi;

        public Config() {
            caseChange = CaseChange.NOP;
            forceNewApi = false;
        }

        private Config(Config that) {
            caseChange = that.caseChange;
            forceNewApi = that.forceNewApi;
        }

        @Beta
        public CaseChange getCaseChange() {
            return caseChange;
        }

        @Beta
        public Config setCaseChange(CaseChange caseChange) {
            this.caseChange = caseChange;
            return this;
        }

        /**
         * @deprecated will be removed along with the old API
         */
        @Beta
        @Deprecated
        public boolean getForceNewApi() {
            return forceNewApi;
        }

        /**
         * @deprecated will be removed along with the old API
         */
        @Beta
        @Deprecated
        public Config setForceNewApi(boolean forceNewApi) {
            this.forceNewApi = forceNewApi;
            return this;
        }

        @Override
        public Config createCopy() {
            return new Config(this);
        }

        @Beta
        public enum CaseChange {
            NOP(UnaryOperator.identity()),
            LOWER(s -> s.toLowerCase(Locale.ROOT)),
            UPPER(s -> s.toUpperCase(Locale.ROOT));

            private final UnaryOperator<String> transformation;

            CaseChange(UnaryOperator<String> transformation) {
                this.transformation = transformation;
            }

            private String apply(String s) {
                return transformation.apply(s);
            }
        }
    }
}
