package com.gigwork.core.identity.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gigwork.core.identity.AccountNames;
import com.gigwork.core.identity.domain.FirstName;
import com.gigwork.core.identity.persistence.AccountRepository;

/** Setting and reading a user's first name. */
@Service
public class NameService implements AccountNames {

	private final AccountRepository accounts;

	NameService(AccountRepository accounts) {
		this.accounts = accounts;
	}

	/** The new name, or empty if the account is not active. Throws InvalidFirstNameException for a bad name. */
	@Transactional
	public Optional<String> setFirstName(UUID accountId, String input) {
		FirstName name = FirstName.parse(input);
		return accounts.updateFirstName(accountId, name.value()) ? Optional.of(name.value()) : Optional.empty();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<String> firstName(UUID accountId) {
		return accounts.findFirstName(accountId).filter(name -> !name.isBlank());
	}

}
