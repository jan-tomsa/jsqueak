/*
Screen.java
Copyright (c) 2008  Daniel H. H. Ingalls, Sun Microsystems, Inc.  All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/

/*
 The author is indebted to Helge Horch for the outer framework of this VM.
 I knew nothing about how to write a Java program, and he provided the basic
 structure of main program, mouse input and display output for me.
 The object model, interpreter and primitives are all my design, but Helge
 continued to help whenever I was particularly stuck during the project.
*/

package JSqueak.display.impl;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.lang.reflect.InvocationTargetException;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import JSqueak.display.Screen;
import JSqueak.io.impl.KeyboardQueue;
import JSqueak.io.impl.MouseStatus;
import JSqueak.vm.SqueakVM;

public class ScreenImpl implements Screen {
    Dimension extent;
    private int depth;
    private JFrame frame;
    private JLabel display;
    private byte displayBits[];
    private MouseStatus mouseStatus;
    private KeyboardQueue keyboardQueue;
    private Timer heartBeat;
    private boolean screenChanged;
    private Object vmSemaphore;
    
    private final static boolean WITH_HEARTBEAT= false;
    private final static int FPS= 10;
    
    // cf. http://doc.novsu.ac.ru/oreilly/java/awt/ch12_02.htm
    private final static byte kComponents[] =
        new byte[]{ (byte)255, 0 , (byte)240, (byte)230, 
                    (byte)220, (byte)210, (byte)200, (byte)190, (byte)180, (byte)170,
                    (byte)160, (byte)150, 110, 70, 30, 10 };
    
    private final static ColorModel kBlackAndWhiteModel =
        new IndexColorModel(1, 2, kComponents, kComponents, kComponents);
    
    public ScreenImpl(String title, int width, int height, int depth, Object vmSema) {
        vmSemaphore= vmSema;
        this.extent= new Dimension(width, height);
        this.depth= depth;
        frame= new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content= new JPanel(new BorderLayout());
        Icon noDisplay= new Icon() {
            public int getIconWidth() {
                return extent.width; 
            }
            public int getIconHeight() {
                return extent.height; 
            }
            public void paintIcon(Component c, Graphics g, int x, int y) {}
        };
        display= new JLabel(noDisplay);
        display.setSize(extent);
        content.add(display, BorderLayout.CENTER);
        frame.setContentPane(content);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation((screen.width - extent.width)/2, (screen.height - extent.height)/2);   // center
        
        mouseStatus= new MouseStatus( (SqueakVM) vmSemaphore );
        display.addMouseMotionListener( mouseStatus );
        display.addMouseListener(mouseStatus);
        
        display.setFocusable(true);    // enable keyboard input
        keyboardQueue= new KeyboardQueue( (SqueakVM) vmSemaphore );
        display.addKeyListener( keyboardQueue );
        
        display.setOpaque(true);
        display.getRootPane().setDoubleBuffered(false);    // prevents losing intermediate redraws (how?!)
    }
    
    public JFrame getFrame() {
        return frame; 
    }
    
    public void setBits(byte rawBits[], int depth) {
        this.depth= depth;
        display.setIcon(createDisplayAdapter(displayBits= rawBits)); 
    }
    
    byte[] getBits() {
        return displayBits; 
    }
    
    protected Icon createDisplayAdapter(byte storage[]) {
        DataBuffer buf= new DataBufferByte(storage, (extent.height*extent.width/8)*depth);       // single bank
        SampleModel sm= new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, extent.width, extent.height, depth /* bpp */);
        WritableRaster raster= Raster.createWritableRaster(sm, buf, new Point(0, 0));
        Image image= new BufferedImage(kBlackAndWhiteModel, raster, true, null);
        return new ImageIcon(image); 
    }
    
    public void open() {
        frame.pack();
        frame.setVisible(true);
        if (WITH_HEARTBEAT) {
            heartBeat= new Timer(1000/FPS /* ms */, new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    // Swing timers execute on EHT
                    if (screenChanged) {
                        // could use synchronization, but lets rather paint too often
                        screenChanged= false;
                        Dimension extent= display.getSize();
                        display.paintImmediately(0, 0, extent.width, extent.height);
                        // Toolkit.getDefaultToolkit().beep();      // FIXME remove
                    }
                }
            } );
            heartBeat.start(); 
        }
    }
    
    public void close() {
        frame.setVisible(false);
        frame.dispose();
        if (WITH_HEARTBEAT)
            heartBeat.stop(); 
    }
    
    public void redisplay(boolean immediately, Rectangle area) {
        redisplay(immediately, area.x, area.y, area.width, area.height); 
    }
    
    public void redisplay(boolean immediately, final int cornerX, final int cornerY, final int width, final int height) {
        display.repaint(cornerX, cornerY, width, height);
        screenChanged= true; 
    }
    
    public void redisplay(boolean immediately) {
        display.repaint();
        screenChanged= true; 
    }
    
    protected boolean scheduleRedisplay(boolean immediately, Runnable trigger) {
        if (immediately) {
            try {
                SwingUtilities.invokeAndWait(trigger);
                return true; 
            } catch (InterruptedException e) {
                logRedisplayException(e); 
            } catch (InvocationTargetException e) {
                logRedisplayException(e);
            }
            return false; 
        } else {
            SwingUtilities.invokeLater(trigger);
            return true; 
        }
    }
    
    // extension point, default is ignorance
    protected void logRedisplayException(Exception e) {}
    
    private final static int Squeak_CURSOR_WIDTH= 16;
    private final static int Squeak_CURSOR_HEIGHT= 16;
    
    private final static byte C_WHITE= 0;
    private final static byte C_BLACK= 1;
    private final static byte C_TRANSPARENT= 2;
    private final static byte kCursorComponentX[]= new byte[] { -1, 0, 0 };
    private final static byte kCursorComponentA[]= new byte[] { -1, -1, 0 };
    
    protected Image createCursorAdapter(byte bits[], byte mask[]) {
        int bufSize= Squeak_CURSOR_HEIGHT*Squeak_CURSOR_WIDTH;
        DataBuffer buf= new DataBufferByte(new byte[bufSize], bufSize);
        // unpack samples and mask to bytes with transparency:
        int p= 0;
        for (int row= 0; row<Squeak_CURSOR_HEIGHT; row++) {
            for (int x=0; x<2; x++) {
                for (int col= 0x80; col!=0; col>>>= 1) {
                    if ((mask[(row*4)+x] & col) != 0)
                        buf.setElem(p++, (bits[(row*4)+x] & col) != 0? C_BLACK : C_WHITE);
                    else
                        buf.setElem(p++, C_TRANSPARENT); 
                }
            }
        }
        SampleModel sm= new SinglePixelPackedSampleModel(DataBuffer.TYPE_BYTE, Squeak_CURSOR_WIDTH, Squeak_CURSOR_HEIGHT, new int[]{ 255 });
        IndexColorModel cm= new IndexColorModel(8, 3, kCursorComponentX, kCursorComponentX, kCursorComponentX, kCursorComponentA);
        WritableRaster raster= Raster.createWritableRaster(sm, buf, new Point(0, 0));
        return new BufferedImage(cm, raster, false, null); 
    }
    
    protected byte[] extractBits(byte bitsAndMask[], int offset) 
    {
        final int n= bitsAndMask.length/2;  // 32 bytes -> 8 bytes
        byte answer[]= new byte[n];
        for (int i= 0; i<n; i++) {
            // convert incoming little-endian words to bytes:
            answer[i]= bitsAndMask[offset + i]; 
        }
        return answer; 
    }
    
    public void setCursor(byte imageAndMask[], int BWMask) {
        int n= imageAndMask.length;
        for(int i=0; i<n/2; i++) 
        {
            imageAndMask[i] ^= BWMask; // reverse cursor bits all the time
            imageAndMask[i+(n/2)] ^= BWMask;  // reverse transparency if display reversed
        }
        Toolkit tk= Toolkit.getDefaultToolkit();
        Dimension cx= tk.getBestCursorSize(Squeak_CURSOR_WIDTH, Squeak_CURSOR_HEIGHT);
        Cursor c;
        if (cx.width == 0 || cx.height == 0) {
            c= Cursor.getDefaultCursor(); 
        } else {
            Image ci= createCursorAdapter(extractBits(imageAndMask, 0), extractBits(imageAndMask, Squeak_CURSOR_HEIGHT*4));
            c= tk.createCustomCursor(ci, new Point(0, 0), "Smalltalk-78 cursor"); 
        }
        display.setCursor(c); 
    }
    
    public Dimension getExtent() {
        return display.getSize(); 
    }
    
    public void setExtent(Dimension extent) {
        display.setSize(extent);
        frame.setSize(extent); 
    }
    
    public Point getLastMousePoint() {
        return new Point(mouseStatus.getfX(), mouseStatus.getfY()); 
    }
    
    public int getLastMouseButtonStatus() {
        return ( mouseStatus.getfButtons() & 7 ) | keyboardQueue.modifierKeys();
    }
    
    public void setMousePoint(int x, int y) {
        Point origin= display.getLocationOnScreen();
        x+= origin.x;
        y+= origin.y;
        try 
        {
            Robot robot = new Robot();
            robot.mouseMove(x, y); 
        }
        catch (AWTException e) 
        {
            // ignore silently?
            System.err.println("Mouse move to " + x + "@" + y + " failed."); 
        }
    }
    
    public int keyboardPeek() {
        return keyboardQueue.peek(); 
    }
    
    public int keyboardNext() {
        //System.err.println("character code="+fKeyboardQueue.peek());
        return keyboardQueue.next(); 
    }
}
