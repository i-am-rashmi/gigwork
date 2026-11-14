package com.gigwork.core.profile.application;

import java.util.Optional;

/**
 * What a viewer is allowed to see of someone's profile. {@code firstName} and the full links are for logged-in users
 * only: {@code loggedIn} says whether the caller had a token.
 */
public record PublicProfile(ProfileView profile, Optional<String> firstName, boolean loggedIn) {
}
