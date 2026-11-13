package com.gigwork.core.identity.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import com.gigwork.core.TestcontainersConfiguration;

/** Full application against real PostgreSQL + PostGIS, with a capturing OTP sender and a movable clock. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, IdentityTestConfig.class })
public @interface IdentityIntegrationTest {

}
