package com.gigwork.core.identity.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gigwork.core.identity.application.AccountService.VerifiedAccount;
import com.gigwork.core.identity.domain.MobileNumber;

/** Sign up and log in with a mobile number and a one-time code, in a single transaction (flow 1). */
@Service
public class AuthenticationService {

	/** @param newAccount true if this login created the account (sign-up) */
	public record LoginResult(UUID accountId, boolean newAccount, AuthTokens tokens) {
	}

	private final OtpService otp;
	private final AccountService accounts;
	private final SessionService sessions;

	AuthenticationService(OtpService otp, AccountService accounts, SessionService sessions) {
		this.otp = otp;
		this.accounts = accounts;
		this.sessions = sessions;
	}

	/**
	 * Empty if the code is not valid or the account may not log in. The transaction commits either way, so a
	 * failed attempt stays counted; callers turn an empty result into a 401 after this method has returned.
	 */
	@Transactional
	public Optional<LoginResult> verifyOtp(MobileNumber mobile, UUID challengeId, String code) {
		if (!otp.verify(mobile, challengeId, code)) {
			return Optional.empty();
		}
		Optional<VerifiedAccount> account = accounts.findOrCreate(mobile);
		return account.map(a -> new LoginResult(a.accountId(), a.newAccount(), sessions.start(a.accountId())));
	}

}
