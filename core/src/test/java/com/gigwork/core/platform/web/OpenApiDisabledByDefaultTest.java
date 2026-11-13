package com.gigwork.core.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.identity.support.IdentityIntegrationTest;

/** Outside the local profile the API docs and Swagger UI do not exist. */
@IdentityIntegrationTest
class OpenApiDisabledByDefaultTest {

	@Autowired
	MockMvc mvc;

	@Test
	void theApiDocsAreNotServed() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
		mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isNotFound());
	}

	@Test
	void swaggerUiIsNotServed() throws Exception {
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
		mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
	}

}
