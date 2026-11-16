# GigWork Bruno collection

Manual exploration of the walking skeleton against a locally running core: the happy flow from login to a confirmed
hire, then the failure cases. The automated version, which CI runs on every build, is
`core/src/test/java/com/gigwork/core/e2e/`. This collection mirrors it so you can poke at the API by hand.

## Run it

1. Start the infrastructure and the core with the `local` profile:

   ```bash
   docker compose -f infra/docker-compose.yml up -d
   cd core && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```

2. Open this folder (`api/bruno`) in [Bruno](https://www.usebruno.com/) and select the **local** environment.
   Its variables:

   | Variable | Meaning |
   | --- | --- |
   | `baseUrl` | `http://localhost:8080/v1` |
   | `posterMobile`, `talentMobile` | The two accounts of the flow (Poster and Talent) |
   | `thirdMobile` | A third account, used only by one failure case (a contact view by someone who is not a party) |
   | `posterOtp`, `talentOtp`, `thirdOtp` | Empty; you paste the login codes here (next step) |

3. **Log in** (folder `00-login`). The local profile has no SMS provider: the code is written to the application log.
   For each of the three accounts, in this order:
   1. Send `... request OTP`.
   2. Find the line `DEV OTP for ******NNNN is 123456` in the output of `spring-boot:run` (logger `gigwork.dev.otp`).
   3. Paste the six digits into the matching variable (`posterOtp`, `talentOtp`, `thirdOtp`) in the environment settings.
   4. Send `... verify OTP`.

   The token is kept as a runtime variable (`posterToken` etc.); a code is valid for 5 minutes and works once. The
   third account is only needed for `02-failures/09-third-contact`; skip it and that one request fails.

4. Run folder `01-happy-flow` with the runner (right-click, **Run**), then `02-failures`. Both are ordered and pass
   IDs to each other through runtime variables (`gigId`, `applicationId`, `hireId`, ...), so run them in that order,
   in the same Bruno session. Every request has tests; a green run means every status code and key field matched.

Notes:

- Runtime variables live as long as the Bruno session. Close the app and you log in again.
- Each run picks a random day 3 to 60 days ahead for the gig, so repeated runs on the same accounts never meet an
  earlier run's gig or booking. Accounts and gigs from earlier runs stay in your local database; reset it with
  `docker compose -f infra/docker-compose.yml down -v` when you want a clean slate.
- `02-failures` continues from the state the happy flow left behind (a confirmed hire on one gig). It cancels that
  hire, hires again and has the Poster cancel the gig, so run the happy flow again before repeating it.
- The last failure cases wait 1.5 seconds before checking that the hire was cancelled: the Hiring module reacts to
  the cancelled gig through an event, a moment after the request returns.

## Command line

The Bruno CLI (`npm install -g @usebruno/cli`) can run a folder. Runtime variables do not survive between separate
CLI invocations, so run login and flow in one call and give the codes and challenge IDs on the command line
(the `request OTP` calls need to happen first, so the CLI is less convenient than the app for this collection):

```bash
cd api/bruno
bru run 00-login/02-poster-verify-otp.bru 00-login/04-talent-verify-otp.bru 00-login/06-third-verify-otp.bru \
  01-happy-flow 02-failures --env local \
  --env-var posterChallenge=... --env-var posterOtp=... \
  --env-var talentChallenge=... --env-var talentOtp=... \
  --env-var thirdChallenge=... --env-var thirdOtp=...
```

(`challengeId` is in the response of each `request OTP`.)
