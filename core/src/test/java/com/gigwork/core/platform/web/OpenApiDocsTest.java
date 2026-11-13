package com.gigwork.core.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.TestcontainersConfiguration;

/** What the local profile switches on: API docs and Swagger UI, readable without a token, with the bearer scheme. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = { "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true" })
class OpenApiDocsTest {

	@Autowired
	MockMvc mvc;

	@Test
	void documentsTheThreeAuthEndpointsUnderTheVersionPrefix() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/v1/auth/otp/request'].post").exists())
				.andExpect(jsonPath("$.paths['/v1/auth/otp/verify'].post").exists())
				.andExpect(jsonPath("$.paths['/v1/auth/token/refresh'].post").exists())
				.andExpect(jsonPath("$.paths['/auth/otp/request']").doesNotExist());
	}

	@Test
	void describesTheBearerTokenSchemeAndAppliesItByDefault() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
				.andExpect(jsonPath("$.security[0].bearerAuth").exists());
	}

	@Test
	void thePublicAuthEndpointsOptOutOfTheBearerToken() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/v1/auth/otp/request'].post.security").isEmpty())
				.andExpect(jsonPath("$.paths['/v1/auth/otp/verify'].post.security").isEmpty())
				.andExpect(jsonPath("$.paths['/v1/auth/token/refresh'].post.security").isEmpty());
	}

	@Test
	void actuatorIsNotPartOfTheApiDocs() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
	}

	@Test
	void swaggerUiNeedsNoToken() throws Exception {
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
		mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
	}

}
