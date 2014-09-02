package jsqueak.display.impl;

import jsqueak.display.Screen;
import jsqueak.display.ScreenFactory;
import jsqueak.io.Keyboard;
import jsqueak.vm.SqueakVM;

public class ScreenFactoryImpl implements ScreenFactory {

	@Override
	public Screen createScreen(String title, int width, int height, int depth,
			SqueakVM vmSema, Keyboard keyboard) {
		return new ScreenImpl(title, width, height, depth, vmSema, keyboard);
	}

}
