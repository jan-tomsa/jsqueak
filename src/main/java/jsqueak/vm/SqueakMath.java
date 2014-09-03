package jsqueak.vm;

public class SqueakMath {
	public static final int NON_SMALL_INT = -0x50000000; //non-small and neg(so non pos32 too)

	// Java rounds toward zero, we also need towards -infinity, so...
	public static int div(int rcvr, int arg) {
		// do this without floats asap
		if (arg == 0)
			return NON_SMALL_INT; // fail if divide by zero
		return (int) Math.floor((double) rcvr / arg);
	}

	public static int quickDivide(int rcvr, int arg) {
		// only handles exact case
		if (arg == 0)
			return NON_SMALL_INT; // fail if divide by zero
		int result = rcvr / arg;
		if (result * arg == rcvr)
			return result;
		return NON_SMALL_INT; // fail if result is not exact
	}

	public static int mod(int rcvr, int arg) {
		if (arg == 0)
			return NON_SMALL_INT; // fail if divide by zero
		return rcvr - div(rcvr, arg) * arg;
	}

	public static int safeMultiply(int multiplicand, int multiplier) {
		int product = multiplier * multiplicand;
		// check for overflow by seeing if computation is reversible
		if (multiplier == 0)
			return product;
		if ((product / multiplier) == multiplicand)
			return product;
		return NON_SMALL_INT; // non-small result will cause failure
	}

	public static int safeShift(int bitsToShift, int shiftCount) {
		if (shiftCount < 0)
			return bitsToShift >> -shiftCount; // OK ot lose bits shifting right
		// check for lost bits by seeing if computation is reversible
		int shifted = bitsToShift << shiftCount;
		if ((shifted >>> shiftCount) == bitsToShift)
			return shifted;
		return NON_SMALL_INT; // non-small result will cause failure
	}
}
