package jsqueak.vm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SqueakVMMathOperationsTest {

	@Test
	public void testDivByZero() throws Exception {
		int result = SqueakMath.div(1, 0);
		assertEquals(SqueakMath.NON_SMALL_INT,result);
	}

	@Test
	public void testDivByOne() throws Exception {
		int result = SqueakMath.div(10, 1);
		assertEquals(10,result);
	}

	@Test
	public void testDivByTwo() throws Exception {
		int result = SqueakMath.div(10, 2);
		assertEquals(5,result);
	}

	@Test
	public void testQuickDivideByZero() throws Exception {
		int result = SqueakMath.quickDivide(1, 0);
		assertEquals(SqueakMath.NON_SMALL_INT,result);
	}

	@Test
	public void testQuickDivideByOne() throws Exception {
		int result = SqueakMath.quickDivide(10, 1);
		assertEquals(10,result);
	}

	@Test
	public void testQuickDivideByTwo() throws Exception {
		int result = SqueakMath.quickDivide(10, 2);
		assertEquals(5,result);
	}

	@Test
	public void testQuickDivideNonDivisible() throws Exception {
		int result = SqueakMath.quickDivide(10, 3);
		assertEquals(SqueakMath.NON_SMALL_INT,result);
	}

	@Test
	public void testMod() throws Exception {
		int result = SqueakMath.mod(10, 3);
		assertEquals(1,result);
	}

	@Test
	public void testSafeMultiply() throws Exception {
		int result = SqueakMath.safeMultiply(2, 3);
		assertEquals(6,result);
	}

	@Test
	public void testSafeShift() throws Exception {
		int result = SqueakMath.safeShift(4, 2);
		assertEquals(16,result);
	}
}
