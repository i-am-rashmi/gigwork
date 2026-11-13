package com.gigwork.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.support.CapturingOtpSender;
import com.gigwork.core.identity.support.IdentityIntegrationTest;
import com.gigwork.core.identity.support.TestNumbers;
import com.jayway.jsonpath.JsonPath;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;

/**
 * NFR-46: logs never contain mobile numbers, OTP codes or tokens. This runs the whole journey (including failures)
 * with the application, security and web loggers at DEBUG and scans every log line and stack trace for the secrets
 * that were used. The development sender is not active here; it is the one sanctioned place that logs a code.
 */
@IdentityIntegrationTest
class LogScanTest {

	private static final List<String> LOGGERS = List.of("com.gigwork", "org.springframework.security",
			"org.springframework.web", "org.springframework.jdbc.core");

	@Autowired
	MockMvc mvc;
	@Autowired
	CapturingOtpSender sender;

	private String postJson(String url, String body) throws Exception {
		return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse()
				.getContentAsString();
	}

	@Test
	void theWholeJourneyLogsNoMobileNumberCodeOrToken() throws Exception {
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
		List<String> secrets = new ArrayList<>();
		try {
			MobileNumber mobile = TestNumbers.next();
			String national = mobile.value().substring(3);
			secrets.add(mobile.value());
			secrets.add(national);

			// a bad number, a code request, a wrong code, the right code, refresh (twice: the second must fail)
			postJson("/v1/auth/otp/request", "{\"mobile\":\"+14155550123\"}");
			String challenge = JsonPath.read(postJson("/v1/auth/otp/request", "{\"mobile\":\"" + national + "\"}"),
					"$.challengeId");
			String code = sender.lastCode(mobile);
			String wrong = code.equals("000000") ? "000001" : "000000";
			postJson("/v1/auth/otp/verify", json(mobile, challenge, wrong));
			String login = postJson("/v1/auth/otp/verify", json(mobile, challenge, code));
			String access = JsonPath.read(login, "$.accessToken");
			String refresh = JsonPath.read(login, "$.refreshToken");
			String refreshed = postJson("/v1/auth/token/refresh", "{\"refreshToken\":\"" + refresh + "\"}");
			postJson("/v1/auth/token/refresh", "{\"refreshToken\":\"" + refresh + "\"}");
			mvc.perform(get("/actuator/info").header("Authorization", "Bearer " + access));
			mvc.perform(get("/actuator/info").header("Authorization", "Bearer " + access + "tampered"));
			secrets.add(access);
			secrets.add(refresh);
			secrets.add(JsonPath.read(refreshed, "$.accessToken"));
			secrets.add(JsonPath.read(refreshed, "$.refreshToken"));

			List<String> lines = appender.list.stream().map(LogScanTest::asText).toList();
			assertThat(lines).as("the scan must be live: DEBUG logging produces lines").isNotEmpty();
			for (String secret : secrets) {
				assertThat(lines).as("a log line contains a secret of length %d", secret.length())
						.noneMatch(line -> line.contains(secret));
			}
			Pattern codeAsWord = Pattern.compile("(?<![0-9A-Za-z])" + code + "(?![0-9A-Za-z])");
			assertThat(lines).as("a log line contains the OTP code").noneMatch(line -> codeAsWord.matcher(line).find());
		}
		finally {
			for (int i = 0; i < loggers.size(); i++) {
				loggers.get(i).detachAppender(appender);
				loggers.get(i).setLevel(before.get(i));
			}
		}
	}

	private static String json(MobileNumber mobile, String challenge, String code) {
		return "{\"mobile\":\"" + mobile.value() + "\",\"challengeId\":\"" + challenge + "\",\"code\":\"" + code + "\"}";
	}

	private static String asText(ILoggingEvent event) {
		String stack = event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy());
		return event.getFormattedMessage() + " " + event.getMDCPropertyMap() + " " + stack;
	}

}
