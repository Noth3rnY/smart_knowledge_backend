package com.smartknowledge.backend;

import com.smartknowledge.backend.common.Json;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SchemaContractTest {
    @Test void fifteenMeaningfulTablesMeetEachMembersRequirement() throws Exception {
        String sql=new String(Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/V1__business_schema.sql")).readAllBytes(),StandardCharsets.UTF_8);
        Matcher tables=Pattern.compile("CREATE TABLE (\\w+) \\((.*?)\\n\\) ENGINE",Pattern.DOTALL).matcher(sql);
        List<Integer> counts=new ArrayList<>();
        while(tables.find()) {
            Matcher columns=Pattern.compile("(?m)^ \\w+ (?:CHAR|VARCHAR|DATETIME|TEXT|MEDIUMTEXT|BOOLEAN|BIGINT|INT).*COMMENT '").matcher(tables.group(2));
            int count=0; while(columns.find()) count++;
            assertThat(count).as(tables.group(1)).isGreaterThanOrEqualTo(7); counts.add(count);
        }
        assertThat(counts).hasSize(15);
        for(int i=0;i<15;i+=3) assertThat(counts.subList(i,i+3).stream().mapToInt(Integer::intValue).sum()).isGreaterThanOrEqualTo(21);
    }
    @Test void utf8CapacityMeasuresBytesInsteadOfCharacters() {
        assertThat(Json.bytes("笔记")).isEqualTo(6);
        assertThat(Json.hash("same")).isEqualTo(Json.hash("same")).hasSize(64);
    }
}
