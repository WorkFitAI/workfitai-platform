package org.workfitai.jobservice.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.workfitai.jobservice.domain.response.RestResponse;
import org.workfitai.jobservice.util.ApiMessage;

@Aspect
@Component
public class ApiMessageAspect {

    @Around("@annotation(apiMessage)")
    public Object handleApiMessage(ProceedingJoinPoint joinPoint, ApiMessage apiMessage) throws Throwable {
        Object result = joinPoint.proceed(); // chạy method gốc

        if (result instanceof ResponseEntity<?> responseEntity) {
            Object body = responseEntity.getBody();
            if (body instanceof RestResponse<?> restResponse) {
                restResponse.setMessage(apiMessage.value()); // gắn message từ annotation
            }
        }

        return result;
    }
}
