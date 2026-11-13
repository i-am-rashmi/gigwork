package com.gigwork.core.platform.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * API documentation shared by all modules. The docs and Swagger UI are switched off by default and on in the local
 * profile ({@code springdoc.*.enabled}). Every endpoint is documented as needing the bearer token, so Swagger UI
 * shows an Authorize button; public endpoints opt out with an empty {@code @SecurityRequirements}.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	OpenAPI gigworkOpenApi() {
		return new OpenAPI()
				.info(new Info().title("GigWork core API").version("v1")
						.description("Sign up and log in with a mobile number and a one-time code, then send the "
								+ "access token as `Authorization: Bearer <token>`."))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME,
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}

}
