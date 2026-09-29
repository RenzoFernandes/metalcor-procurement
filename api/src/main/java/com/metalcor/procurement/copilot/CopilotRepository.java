package com.metalcor.procurement.copilot;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Runs already-validated copilot SQL against the metalcor_readonly connection. The result shape
 * is not known ahead of time (any SELECT is allowed), so rows come back as generic column maps.
 */
@Repository
public class CopilotRepository {

    private final JdbcClient jdbc;

    public CopilotRepository(ReadonlyJdbcClient readonlyJdbcClient) {
        this.jdbc = readonlyJdbcClient.jdbc();
    }

    public CopilotQueryResult execute(String sql) {
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = jdbc.sql(sql).query((rs, rowNum) -> {
            ResultSetMetaData meta = rs.getMetaData();
            if (columns.isEmpty()) {
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    columns.add(meta.getColumnLabel(i));
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            return row;
        }).list();
        return new CopilotQueryResult(columns, rows);
    }
}