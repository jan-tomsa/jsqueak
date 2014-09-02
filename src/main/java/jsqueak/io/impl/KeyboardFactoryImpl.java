package jsqueak.io.impl;

import jsqueak.io.Keyboard;
import jsqueak.io.KeyboardFactory;
import jsqueak.vm.SqueakVM;

public class KeyboardFactoryImpl implements KeyboardFactory {

	@Override
	public Keyboard createKeyboard(SqueakVM vmSema) {
		return new KeyboardQueue( vmSema );
	}

}
