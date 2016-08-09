package edworld.common.web;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class ServiceTest {
	private Service service;

	@Before
	public void setUp() {
		service = new Service() {
		};
	}

	@Test
	public void getCurrentPath() {
		assertEquals("", service.getCurrentPath(""));
		assertEquals("", service.getCurrentPath("/"));
		assertEquals("sys/", service.getCurrentPath("/sys"));
		assertEquals("", service.getCurrentPath("/sys/"));
		assertEquals("delete/", service.getCurrentPath("/sys/auth/entities/1/children/2/delete"));
	}

	@Test
	public void getRootPath() {
		assertEquals("../../", service.getRootPath("/auth/entities/1", ""));
		assertEquals("../../../", service.getRootPath("/auth/entities/1/", ""));
	}
}
