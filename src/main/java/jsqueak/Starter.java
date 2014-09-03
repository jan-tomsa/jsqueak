/*
Starter.java
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

package jsqueak;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import jsqueak.display.ScreenFactory;
import jsqueak.display.impl.ScreenFactoryImpl;
import jsqueak.image.SqueakImage;
import jsqueak.io.KeyboardFactory;
import jsqueak.monitor.Monitor;
import jsqueak.monitor.MonitorFrame;
import jsqueak.io.impl.KeyboardFactoryImpl;
import jsqueak.vm.SqueakVM;

public class Starter {
    private static final String MINI_IMAGE_FILE_NAME = "mini.image.gz";
    private static Monitor monitor = null;
    
    private static SqueakImage locateStartableImageAndLoadIt() throws IOException {
	    monitor.logMessage("Attempting to load the default image.");
        URL imageUrl = Starter.class.getResource( MINI_IMAGE_FILE_NAME );
	    if (imageUrl == null) {
		    throw new RuntimeException("Failed to load image at URL '"+ MINI_IMAGE_FILE_NAME +"'");
	    }
        if ( "file".equals( imageUrl.getProtocol() ) )
            return new SqueakImage( new File( imageUrl.getPath() ), monitor );
            
        InputStream ims = Starter.class.getResourceAsStream( MINI_IMAGE_FILE_NAME );
        if ( ims != null )
            return new SqueakImage(ims, monitor);
        
        throw new FileNotFoundException( "Cannot locate resource " + MINI_IMAGE_FILE_NAME );
    }

    private static SqueakImage locateSavedImageAndLoadIt( String pathname ) throws IOException {
	    monitor.logMessage("Attempting to load image '" + pathname + "'.");
        File saved = new File( pathname );
        if ( saved.exists() )
            return new SqueakImage( saved, monitor );

	    final String message = "Cannot locate image '" + pathname + "'";
	    monitor.logMessage(message);
	    throw new FileNotFoundException(message);
    }
    
    /**
     * @param args first arg may specify image file name
     */
    public static void main(String[] args) throws IOException, NullPointerException, java.lang.ArrayIndexOutOfBoundsException {
        monitor = new MonitorFrame();
        //SqueakVM.initSmallIntegerCache();
        SqueakImage img = args.length > 0 ? locateSavedImageAndLoadIt( args[0] )
                                          : locateStartableImageAndLoadIt();
        ScreenFactory screenFactory = new ScreenFactoryImpl();
		//monitorFrame.logMessage(MINI_IMAGE_FILE_NAME);
        KeyboardFactory keyboardFactory = new KeyboardFactoryImpl();		
        SqueakVM vm= new SqueakVM(img, monitor, screenFactory, keyboardFactory);
        vm.run(); 
    }

}
