package org.workfitai.cvservice.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.workfitai.cvservice.constant.CVConst;
import org.workfitai.cvservice.security.utils.SecurityUtils;

import java.util.Optional;

@Component
public class SpringSecurityAuditorAware implements AuditorAware<String> {
    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(SecurityUtils.getCurrentUserLogin().orElse(CVConst.SYSTEM_ACCOUNT));
    }
}