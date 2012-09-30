package JSqueak.display.impl;

import JSqueak.display.Screen;
import JSqueak.display.ScreenFactory;
import JSqueak.io.Keyboard;
import JSqueak.vm.SqueakVM;

public class ScreenFactoryImpl implements ScreenFactory {

	@Override
	public Screen createScreen(String title, int width, int height, int depth,
			SqueakVM vmSema, Keyboard keyboard) {
		return new ScreenImpl(title, width, height, depth, vmSema, keyboard);
	}

}
