package com.gigwork.core.identity.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Counts the SQL statements the application prepares or executes, to prove a list endpoint's query count does not
 * grow with the page (no N+1). Counts one per prepareStatement / createStatement / prepareCall on any connection.
 */
public final class SqlCounter {

	private static final AtomicLong COUNT = new AtomicLong();

	private SqlCounter() {
	}

	/** The number of statements prepared while {@code work} runs (assumes nothing else is using the database). */
	public static long during(Runnable work) {
		long before = COUNT.get();
		work.run();
		return COUNT.get() - before;
	}

	/** Wraps the application's DataSource so every statement is counted. */
	static BeanPostProcessor wrapper() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String name) {
				if (bean instanceof DataSource ds && !Proxy.isProxyClass(bean.getClass())) {
					return Proxy.newProxyInstance(bean.getClass().getClassLoader(), new Class<?>[] { DataSource.class },
							(proxy, method, args) -> {
								Object result = invoke(ds, method, args);
								return "getConnection".equals(method.getName()) ? connection((Connection) result) : result;
							});
				}
				return bean;
			}
		};
	}

	private static Connection connection(Connection real) {
		return (Connection) Proxy.newProxyInstance(real.getClass().getClassLoader(), new Class<?>[] { Connection.class },
				(proxy, method, args) -> {
					String n = method.getName();
					if (n.equals("prepareStatement") || n.equals("createStatement") || n.equals("prepareCall")) {
						COUNT.incrementAndGet();
					}
					return invoke(real, method, args);
				});
	}

	private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
		try {
			return method.invoke(target, args);
		}
		catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

}
