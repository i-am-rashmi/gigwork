package com.gigwork.core.identity.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.gigwork.core.identity.domain.MobileNumber;
import com.gigwork.core.identity.domain.OtpDeliveryUnavailableException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** Which OtpSender exists is decided by configuration alone, and the dev sender logs on its own logger. */
class OtpSenderTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(UnavailableOtpSender.class, DevLoggingOtpSender.class);

	@Test
	void withoutConfigurationOnlyTheUnavailableSenderExists() {
		runner.run(context -> {
			assertThat(context).hasSingleBean(OtpSender.class);
			assertThat(context).hasSingleBean(UnavailableOtpSender.class);
		});
	}

	@Test
	void noneSelectsTheUnavailableSender() {
		runner.withPropertyValues("gigwork.identity.otp.sender=none").run(context -> {
			assertThat(context).hasSingleBean(OtpSender.class);
			assertThat(context).hasSingleBean(UnavailableOtpSender.class);
		});
	}

	@Test
	void devSelectsOnlyTheDevSender() {
		runner.withPropertyValues("gigwork.identity.otp.sender=dev").run(context -> {
			assertThat(context).hasSingleBean(OtpSender.class);
			assertThat(context).hasSingleBean(DevLoggingOtpSender.class);
			assertThat(context).doesNotHaveBean(UnavailableOtpSender.class);
		});
	}

	@Test
	void theUnavailableSenderFailsInsteadOfDroppingTheCode() {
		assertThatThrownBy(() -> new UnavailableOtpSender().send(MobileNumber.parse("9876543210"), "123456"))
				.isInstanceOf(OtpDeliveryUnavailableException.class);
	}

	@Test
	void theDevSenderLogsTheCodeOnItsOwnLoggerWithTheNumberMasked() {
		Logger logger = (Logger) LoggerFactory.getLogger(DevLoggingOtpSender.LOGGER_NAME);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		Level before = logger.getLevel();
		logger.setLevel(Level.INFO);
		try {
			new DevLoggingOtpSender().send(MobileNumber.parse("9876543210"), "123456");

			assertThat(appender.list).hasSize(1);
			String message = appender.list.get(0).getFormattedMessage();
			assertThat(message).contains("123456").contains("+91******3210").doesNotContain("9876543210");
			assertThat(appender.list.get(0).getLoggerName()).isEqualTo("gigwork.dev.otp");
		}
		finally {
			logger.detachAppender(appender);
			logger.setLevel(before);
		}
	}

}
