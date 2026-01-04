package org.workfitai.userservice.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable privacy checking on controller methods.
 * Methods annotated with @CheckPrivacy will automatically apply privacy rules
 * based on user's privacy settings (profileVisibility).
 * 
 * Usage:
 * 
 * @CheckPrivacy
 *               @GetMapping("/{id}")
 *               public ResponseEntity<CandidateResponse> getById(@PathVariable
 *               UUID id) {...}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckPrivacy {
    /**
     * Whether to check if the requester owns the resource
     */
    boolean checkOwnership() default true;

    /**
     * Whether to allow admins to bypass privacy checks
     */
    boolean allowAdmin() default true;
}
