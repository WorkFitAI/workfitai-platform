package org.workfitai.userservice.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.workfitai.userservice.constants.LogType;

import static org.assertj.core.api.Assertions.assertThat;

class LogContextTest {

    @AfterEach
    void tearDown() {
        LogContext.clear();
    }

    @Test
    void setContext_validValues_populatesMdc() {
        LogContext.setContext(LogType.USER_ACTION, "UPDATE_PROFILE", "User", "user-1");

        assertThat(LogContext.getLogType()).isEqualTo("USER_ACTION");
        assertThat(MDC.get("action")).isEqualTo("UPDATE_PROFILE");
        assertThat(MDC.get("entity_type")).isEqualTo("User");
        assertThat(MDC.get("entity_id")).isEqualTo("user-1");
    }

    @Test
    void setters_nullOrBlankValues_leaveExistingContextUntouched() {
        LogContext.setContext(LogType.SYSTEM, "CREATE_USER", "User", "user-1");

        LogContext.setLogType(null);
        LogContext.setAction(" ");
        LogContext.setEntityType("");
        LogContext.setEntityId(null);

        assertThat(LogContext.getLogType()).isEqualTo("SYSTEM");
        assertThat(MDC.get("action")).isEqualTo("CREATE_USER");
        assertThat(MDC.get("entity_type")).isEqualTo("User");
        assertThat(MDC.get("entity_id")).isEqualTo("user-1");
    }

    @Test
    void clear_removesOnlyOwnedContextKeys() {
        MDC.put("trace_id", "trace-1");
        LogContext.setContext(LogType.AUTH, "BLOCK_USER", "User", "user-1");

        LogContext.clear();

        assertThat(LogContext.getLogType()).isNull();
        assertThat(MDC.get("action")).isNull();
        assertThat(MDC.get("entity_type")).isNull();
        assertThat(MDC.get("entity_id")).isNull();
        assertThat(MDC.get("trace_id")).isEqualTo("trace-1");
        MDC.remove("trace_id");
    }
}
