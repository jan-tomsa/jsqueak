/*
SqueakImage.java
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

package JSqueak.image;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Hashtable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import JSqueak.Squeak;
import JSqueak.monitor.Monitor;
import JSqueak.util.HexUtils;
import JSqueak.vm.SqueakObject;
import JSqueak.vm.SqueakVM;

/**
 * @author Daniel Ingalls
 *
 * A SqueakImage represents the complete state of a running Squeak.
 * This implemenatation uses Java objects (see SqueakObject) for all Squeak objects,
 * with direct pointers between them.  Enumeration is supported by objectTable,
 * which points weakly to all objects.  SmallIntegers are modeled by Java Integers.
 *
 * Some care is taken in reclaiming OT slots, to preserve the order of creation of objects,
 * as this matters for Squeak weak objects, should we ever support them.
 */

public class SqueakImage 
{
    private final String DEFAULT_IMAGE_NAME = "jsqueak.image";
    
    ImageHeader imageHeader;
    private SqueakVM vm;
    private WeakReference[] objectTable;
    private int otMaxUsed;
    private int otMaxOld;
    //private int lastHash;
    private int lastOTindex;
    
    private File imageFile;
    
    // FIXME: Access this through a method
    private SqueakObject specialObjectsArray;

	private Monitor monitor;
    
    public SqueakImage(InputStream raw, Monitor monitor ) throws IOException 
    {
    	this.monitor = monitor;
        imageFile = new File( System.getProperty( "user.dir" ),
                              DEFAULT_IMAGE_NAME );
        loadImage(raw); 
    }
    
    public SqueakImage( File fn, Monitor monitor ) throws IOException 
    {
    	this.monitor = monitor;
        imageFile = fn;
        loadImage(fn); 
    }
    
    public void save(File fn) throws IOException 
    {
        BufferedOutputStream fp= new BufferedOutputStream(new FileOutputStream(fn));
        GZIPOutputStream gz= new GZIPOutputStream(fp);
        DataOutputStream ser= new DataOutputStream(gz);
        writeImage(ser);
        ser.flush();
        ser.close();
        imageFile = fn;
    }

    public File imageFile()
    {
        return imageFile;
    }
    
    public void bindVM(SqueakVM theVM) 
    {
        vm = theVM; 
    }
    
    private void loadImage(InputStream raw) throws IOException 
    {
        BufferedInputStream inputStream= new BufferedInputStream(raw);
        GZIPInputStream gzippedInputStream= new GZIPInputStream(inputStream);
        DataInputStream dataInputStream= new DataInputStream(gzippedInputStream);
        readImage(dataInputStream); 
    }

    private void loadImage(File fn) throws IOException 
    {
        FileInputStream unbuffered= new FileInputStream(fn);
        loadImage(unbuffered);
        unbuffered.close(); 
    }
    
    public boolean bulkBecome(Object[] fromPointers, Object[] toPointers, boolean twoWay) 
    {
        int n= fromPointers.length;
        Object p, ptr, body[], mut;
        SqueakObject obj;
        if (n != toPointers.length) 
            return false;
        Hashtable mutations= new Hashtable(n*4*(twoWay?2:1));
        for(int i=0; i<n; i++) 
        {
            p= fromPointers[i];
            if (!(p instanceof SqueakObject)) 
                return false;  //non-objects in from array
            if (mutations.get(p) != null) 
                return false; //repeated oops in from array
            else 
                mutations.put(p,toPointers[i]); 
        }
        if (twoWay) 
        {
            for(int i=0; i<n; i++) 
            {
                p= toPointers[i];
                if (!(p instanceof SqueakObject)) 
                    return false;  //non-objects in to array
                if (mutations.get(p) != null) 
                    return false; //repeated oops in to array
                else 
                    mutations.put(p,fromPointers[i]); 
            }
        }
        for(int i=0; i<=otMaxUsed; i++) 
        {
            // Now, for every object...
            obj= (SqueakObject)objectTable[i].get();
            if (obj != null) 
            {
                // mutate the class
                mut = (SqueakObject)mutations.get(obj.getSqClass());
                if (mut != null)
                    obj.setSqClass( mut ); 
                if ((body= obj.getPointers()) != null)
                {
                    // and mutate body pointers
                    for(int j=0; j<body.length; j++) 
                    {
                        ptr= body[j];
                        mut= mutations.get(ptr);
                        if (mut != null) 
                            body[j]= mut; 
                    }
                }
            }
        }
        return true; 
    }

    //Enumeration...
    public SqueakObject nextInstance(int startingIndex, SqueakObject sqClass) 
    {
        //if sqClass is null, then find next object, else find next instance of sqClass
        for(int i=startingIndex; i<=otMaxUsed; i++) 
        {
            // For every object...
            SqueakObject obj= (SqueakObject)objectTable[i].get();
            if (obj != null && (sqClass==null | obj.getSqClass() == sqClass)) 
            {
                lastOTindex= i; // save hint for next scan
                return obj;
            }
        }
        return vm.nilObj;  // Return nil if none found
    }
    
    public int otIndexOfObject(SqueakObject lastObj) 
    {
        // hint: lastObj should be at lastOTindex
        SqueakObject obj= (SqueakObject)objectTable[lastOTindex].get(); 
        if (lastOTindex<=otMaxUsed && obj==lastObj) 
        {
            return lastOTindex;
        }
        else 
        {
            for(int i=0; i<=otMaxUsed; i++) 
            {
                // Alas no; have to find it again...
                obj= (SqueakObject)objectTable[i].get();
                if (obj == lastObj) 
                    return i; 
            }
        }
        return -1;  //should not happen
    }
    
    private final static int OT_MIN_SIZE=  500000;
    private final static int OT_MAX_SIZE= 1600000;
    private final static int OT_GROW_SIZE= 10000;

	private boolean doSwap;
    
    public short registerObject (SqueakObject obj) 
    {
        //All enumerable objects must be registered
        if ((otMaxUsed+1) >= objectTable.length)
            if (!getMoreOops(OT_GROW_SIZE))
                throw new RuntimeException("Object table has reached capacity");
        objectTable[++otMaxUsed]= new WeakReference(obj);
        imageHeader.lastHash= 13849 + (27181 * imageHeader.lastHash);
        return (short) (imageHeader.lastHash & 0xFFF); 
    }
    
    private boolean getMoreOops(int request) 
    {
        int nullCount;
        int startingOtMaxUsed= otMaxUsed;
        for(int i=0; i<5; i++) 
        {
            if (i==2) 
                vm.clearCaches(); //only flush caches after two tries
            partialGC();
            nullCount= startingOtMaxUsed - otMaxUsed;
            if (nullCount >= request)
                return true; 
        }
        
        // Sigh -- really need more space...
        int n= objectTable.length;
        if (n+request > OT_MAX_SIZE) 
        {
            fullGC();
            return false; 
        }
        System.out.println("Squeak: growing to " + (n+request) + " objects...");
        WeakReference newTable[]= new WeakReference[n+request];
        System.arraycopy(objectTable, 0, newTable, 0, n);
        objectTable= newTable;
        return true; 
    }
    
    public int partialGC() 
    {
        System.gc();
        otMaxUsed=reclaimNullOTSlots(otMaxOld);
        return spaceLeft(); 
    }

    public int spaceLeft() 
    {
        return (int)Math.min(Runtime.getRuntime().freeMemory(),(long)SqueakVM.maxSmallInt); 
    }

    public int fullGC() 
    {
        vm.clearCaches();
        for(int i=0; i<5; i++) partialGC();
        otMaxUsed=reclaimNullOTSlots(0);
        otMaxOld= Math.min(otMaxOld,otMaxUsed);
        return spaceLeft(); 
    }

    private int reclaimNullOTSlots(int start) 
    {
        // Java GC will null out slots in the weak Object Table.
        // This procedure compacts the occupied slots (retaining order),
        // and returns a new value for otMaxUsed.
        // If start=0, all are scanned (like full gc);
        // if start=otMaxOld it will skip the old objects (like gcMost).
        int oldOtMaxUsed= otMaxUsed;
        int writePtr= start;
        for(int readPtr= start; readPtr<=otMaxUsed; readPtr++)
            if (objectTable[readPtr].get() != null)
                objectTable[writePtr++]=objectTable[readPtr];
        if (writePtr==start) 
            return oldOtMaxUsed;
        return writePtr-1; 
    }
    
    private void writeImage (DataOutput ser) throws IOException 
    {
        // Later...
        throw new IOException( "Image saving is not implemented yet" );
    } 
    
    ///////////////////////////////////////////////////////////////////////////////////////////
    
    private class ImageHeader {
    	int headerSize;
    	int endOfMemory;
    	int oldBaseAddr;
    	int specialObjectsOopInt;
    	int lastHash;
    	int savedWindowSize;
    	int fullScreenFlag;
    	int extraVMMemory;
    }
    
    private void readImage(DataInput in) throws IOException 
    {
        System.out.println("Start reading at " + System.currentTimeMillis());
        monitor.logMessage("Start reading image at " + System.currentTimeMillis());
        monitor.setStatus("Reading image");
        
        objectTable = new WeakReference[OT_MIN_SIZE];
        otMaxUsed= -1;
        Hashtable oopMap= new Hashtable(30000);
        
        // Read image header
        readImageHeader(in);
        
        // Read objects 
        readObjects(in, oopMap);
        
        //Temp version of spl objs needed for makeCCArray; not a good object yet
        SqueakObject specialObjectsArrayOop = (SqueakObject)(oopMap.get(Integer.valueOf(imageHeader.specialObjectsOopInt)));
        int[] soaByteCode = (int[]) specialObjectsArrayOop.getBits();
        String soaByteCodeHex = HexUtils.translateRawData(soaByteCode);
        monitor.logMessage("Special objects bytecode: " + soaByteCodeHex);
        setSpecialObjectsArray(specialObjectsArrayOop);
        
        Integer[] ccArray= makeCCArray(oopMap,getSpecialObjectsArray());
        
        int oldOop= getSpecialObjectsArray().oldOopAt(Squeak.splOb_ClassFloat);
        SqueakObject floatClass= ((SqueakObject) oopMap.get(Integer.valueOf(oldOop)));
        
        monitor.setStatus("Installing");
        System.out.println("Start installs at " + System.currentTimeMillis());
        monitor.logMessage("Start installs at " + System.currentTimeMillis());
        for (int i= 0; i<otMaxUsed; i++) 
        {
        	SqueakObject squeakObject = (SqueakObject) objectTable[i].get();
        	monitor.logMessage("Installing: "+squeakObject.getHash());
            // Don't need oldBaseAddr here**
        	squeakObject.install(oopMap,ccArray,floatClass); 
            //((SqueakObject) objectTable[i].get()).install(oopMap,ccArray,floatClass);
        }
        
        System.out.println("Done installing at " + System.currentTimeMillis());
        monitor.logMessage("Done installing at " + System.currentTimeMillis());
        
        //Proper version of spl objs -- it's a good object
        setSpecialObjectsArray((SqueakObject)(oopMap.get(Integer.valueOf(imageHeader.specialObjectsOopInt))));
        otMaxOld= otMaxUsed; 
    }

	private void readObjects(DataInput in, Hashtable oopMap) throws IOException {
		for (int i= 0; i<imageHeader.endOfMemory;) {
            int dataLength = 0;
            int classInt = 0;
            int[] data;
            int objectHeader = intFromInputSwapped(in);
            switch (objectHeader & Squeak.HEADER_TYPE_MASK) {
                case Squeak.HEADER_TYPE_SIZE_AND_CLASS:
                    dataLength= objectHeader>>2;
                    classInt= intFromInputSwapped(in) - Squeak.HEADER_TYPE_SIZE_AND_CLASS;
                    objectHeader= intFromInputSwapped(in);
                    i += 12;
                    break;
                case Squeak.HEADER_TYPE_CLASS:
                    classInt= objectHeader - Squeak.HEADER_TYPE_CLASS;
                    objectHeader= intFromInputSwapped(in);
                    i += 8;
                    dataLength= (objectHeader>>2) & 63;
                    break;
                case Squeak.HEADER_TYPE_FREE_BLOCK:
                    throw new IOException("Unexpected free block");
                case Squeak.HEADER_TYPE_SHORT:
                    i += 4;
                    classInt= (objectHeader>>12) & 31; //compact class index
                    //Note classInt<32 implies compact class index
                    dataLength= (objectHeader>>2) & 63;
                    break;
            }
            int baseAddr = i - 4; //0-rel byte oop of this object (base header)
            dataLength--;  //length includes base header which we have already read
            int format= ((objectHeader>>8) & 15);
            int hash= ((objectHeader>>17) & 4095);
            
            // Note classInt and data are just raw data; no base addr adjustment and no Int conversion
            data= new int[dataLength];
            for (int j= 0; j<dataLength; j++) {
            	data[j]= intFromInputSwapped(in);
            }
            String rawDataChunk = HexUtils.translateRawData(data);
            monitor.logMessage(rawDataChunk);
            i += dataLength*4;
            
            SqueakObject squeakObject= new SqueakObject(Integer.valueOf(classInt),(short)format,(short)hash,data);
            registerObject(squeakObject);
            //oopMap is from old oops to new objects
            //Why can't we use ints as keys??...
            oopMap.put(Integer.valueOf(baseAddr+imageHeader.oldBaseAddr),squeakObject); 
        }
	}

	private void readImageHeader(DataInput in) throws IOException {
		imageHeader = new ImageHeader();
        doSwap = determineEndianness(in);
        System.err.println("version passes with swap= " + doSwap);
        imageHeader.headerSize= intFromInputSwapped(in);
        imageHeader.endOfMemory= intFromInputSwapped(in); //first unused location in heap
        imageHeader.oldBaseAddr= intFromInputSwapped(in); //object memory base address of image
        imageHeader.specialObjectsOopInt= intFromInputSwapped(in); //oop of array of special oops
        imageHeader.lastHash= intFromInputSwapped(in); //Should be loaded from, and saved to the image header
        imageHeader.savedWindowSize= intFromInputSwapped(in);
        imageHeader.fullScreenFlag= intFromInputSwapped(in);
        imageHeader.extraVMMemory= intFromInputSwapped(in);
        in.skipBytes(imageHeader.headerSize - (9*4)); //skip to end of header
	}

	private boolean determineEndianness(DataInput in) throws IOException {
		int version= in.readInt();
        if (version != 6502) 
        {
            version= swapInt(version);
            if (version != 6502)
                throw new IOException("bad image version");
            doSwap= true; 
        }
		return doSwap;
	}
    
    private int intFromInputSwapped (DataInput in) throws IOException 
    {
        // Return an int from stream 'in', swizzled if doSwap is true
        if (doSwap) 
            return swapInt(in.readInt());
        else 
            return in.readInt(); 
    }
    
    private int swapInt (int toSwap) 
    {
        // Return an int with byte order reversed
        int incoming= toSwap;
        int outgoing= 0;
        for (int i= 0; i<4; i++) 
        {
            int lowByte= incoming & 255;
            outgoing= (outgoing<<8) + lowByte;
            incoming= incoming>>8; 
        }
        return outgoing; 
    }
        
    private Integer[] makeCCArray(Hashtable oopMap, SqueakObject splObs) 
    {
        //Makes an aray of the complact classes as oldOops (still need to be mapped)
        int oldOop= splObs.oldOopAt(Squeak.splOb_CompactClasses);
        SqueakObject compactClassesArray= ((SqueakObject) oopMap.get(new Integer(oldOop)));
        Integer[] ccArray= new Integer[31];
        for (int i= 0; i<31; i++) 
        {
            ccArray[i]= new Integer (compactClassesArray.oldOopAt(i)); 
        }
        return ccArray; 
    }

	public SqueakObject getSpecialObjectsArray() {
		return specialObjectsArray;
	}

	public void setSpecialObjectsArray(SqueakObject specialObjectsArray) {
		this.specialObjectsArray = specialObjectsArray;
	}
}
