package edworld.common.web;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WebGenServiceTest {
	@Test
	public void resolverSelectedOption() {
		WebGenService service = new WebGenService();
		String field1 = "<select name=\"tipo1\" value=\"V01\"><option value=\"V01\">First</option></select>";
		String field2 = "<select name=\"tipo2\" value=\"V01\"><option value=\"V01\">First</option></select>";
		String resolved1 = "<select name=\"tipo1\"><option value=\"V01\" selected>First</option></select>";
		String resolved2 = "<select name=\"tipo2\"><option value=\"V01\" selected>First</option></select>";
		assertEquals(resolved1, service.solveSelectedOption(field1));
		assertEquals(resolved2, service.solveSelectedOption(field2));
		assertEquals(resolved1 + resolved2, service.solveSelectedOption(field1 + field2));
	}
}
