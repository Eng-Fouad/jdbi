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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import de.softwareforge.testing.postgres.junit5.EmbeddedPgExtension;
import de.softwareforge.testing.postgres.junit5.MultiDatabaseBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Something;
import org.jdbi.v3.core.junit5.PgDatabaseExtension;
import org.jdbi.v3.core.result.BatchResultBearing;
import org.jdbi.v3.core.result.ResultIterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class TestPreparedBatchGenerateKeysPostgres {

    @RegisterExtension
    public static EmbeddedPgExtension pg = MultiDatabaseBuilder.instanceWithDefaults().build();

    @RegisterExtension
    public PgDatabaseExtension pgExtension = PgDatabaseExtension.instance(pg).withInitializer(
        handle -> handle.execute("create table something (id serial primary key, name varchar(50), create_time timestamptz)")
    );

    @Test
    public void testBatchInsertWithKeyGenerationAndExplicitColumnNames() {
        Handle h = pgExtension.getSharedHandle();

        PreparedBatch batch = h.prepareBatch("insert into something (name) values (?) ");
        batch.add("Brian");
        batch.add("Thom");

        List<Integer> ids = batch.executePreparedBatch("id").mapTo(Integer.class).list();
        assertThat(ids).containsExactly(1, 2);

        List<Something> somethings = h.createQuery("select id, name from something")
            .mapToBean(Something.class)
            .list();
        assertThat(somethings).containsExactly(new Something(1, "Brian"), new Something(2, "Thom"));
    }

    @Test
    public void testBatchInsertWithKeyGenerationAndExplicitSeveralColumnNames() {
        Handle h = pgExtension.getSharedHandle();

        PreparedBatch batch = h.prepareBatch("insert into something (name, create_time) values (?, ?)");
        batch.add("Brian", Instant.ofEpochSecond(1));
        batch.add("Thom", Instant.ofEpochSecond(2));

        List<IdCreateTime> ids = batch.executePreparedBatch("id", "create_time")
            .map((r, ctx) -> new IdCreateTime(r.getInt("id"), r.getObject("create_time", OffsetDateTime.class)))
            .list();

        assertThat(ids).hasSize(2);
        assertThat(ids).extracting(ic -> ic.id).containsExactly(1, 2);
        assertThat(ids).extracting(ic -> ic.createTime.toEpochSecond()).containsExactly(1L, 2L);
    }

    @Test
    public void testBatchResultBearing() {
        try (Handle h = pgExtension.getSharedHandle()) {

            PreparedBatch batch1 = h.prepareBatch("insert into something (name) values (?) ");
            batch1.add("Brian1");
            batch1.add("Brian2");
            batch1.add("Thom1");
            batch1.add("Thom2");
            List<IdCreateTime> ids = batch1.executePreparedBatch("id", "create_time")
                .map((r, ctx) -> new IdCreateTime(
                    r.getInt("id"),
                    r.getObject("create_time", OffsetDateTime.class)))
                .list();

            assertThat(ids).hasSize(4);
            assertThat(ids).extracting(ic -> ic.id).containsExactly(1, 2, 3, 4);
            assertThat(ids).extracting(ic -> ic.createTime).containsExactly(null, null, null, null);

            var now = OffsetDateTime.ofInstant(Instant.ofEpochSecond(1234567), ZoneOffset.UTC);

            PreparedBatch batch2 = h.prepareBatch("update something set create_time = :now where name like :name returning id, name, create_time");

            batch2.bind("name", "Brian%")
                .bind("now", now)
                .add()
                .bind("now", now)
                .bind("name", "Thom%")
                .add()
                .bind("now", now)
                .bind("name", "Nothing%")
                .add();

            List<List<IdCreateTime>> choppedList = batch2.executePreparedBatch("id", "create_time")
                .map((r, ctx) -> new IdCreateTime(r.getInt("id"), r.getObject("create_time", OffsetDateTime.class)))
                .listPerBatch();

            assertThat(choppedList).hasSize(3);
            assertThat(choppedList.get(0)).extracting(ic -> ic.id).containsExactly(1, 2);
            assertThat(choppedList.get(0)).extracting(ic -> ic.createTime)
                .containsExactly(now, now);
            assertThat(choppedList.get(1)).extracting(ic -> ic.id).containsExactly(3, 4);
            assertThat(choppedList.get(1)).extracting(ic -> ic.createTime)
                .containsExactly(now, now);
            assertThat(choppedList.get(2)).extracting(ic -> ic.id).isEmpty();
            assertThat(choppedList.get(2)).extracting(ic -> ic.createTime).isEmpty();
        }
    }

    @Test
    public void testListModCount() {
        try (Handle h = pgExtension.getSharedHandle()) {
            for (int i = 0; i < 5; i++) {
                h.execute("INSERT INTO something (name) VALUES('Brian" + i + "')");
                h.execute("INSERT INTO something (name) VALUES('Tom" + i + "')");
                h.execute("INSERT INTO something (name) VALUES('Steven" + i + "')");
            }

            assertThat(h.createQuery("SELECT COUNT(1) FROM something").mapTo(Integer.class).first()).isEqualTo(15);

            PreparedBatch batch = h.prepareBatch("update something set create_time = now() where name like :name");
            batch.bind("name", "Brian%").add()
                .bind("name", "Tom%").add()
                .bind("name", "Steven%").add()
                .bind("name", "Nothing%").add();

            List<Integer> counts = new ArrayList<>();
            try (ResultIterator<Integer> i = batch.executeAndGetModCount()) {
                while (i.hasNext()) {
                    counts.add(i.next());
                }
            }

            assertThat(counts).containsExactly(5, 5, 5, 0);

            PreparedBatch batch2 = h.prepareBatch("update something set create_time = now() where name like :name");
            batch2.bind("name", "Brian%").add()
                .bind("name", "Tom%").add()
                .bind("name", "Steven%").add()
                .bind("name", "Nothing%").add();

            BatchResultBearing batchResultBearing = batch2.executePreparedBatch();
            batchResultBearing.mapTo(Integer.class).list();

            int[] batchCounts = batchResultBearing.modifiedRowCounts();
            assertThat(batchCounts).containsExactly(5, 5, 5, 0);
        }
    }

    private static class IdCreateTime {

        final Integer id;
        final OffsetDateTime createTime;

        IdCreateTime(Integer id, OffsetDateTime createTime) {
            this.id = id;
            this.createTime = createTime;
        }
    }
}
