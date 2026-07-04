package org.workfitai.cvservice.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.workfitai.cvservice.constants.LogType;

import static org.assertj.core.api.Assertions.assertThat;

class LogContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void setLogType_putsValueInMdc() {
        LogContext.setLogType(LogType.USER_ACTION);

        assertThat(LogContext.getLogType()).isEqualTo("USER_ACTION");
    }

    @Test
    void setLogType_ignoresNull() {
        LogContext.setLogType(null);

        assertThat(LogContext.getLogType()).isNull();
    }

    @Test
    void setAction_ignoresBlank() {
        LogContext.setAction("  ");

        assertThat(MDC.get("action")).isNull();
    }

    @Test
    void setEntityType_ignoresNull() {
        LogContext.setEntityType(null);

        assertThat(MDC.get("entity_type")).isNull();
    }

    @Test
    void setEntityId_setsValue() {
        LogContext.setEntityId("cv-1");

        assertThat(MDC.get("entity_id")).isEqualTo("cv-1");
    }

    @Test
    void setContext_setsAllValues() {
        LogContext.setContext(LogType.SYSTEM, "CREATE_JOB", "Job", "job-1");

        assertThat(LogContext.getLogType()).isEqualTo("SYSTEM");
        assertThat(MDC.get("action")).isEqualTo("CREATE_JOB");
        assertThat(MDC.get("entity_type")).isEqualTo("Job");
        assertThat(MDC.get("entity_id")).isEqualTo("job-1");
    }

    @Test
    void clear_removesAllKeys() {
        LogContext.setContext(LogType.AUTH, "LOGIN", "User", "u-1");

        LogContext.clear();

        assertThat(LogContext.getLogType()).isNull();
        assertThat(MDC.get("action")).isNull();
        assertThat(MDC.get("entity_type")).isNull();
        assertThat(MDC.get("entity_id")).isNull();
    }
}
