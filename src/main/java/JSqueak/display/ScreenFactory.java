package jsqueak.display;

import jsqueak.io.Keyboard;
import jsqueak.vm.SqueakVM;

public interface ScreenFactory {
	public Screen createScreen(String title, int width, int height, int depth, 
			SqueakVM vmSema, Keyboard keyboard);
}
