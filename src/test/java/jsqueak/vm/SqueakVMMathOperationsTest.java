package jsqueak.vm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SqueakVMMathOperationsTest {

	@Test
	public void testDivByZero() throws Exception {
		int result = SqueakVM.div(1, 0);
		assertEquals(SqueakVM.NON_SMALL_INT,result);
	}

	@Test
	public void testDivByOne() throws Exception {
		int result = SqueakVM.div(10, 1);
		assertEquals(10,result);
	}

	@Test
	public void testDivByTwo() throws Exception {
		int result = SqueakVM.div(10, 2);
		assertEquals(5,result);
	}

	@Test
	public void testQuickDivideByZero() throws Exception {
		int result = SqueakVM.quickDivide(1, 0);
		assertEquals(SqueakVM.NON_SMALL_INT,result);
	}

	@Test
	public void testQuickDivideByOne() throws Exception {
		int result = SqueakVM.quickDivide(10, 1);
		assertEquals(10,result);
	}

	@Test
	public void testQuickDivideByTwo() throws Exception {
		int result = SqueakVM.quickDivide(10, 2);
		assertEquals(5,result);
	}

	@Test
	public void testQuickDivideNonDivisible() throws Exception {
		int result = SqueakVM.quickDivide(10, 3);
		assertEquals(SqueakVM.NON_SMALL_INT,result);
	}

	@Test
	public void testMod() throws Exception {
		int result = SqueakVM.mod(10, 3);
		assertEquals(1,result);
	}

	@Test
	public void testSafeMultiply() throws Exception {
		int result = SqueakVM.safeMultiply(2, 3);
		assertEquals(6,result);
	}

	@Test
	public void testSafeShift() throws Exception {
		int result = SqueakVM.safeShift(4, 2);
		assertEquals(16,result);
	}
}
