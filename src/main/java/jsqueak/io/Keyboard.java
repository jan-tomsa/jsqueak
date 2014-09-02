package jsqueak.io;

import java.awt.event.KeyListener;

public interface Keyboard extends KeyListener {
	int keyboardPeek();
	int keyboardNext();
	int modifierKeys();
}
