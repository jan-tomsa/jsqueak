package JSqueak;

import java.awt.event.MouseEvent;

import javax.swing.event.MouseInputAdapter;

public class MouseStatus extends MouseInputAdapter 
{
    private final SqueakVM fSqueakVM;
    
    private int fX;

	private int fY;
    private int fButtons;
    
    private final static int RED = 4;
    private final static int YELLOW = 2;
    private final static int BLUE = 1;
    
    public MouseStatus( SqueakVM squeakVM )
    {
        fSqueakVM = squeakVM;
    }
    
    private int mapButton(MouseEvent evt) 
    {
        switch (evt.getButton()) 
        {
            case MouseEvent.BUTTON1:
                if (evt.isControlDown()) 
                    return YELLOW;
                if (evt.isAltDown()) 
                    return BLUE;
                return RED;
            case MouseEvent.BUTTON2:    return BLUE;        // middle (frame menu)
            case MouseEvent.BUTTON3:    return YELLOW;  // right (pane menu)
            case MouseEvent.NOBUTTON:   return 0;
        }
        throw new RuntimeException("unknown mouse button in event"); 
    }
    
    public void mouseMoved(MouseEvent evt) 
    {
        setfX(evt.getX());
        setfY(evt.getY());
        fSqueakVM.wakeVM(); 
    }
    
    public void mouseDragged(MouseEvent evt) 
    {
        setfX(evt.getX());
        setfY(evt.getY());
        fSqueakVM.wakeVM(); 
    }
    
    public void mousePressed(MouseEvent evt) 
    {
        setfButtons(getfButtons() | mapButton(evt));
        fSqueakVM.wakeVM(); 
    }
    
    public void mouseReleased(MouseEvent evt) 
    {
        setfButtons(getfButtons() & ~mapButton(evt));
        fSqueakVM.wakeVM();
    }

	public int getfX() {
		return fX;
	}

	public void setfX(int fX) {
		this.fX = fX;
	}

	public int getfY() {
		return fY;
	}

	public void setfY(int fY) {
		this.fY = fY;
	}

	public int getfButtons() {
		return fButtons;
	}

	public void setfButtons(int fButtons) {
		this.fButtons = fButtons;
	}
}