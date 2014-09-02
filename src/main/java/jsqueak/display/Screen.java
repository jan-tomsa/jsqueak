package jsqueak.display;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.JFrame;

public interface Screen {
	public JFrame getFrame();
	public void setBits(byte rawBits[], int depth);
	public void open();
	public void close();
	public Dimension getExtent();
	public void setExtent(Dimension requestedExtent);
	public void setCursor(byte[] cursorBytes, int bWMask);
	public void redisplay(boolean immediately, Rectangle affectedArea);
	public Point getLastMousePoint();
	public int getLastMouseButtonStatus();
}
