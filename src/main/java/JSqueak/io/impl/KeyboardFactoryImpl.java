package JSqueak.io.impl;

import JSqueak.io.Keyboard;
import JSqueak.io.KeyboardFactory;
import JSqueak.vm.SqueakVM;

public class KeyboardFactoryImpl implements KeyboardFactory {

	@Override
	public Keyboard createKeyboard(SqueakVM vmSema) {
		return new KeyboardQueue( vmSema );
	}

}
