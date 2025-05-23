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
package org.jdbi.v3.core.statement;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Something;
import org.jdbi.v3.core.junit5.H2DatabaseExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestPositionalParameterBinding {

    @RegisterExtension
    public H2DatabaseExtension h2Extension = H2DatabaseExtension.instance().withInitializer(H2DatabaseExtension.SOMETHING_INITIALIZER);

    @Test
    public void testSetPositionalString() {
        Handle h = h2Extension.getSharedHandle();

        h.execute("insert into something (id, name) values (1, 'eric')");
        h.execute("insert into something (id, name) values (2, 'brian')");

        Something eric = h.createQuery("select * from something where name = ?")
                .bind(0, "eric")
                .mapToBean(Something.class)
                .list()
                .get(0);
        assertThat(eric.getId()).isOne();
    }

    @Test
    public void testSetPositionalInteger() {
        Handle h = h2Extension.getSharedHandle();

        h.execute("insert into something (id, name) values (1, 'eric')");
        h.execute("insert into something (id, name) values (2, 'brian')");

        Something eric = h.createQuery("select * from something where id = ?")
                .bind(0, 1)
                .mapToBean(Something.class)
                .list().get(0);
        assertThat(eric.getId()).isOne();
    }

    @Test
    public void testBehaviorOnBadBinding1() {
        Handle h = h2Extension.getSharedHandle();

        assertThatThrownBy(() -> {
            try (Query query = h.createQuery("select * from something where id = ? and name = ?")) {
                query.bind(0, 1)
                    .mapToBean(Something.class)
                    .list();
            }
        }).isInstanceOf(UnableToCreateStatementException.class);
    }

    @Test
    public void testBehaviorOnBadBinding2() {
        Handle h = h2Extension.getSharedHandle();

        assertThatThrownBy(() -> {
            try (Query query = h.createQuery("select * from something where id = ?")) {
                query.bind(1, 1)
                    .bind(2, "Hi")
                    .mapToBean(Something.class)
                    .list();
            }
        }).isInstanceOf(UnableToCreateStatementException.class);
    }

    @Test
    public void testInsertParamBinding() {
        Handle h = h2Extension.getSharedHandle();

        int count = h.createUpdate("insert into something (id, name) values (?, 'eric')")
                .bind(0, 1)
                .execute();

        assertThat(count).isOne();
    }

    @Test
    public void testPositionalConvenienceInsert() {
        Handle h = h2Extension.getSharedHandle();

        int count = h.execute("insert into something (id, name) values (?, ?)", 1, "eric");

        assertThat(count).isOne();
    }
}
