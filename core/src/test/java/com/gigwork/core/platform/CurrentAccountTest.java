package com.gigwork.core.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentAccountTest {

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	private static void signIn(String subject) {
		Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject).issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(60)).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES));
	}

	@Test
	void theAccountIsTheTokenSubject() {
		UUID id = UUID.randomUUID();
		signIn(id.toString());

		assertThat(CurrentAccount.optional()).contains(id);
		assertThat(CurrentAccount.required()).isEqualTo(id);
	}

	@Test
	void aVisitorHasNoAccount() {
		assertThat(CurrentAccount.optional()).isEmpty();
		SecurityContextHolder.getContext().setAuthentication(
				new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

		assertThat(CurrentAccount.optional()).isEmpty();
		assertThatThrownBy(CurrentAccount::required).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
	}

	@Test
	void aSubjectThatIsNotAUuidIsNoAccount() {
		signIn("not-a-uuid");

		assertThat(CurrentAccount.optional()).isEmpty();
	}

}
