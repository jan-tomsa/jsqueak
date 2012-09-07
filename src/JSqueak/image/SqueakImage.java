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
    
    SqueakImageHeader imageHeader;
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
    
    private static class NonObjectsInSourceArray extends RuntimeException {
		private static final long serialVersionUID = -5448204393408280585L;
	};
    
	private static class RepeatedObjectsInSourceArray extends RuntimeException {
		private static final long serialVersionUID = 4226255506291014244L;
	}
	
	private static class NonObjectsInTargetArray extends RuntimeException {
		private static final long serialVersionUID = 7990715745242510056L;
	}

	private static class RepeatedObjectsInTargetArray extends RuntimeException {
		private static final long serialVersionUID = -5226177918479481765L;
	}
	
	private static class SourceAndTargedHaveDifferentLengths extends RuntimeException {
		private static final long serialVersionUID = 4948074590011014722L;
	}
	
    public void bulkBecome(Object[] sourceObjects, Object[] targetClasses) 
    {
    	verifySameLengths(sourceObjects, targetClasses);
    	int length = sourceObjects.length;
		Hashtable mutations= new Hashtable(length*4);
		setupMutationsSourceToTarget(sourceObjects, targetClasses, length, mutations);
        mutateClasses(mutations,objectTable,otMaxUsed);
    }

    public void bulkBecomeTwoWay(Object[] sourceObjects, Object[] targetClasses) 
    {
    	verifySameLengths(sourceObjects, targetClasses);
    	int length = sourceObjects.length;
		Hashtable mutations= new Hashtable(length*4*2);
		setupMutationsSourceToTarget(sourceObjects, targetClasses, length, mutations);
		setupMutationsTargetToSource(sourceObjects, targetClasses, length, mutations);
        mutateClasses(mutations,objectTable,otMaxUsed);
    }

	private void verifySameLengths(Object[] sourceObjects, Object[] targetClasses) {
        if (sourceObjects.length != targetClasses.length) 
            throw new SourceAndTargedHaveDifferentLengths();
	}

	private void mutateClasses(Hashtable mutations, WeakReference[] stObjectTable, int objectTableLength) {
		for(int i=0; i<=objectTableLength; i++) {
		    // Now, for every object...
		    SqueakObject object = (SqueakObject)stObjectTable[i].get();
		    if (object != null) {
		        // mutate the class
		    	Object sqClass = (SqueakObject)mutations.get(object.getSqClass());
		        if (sqClass != null)
		            object.setSqClass( sqClass ); 
		        Object body[] = object.getPointers();
		        if (body != null) {
		            // and mutate body pointers
		            for(int j=0; j<body.length; j++) {
		            	Object ptr = body[j];
		                sqClass = mutations.get(ptr);
		                if (sqClass != null) 
		                    body[j]= sqClass; 
		            }
		        }
		    }
		}
	}

	private static void setupMutationsTargetToSource(Object[] sourceObjects,
			Object[] targetClasses, int length, Hashtable mutations) {
		for(int i=0; i<length; i++) {
			Object targetClass= targetClasses[i];
		    if (!(targetClass instanceof SqueakObject)) 
		    	throw new NonObjectsInTargetArray();  //non-objects in to array
		    if (mutations.get(targetClass) != null) 
		        throw new RepeatedObjectsInTargetArray(); //repeated oops in to array
		    else 
		        mutations.put(targetClass,sourceObjects[i]); 
		}
	}

	private static void setupMutationsSourceToTarget(Object[] sourceObjects,
			Object[] targetClasses, int length, Hashtable mutations) {
		for(int i=0; i<length; i++) {
			Object sourceObj = sourceObjects[i];
		    if (!(sourceObj instanceof SqueakObject)) 
		        throw new NonObjectsInSourceArray();  //non-objects in from array
		    if (mutations.get(sourceObj) != null) 
		        throw new RepeatedObjectsInSourceArray(); //repeated oops in from array
		    else 
		        mutations.put(sourceObj,targetClasses[i]); 
		}
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
    
    private void readImage(DataInput in) throws IOException 
    {
        System.out.println("Start reading at " + System.currentTimeMillis());
        monitor.logMessage("Start reading image at " + System.currentTimeMillis());
        monitor.setStatus("Reading image");
        
        objectTable = new WeakReference[OT_MIN_SIZE];
        otMaxUsed= -1;
        
        SqueakImageReader reader = new SqueakImageReader(in);
        
        // Read image header
        imageHeader = reader.readImageHeader();
        
        // Read objects 
        Hashtable<Integer, SqueakObject> oopMap = reader.readObjects(this);
        
        //Temp version of special objects needed for makeCompactClassesArray; not a good object yet
        SqueakObject specialObjectsArrayOop = oopMap.get(Integer.valueOf(imageHeader.specialObjectsOopInt));
        int[] soaByteCode = (int[]) specialObjectsArrayOop.getBits();
        String soaByteCodeHex = HexUtils.translateRawData(soaByteCode);
        monitor.logMessage("Special objects bytecode: " + soaByteCodeHex);
        setSpecialObjectsArray(specialObjectsArrayOop);
        
        Integer[] ccArray= makeCompactClassesArray(oopMap,getSpecialObjectsArray());
        
        int oldOop= getSpecialObjectsArray().oldOopAt(Squeak.splOb_ClassFloat);
        SqueakObject floatClass= oopMap.get(Integer.valueOf(oldOop));
        
        monitor.setStatus("Installing");
        System.out.println("Start installs at " + System.currentTimeMillis());
        monitor.logMessage("Start installs at " + System.currentTimeMillis());
        for (int i= 0; i<otMaxUsed; i++) {
        	SqueakObject squeakObject = (SqueakObject) objectTable[i].get();
        	monitor.logMessage("Installing: "+squeakObject.getHash());
        	squeakObject.install(oopMap,ccArray,floatClass); 
        }
        
        System.out.println("Done installing at " + System.currentTimeMillis());
        monitor.logMessage("Done installing at " + System.currentTimeMillis());
        
        //Proper version of special objects -- it's a good object
        setSpecialObjectsArray(oopMap.get(Integer.valueOf(imageHeader.specialObjectsOopInt)));
        otMaxOld= otMaxUsed; 
    }

    private Integer[] makeCompactClassesArray(Hashtable<Integer, SqueakObject> oopMap, SqueakObject splObs) 
    {
        //Makes an array of the compact classes as oldOops (still need to be mapped)
        int oldOop= splObs.oldOopAt(Squeak.splOb_CompactClasses);
        SqueakObject compactClassesArray= oopMap.get(new Integer(oldOop));
        Integer[] ccArray= new Integer[31];
        for (int i= 0; i<31; i++) {
            ccArray[i]= Integer.valueOf(compactClassesArray.oldOopAt(i)); 
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
