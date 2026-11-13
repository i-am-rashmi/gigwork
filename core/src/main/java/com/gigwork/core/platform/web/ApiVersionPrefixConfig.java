package com.gigwork.core.platform.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.gigwork.core.platform.ApiPaths;

/** Puts {@link ApiPaths#V1} in front of every REST controller of the core, in this one place. */
@Configuration(proxyBeanMethods = false)
class ApiVersionPrefixConfig implements WebMvcConfigurer {

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(ApiPaths.V1,
				HandlerTypePredicate.forBasePackage("com.gigwork.core").and(HandlerTypePredicate.forAnnotation(RestController.class)));
	}

}
