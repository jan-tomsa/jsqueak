package JSqueak.display;

import javax.swing.JFrame;

public interface Screen {
	public JFrame getFrame();
	public void setBits(byte rawBits[], int depth);
	public void open();
	public void close();
}
