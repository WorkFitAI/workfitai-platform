package org.workfitai.jobservice.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.util.ApiMessage;

@Aspect
@Component
public class ApiMessageAspect {

    @Around("@annotation(apiMessage)")
    public Object handleApiMessage(ProceedingJoinPoint joinPoint, ApiMessage apiMessage) throws Throwable {
        Object result = joinPoint.proceed();

        if (result instanceof ResponseEntity<?> responseEntity) {
            Object body = responseEntity.getBody();
            if (body instanceof RestResponse<?> restResponse) {
                return ResponseEntity
                        .status(responseEntity.getStatusCode())
                        .body(new RestResponse<>(restResponse.getStatus(), apiMessage.value(), restResponse.getData()));
            }
        } else if (result instanceof RestResponse<?> restResponse) {
            return new RestResponse<>(restResponse.getStatus(), apiMessage.value(), restResponse.getData());
        }

        return result;
    }
}
