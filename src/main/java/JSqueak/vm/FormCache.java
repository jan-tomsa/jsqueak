package jsqueak.vm;

public class FormCache {
	private SqueakVM vm;
	private SqueakObject squeakForm;
	private int[] bits;
	private int width;
	private int height;
	private int depth;
	private boolean msb;
	private int pixPerWord;
	private int pitch; // aka raster

	FormCache(SqueakVM squeakVM) {
		this.vm = squeakVM;
	}

	FormCache(SqueakVM squeakVM, SqueakObject obj) {
		this.vm = squeakVM;
		this.loadFrom(obj);
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public boolean loadFrom(Object aForm) {
		// We do not reload if this is the same form as before
		if (getSqueakForm() == aForm)
			return true;
		squeakForm = null; // Marks this as failed until very end...
		if (SqueakVM.isSmallInt(aForm))
			return false;
		Object[] formPointers = ((SqueakObject) aForm).pointers;
		if (formPointers == null || formPointers.length < 4)
			return false;
		for (int i = 1; i < 4; i++)
			if (!SqueakVM.isSmallInt(formPointers[i]))
				return false;
		Object bitsObject = formPointers[0];
		setWidth(SqueakVM.intFromSmall(((Integer) formPointers[1])));
		setHeight(SqueakVM.intFromSmall(((Integer) formPointers[2])));
		setDepth(SqueakVM.intFromSmall(((Integer) formPointers[3])));
		if ((getWidth() < 0) || (getHeight() < 0))
			return false;
		if (bitsObject == vm.nilObj || SqueakVM.isSmallInt(bitsObject))
			return false;
		setMsb(getDepth() > 0);
		if (getDepth() < 0)
			setDepth(0 - getDepth());
		Object maybeBytes = ((SqueakObject) bitsObject).getBits();
		if (maybeBytes == null || maybeBytes instanceof byte[])
			return false; // Happens with compressed bits
		setBits((int[]) maybeBytes);
		setPixPerWord(32 / getDepth());
		setPitch((getWidth() + (getPixPerWord() - 1)) / getPixPerWord());
		if (getBits().length != (getPitch() * getHeight()))
			return false;
		squeakForm = (SqueakObject) aForm; // Only now is it marked as OK
		return true;
	}

	public int getDepth() {
		return depth;
	}

	public void setDepth(int depth) {
		this.depth = depth;
	}

	public boolean isMsb() {
		return msb;
	}

	public void setMsb(boolean msb) {
		this.msb = msb;
	}

	public int getPixPerWord() {
		return pixPerWord;
	}

	public void setPixPerWord(int pixPerWord) {
		this.pixPerWord = pixPerWord;
	}

	public int getPitch() {
		return pitch;
	}

	public void setPitch(int pitch) {
		this.pitch = pitch;
	}

	public int[] getBits() {
		return bits;
	}

	public void setBits(int[] bits) {
		this.bits = bits;
	}

	public SqueakObject getSqueakForm() {
		return squeakForm;
	}
}
