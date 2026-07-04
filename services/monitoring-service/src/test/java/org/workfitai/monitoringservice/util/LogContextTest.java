package org.workfitai.monitoringservice.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.workfitai.monitoringservice.constants.LogType;

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
    void setAction_putsValue_andIgnoresBlank() {
        LogContext.setAction("CREATE_JOB");
        assertThat(MDC.get("action")).isEqualTo("CREATE_JOB");

        MDC.clear();
        LogContext.setAction("  ");
        assertThat(MDC.get("action")).isNull();

        LogContext.setAction(null);
        assertThat(MDC.get("action")).isNull();
    }

    @Test
    void setEntityType_putsValue_andIgnoresBlank() {
        LogContext.setEntityType("Job");
        assertThat(MDC.get("entity_type")).isEqualTo("Job");

        MDC.clear();
        LogContext.setEntityType("");
        assertThat(MDC.get("entity_type")).isNull();
    }

    @Test
    void setEntityId_putsValue_andIgnoresBlank() {
        LogContext.setEntityId("job-1");
        assertThat(MDC.get("entity_id")).isEqualTo("job-1");

        MDC.clear();
        LogContext.setEntityId(null);
        assertThat(MDC.get("entity_id")).isNull();
    }

    @Test
    void setContext_setsAllFourValues() {
        LogContext.setContext(LogType.SYSTEM, "REFRESH", "Token", "tok-1");

        assertThat(MDC.get("log_type")).isEqualTo("SYSTEM");
        assertThat(MDC.get("action")).isEqualTo("REFRESH");
        assertThat(MDC.get("entity_type")).isEqualTo("Token");
        assertThat(MDC.get("entity_id")).isEqualTo("tok-1");
    }

    @Test
    void clear_removesAllFourKeys() {
        LogContext.setContext(LogType.SYSTEM, "REFRESH", "Token", "tok-1");

        LogContext.clear();

        assertThat(MDC.get("log_type")).isNull();
        assertThat(MDC.get("action")).isNull();
        assertThat(MDC.get("entity_type")).isNull();
        assertThat(MDC.get("entity_id")).isNull();
    }
}
