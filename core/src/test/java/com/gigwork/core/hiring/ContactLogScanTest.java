package com.gigwork.core.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.MutableClock;
import com.gigwork.core.identity.support.TestAccounts;
import com.gigwork.core.identity.support.TestAccounts.TestAccount;
import com.gigwork.core.reference.ReferenceData;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;

/**
 * NFR-46: logs never contain mobile numbers. This runs the whole contact journey (choose methods, shortlist, consent,
 * both views, refused views, removal) with the application, security and web loggers at DEBUG and scans every log
 * line and stack trace for the two numbers that were revealed.
 */
@IdentityIntegrationTest
class ContactLogScanTest {

	private static final List<String> LOGGERS = List.of("com.gigwork", "org.springframework.security", "org.springframework.web",
			"org.springframework.jdbc.core", "org.springframework.transaction");

	@Autowired
	MockMvc mvc;
	@Autowired
	ReferenceData reference;
	@Autowired
	TestAccounts accounts;
	@Autowired
	MutableClock clock;

	@Test
	void theWholeContactJourneyLogsNoMobileNumber() throws Exception {
		clock.reset();
		HiringApiSupport api = new HiringApiSupport(mvc, reference, accounts, clock);
		TestAccount poster = accounts.create();
		TestAccount talent = api.talent("Asha", "photographer");
		UUID gig = api.openGig(poster);
		UUID application = api.applyOk(talent, gig);

		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		List<Logger> loggers = new ArrayList<>();
		List<Level> before = new ArrayList<>();
		for (String name : LOGGERS) {
			Logger logger = (Logger) LoggerFactory.getLogger(name);
			loggers.add(logger);
			before.add(logger.getLevel());
			logger.setLevel(Level.DEBUG);
			logger.addAppender(appender);
		}
		try {
			api.sharesContact(poster);
			api.sharesContact(talent);
			api.talentContact(poster, application).andExpect(status().isConflict()); // not shortlisted yet
			api.shortlist(poster, application, true).andExpect(status().isOk());
			api.consent(talent, application, true).andExpect(status().isOk());
			api.talentContact(poster, application).andExpect(status().isOk());
			api.posterContact(talent, application).andExpect(status().isOk());
			api.removeFromShortlist(poster, application).andExpect(status().isNoContent());
			api.talentContact(poster, application).andExpect(status().isConflict());

			List<String> lines = appender.list.stream().map(ContactLogScanTest::asText).toList();
			assertThat(lines).as("the scan must be live: DEBUG logging produces lines").isNotEmpty();
			for (TestAccount account : List.of(poster, talent)) {
				String mobile = accounts.mobileOf(account.id());
				for (String secret : List.of(mobile, mobile.substring(3))) {
					assertThat(lines).as("a log line contains a mobile number").noneMatch(line -> line.contains(secret));
				}
			}
		}
		finally {
			for (int i = 0; i < loggers.size(); i++) {
				loggers.get(i).detachAppender(appender);
				loggers.get(i).setLevel(before.get(i));
			}
		}
	}

	private static String asText(ILoggingEvent event) {
		String stack = event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy());
		return event.getFormattedMessage() + " " + event.getMDCPropertyMap() + " " + stack;
	}

}
