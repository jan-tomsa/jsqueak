package JSqueak.display;

import JSqueak.io.Keyboard;
import JSqueak.vm.SqueakVM;

public interface ScreenFactory {
	public Screen createScreen(String title, int width, int height, int depth, 
			SqueakVM vmSema, Keyboard keyboard);
}
