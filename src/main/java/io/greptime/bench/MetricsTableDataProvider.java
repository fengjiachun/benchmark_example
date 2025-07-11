package io.greptime.bench;

import io.greptime.common.util.SystemPropertyUtil;
import io.greptime.models.DataType;
import io.greptime.models.TableSchema;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * The metrics table data provider.
 */
public class MetricsTableDataProvider implements TableDataProvider {

    /* Create a table with the following schema:
    ```sql
    CREATE TABLE IF NOT EXISTS `tt_metrics_table` (
        `ts` TIMESTAMP(3) NOT NULL,
        `idc` STRING NULL INVERTED INDEX,
        `host` STRING NULL INVERTED INDEX,
        `shard` INT,
        `service` STRING NULL INVERTED INDEX,
        `url` STRING NULL,
        `cpu_util` DOUBLE NULL,
        `memory_util` DOUBLE NULL,
        `disk_util` DOUBLE NULL,
        `load_util` DOUBLE NULL,
        TIME INDEX (`ts`),
        PRIMARY KEY (`idc`, `host`, `service`)
    )
    PARTITION ON COLUMNS (shard) (
        shard < 1,
        shard >= 1
    )
    ENGINE=mito
    WITH(
        append_mode = 'true'
    );
    ```
    */

    private final TableSchema tableSchema;
    private final long rowCount;
    private final int serviceNumPerApp;

    {
        this.tableSchema = TableSchema.newBuilder("tt_metrics_table")
                .addTimestamp("ts", DataType.TimestampMillisecond)
                .addTag("idc", DataType.String)
                .addTag("host", DataType.String)
                .addField("shard", DataType.Int32)
                .addTag("service", DataType.String)
                .addField("url", DataType.String)
                .addField("cpu_util", DataType.Float64)
                .addField("memory_util", DataType.Float64)
                .addField("disk_util", DataType.Float64)
                .addField("load_util", DataType.Float64)
                .build();
        this.rowCount = SystemPropertyUtil.getLong("table_row_count", 10_000_000_000L);
        this.serviceNumPerApp = SystemPropertyUtil.getInt("tt_metrics_table.service_num_per_app", 20);
    }

    @Override
    public void init() {
        // do nothing
    }

    @Override
    public void close() throws Exception {
        // do nothing
    }

    @Override
    public TableSchema tableSchema() {
        return this.tableSchema;
    }

    @Override
    public Iterator<Object[]> rows() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        return new Iterator<Object[]>() {
            private long index = 0;
            private Batch batch = null;

            @Override
            public boolean hasNext() {
                return index < rowCount;
            }

            @Override
            public Object[] next() {
                index++;

                if (batch == null || !batch.hasNext()) {
                    // fill data
                    ArrayList<Object[]> rows = new ArrayList<>(serviceNumPerApp);
                    long ts = System.currentTimeMillis();

                    String idc = nextIdc(random);
                    String host = nextHost(random, idc);
                    String app = nextApp(random, host);
                    String url = nextUrl(random, ts);

                    // In real-world scenarios, all services on a host typically generate metrics data simultaneously,
                    // so this data generation logic aligns with real-world patterns.
                    //
                    // Each app/host has 20 services.
                    for (int i = 0; i < serviceNumPerApp; i++) {
                        String service = nextService(random, app, i);

                        rows.add(new Object[] {
                            ts,
                            idc,
                            host,
                            i,
                            service,
                            url,
                            random.nextDouble(0, 100), // cpu_util
                            random.nextDouble(0, 100), // memory_util
                            random.nextDouble(0, 100), // disk_util
                            random.nextDouble(0, 100), // load_util
                        });
                    }

                    batch = new Batch(rows);
                }

                return batch.next();
            }
        };
    }

    @Override
    public long rowCount() {
        return this.rowCount;
    }

    /**
     * Returns a random idc, there are 20 idcs.
     */
    private String nextIdc(ThreadLocalRandom random) {
        return "idc_" + random.nextInt(20);
    }

    /**
     * Returns a random host name. Each IDC contains approximately 500 hosts, with a total of 10,000 hosts across all IDCs.
     */
    private String nextHost(ThreadLocalRandom random, String idc) {
        return idc + "_host_" + random.nextInt(500);
    }

    /**
     * Returns a random app name. There are 500 apps across all IDCs.
     */
    private String nextApp(ThreadLocalRandom random, String host) {
        int hash = host.hashCode();
        return "app_" + (hash % 500);
    }

    /**
     * Returns a random service name. Each app contains 20 services.
     */
    private String nextService(ThreadLocalRandom random, String app, int index) {
        return app + "_service_" + index;
    }

    /**
     * Returns a random URL with a timestamp-based path.
     * The URL format is: http://127.0.0.1/helloworld/{minutes}/{random_id}
     * where random_id is between 0 and 1999.
     */
    private String nextUrl(ThreadLocalRandom random, long ts) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ts);
        return String.format("http://127.0.0.1/helloworld/%d/%d", minutes, random.nextInt(2000));
    }
}

class Batch {
    private final ArrayList<Object[]> rows;
    private int index = 0;

    Batch(ArrayList<Object[]> rows) {
        this.rows = rows;
    }

    boolean hasNext() {
        return this.index < this.rows.size();
    }

    Object[] next() {
        return this.rows.get(this.index++);
    }
}
