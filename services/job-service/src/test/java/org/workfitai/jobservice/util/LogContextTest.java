package org.workfitai.jobservice.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.workfitai.jobservice.constants.LogType;

import static org.assertj.core.api.Assertions.assertThat;

class LogContextTest {

    @AfterEach
    void clearMdc() {
        LogContext.clear();
    }

    @Test
    void setLogType_putsLogTypeIntoMdc() {
        LogContext.setLogType(LogType.USER_ACTION);

        assertThat(LogContext.getLogType()).isEqualTo("USER_ACTION");
    }

    @Test
    void setLogType_ignoresNull() {
        LogContext.setLogType(null);

        assertThat(LogContext.getLogType()).isNull();
    }

    @Test
    void setAction_putsActionIntoMdc() {
        LogContext.setAction("CREATE_JOB");

        assertThat(MDC.get("action")).isEqualTo("CREATE_JOB");
    }

    @Test
    void setAction_ignoresBlank() {
        LogContext.setAction("  ");

        assertThat(MDC.get("action")).isNull();
    }

    @Test
    void setEntityType_putsEntityTypeIntoMdc() {
        LogContext.setEntityType("Job");

        assertThat(MDC.get("entity_type")).isEqualTo("Job");
    }

    @Test
    void setEntityId_putsEntityIdIntoMdc() {
        LogContext.setEntityId("job-1");

        assertThat(MDC.get("entity_id")).isEqualTo("job-1");
    }

    @Test
    void setContext_setsAllValuesAtOnce() {
        LogContext.setContext(LogType.SYSTEM, "CLOSE_JOB", "Job", "job-2");

        assertThat(LogContext.getLogType()).isEqualTo("SYSTEM");
        assertThat(MDC.get("action")).isEqualTo("CLOSE_JOB");
        assertThat(MDC.get("entity_type")).isEqualTo("Job");
        assertThat(MDC.get("entity_id")).isEqualTo("job-2");
    }

    @Test
    void clear_removesAllMdcValues() {
        LogContext.setContext(LogType.AUTH, "LOGIN", "User", "user-1");

        LogContext.clear();

        assertThat(LogContext.getLogType()).isNull();
        assertThat(MDC.get("action")).isNull();
        assertThat(MDC.get("entity_type")).isNull();
        assertThat(MDC.get("entity_id")).isNull();
    }
}
