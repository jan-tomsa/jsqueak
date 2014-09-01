package jsqueak.io;

import jsqueak.vm.SqueakVM;

public interface KeyboardFactory {

	Keyboard createKeyboard(SqueakVM vmSema);

}
