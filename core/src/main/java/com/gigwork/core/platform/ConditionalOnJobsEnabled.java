package com.gigwork.core.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Put this on every class that holds {@code @Scheduled} job methods (ADR-0010): the class exists only when
 * {@code gigwork.jobs.enabled} is {@code true}. Enabling scheduling is not a reliable off switch, because libraries
 * on the classpath (Spring Modulith's moments, for one) enable it too and would run a {@code @Scheduled} method anyway.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@ConditionalOnProperty(name = "gigwork.jobs.enabled", havingValue = "true")
public @interface ConditionalOnJobsEnabled {

}
