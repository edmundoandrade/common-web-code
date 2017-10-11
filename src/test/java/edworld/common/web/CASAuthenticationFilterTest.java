package edworld.common.web;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CASAuthenticationFilterTest {
	@Test
	public void detectExtraPath() {
		assertEquals("selflearning/public",
				CASAuthenticationFilter.detectExtraPath("http://sciplace.org/selflearning/public").get());
		assertEquals("en/subjects.html",
				CASAuthenticationFilter.detectExtraPath("http://sciplace.org:8080/en/subjects.html").get());
	}
}
