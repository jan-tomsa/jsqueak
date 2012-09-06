package JSqueak.image;

import java.io.DataInput;
import java.io.IOException;
import java.util.Hashtable;

import JSqueak.Squeak;
import JSqueak.util.HexUtils;
import JSqueak.vm.SqueakObject;

class SqueakImageReader {
	SqueakImageHeader imageHeader = null;
	DataInput in = null;
	
	public SqueakImageReader(DataInput in) {
		this.in = in;
	}

	public SqueakImageHeader readImageHeader() throws IOException {
		imageHeader = new SqueakImageHeader();
		imageHeader.doSwap = determineEndianness();
        System.err.println("version passes with swap= " + imageHeader.doSwap);
        imageHeader.headerSize= intFromInputSwapped();
        imageHeader.endOfMemory= intFromInputSwapped(); //first unused location in heap
        imageHeader.oldBaseAddr= intFromInputSwapped(); //object memory base address of image
        imageHeader.specialObjectsOopInt= intFromInputSwapped(); //oop of array of special oops
        imageHeader.lastHash= intFromInputSwapped(); //Should be loaded from, and saved to the image header
        imageHeader.savedWindowSize= intFromInputSwapped();
        imageHeader.fullScreenFlag= intFromInputSwapped();
        imageHeader.extraVMMemory= intFromInputSwapped();
        in.skipBytes(imageHeader.headerSize - (9*4)); //skip to end of header
        return imageHeader;
	}

	private boolean determineEndianness() throws IOException {
		int version= in.readInt();
        if (version != 6502) 
        {
            version= swapInt(version);
            if (version != 6502)
                throw new IOException("bad image version");
            return true; 
        }
		return false;
	}
    
    private int intFromInputSwapped() throws IOException 
    {
        // Return an int from stream 'in', swizzled if doSwap is true
        if (imageHeader.doSwap) 
            return swapInt(in.readInt());
        else 
            return in.readInt(); 
    }
    
    private int swapInt(int toSwap) 
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
        
	public Hashtable readObjects(SqueakImage objectRegistry) throws IOException {
        Hashtable oopMap= new Hashtable(30000);
		for (int i= 0; i<imageHeader.endOfMemory;) {
            int dataLength = 0;
            int classInt = 0;
            int[] data;
            int objectHeader = intFromInputSwapped();
            switch (objectHeader & Squeak.HEADER_TYPE_MASK) {
                case Squeak.HEADER_TYPE_SIZE_AND_CLASS:
                    dataLength= objectHeader>>2;
                    classInt= intFromInputSwapped() - Squeak.HEADER_TYPE_SIZE_AND_CLASS;
                    objectHeader= intFromInputSwapped();
                    i += 12;
                    break;
                case Squeak.HEADER_TYPE_CLASS:
                    classInt= objectHeader - Squeak.HEADER_TYPE_CLASS;
                    objectHeader= intFromInputSwapped();
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
            	data[j]= intFromInputSwapped();
            }
            //String rawDataChunk = HexUtils.translateRawData(data);
            //monitor.logMessage(rawDataChunk);
            i += dataLength*4;
            
            SqueakObject squeakObject= new SqueakObject(Integer.valueOf(classInt),(short)format,(short)hash,data);
            objectRegistry.registerObject(squeakObject);
            //oopMap is from old oops to new objects
            //Why can't we use ints as keys??...
            oopMap.put(Integer.valueOf(baseAddr+imageHeader.oldBaseAddr),squeakObject); 
        }
		return oopMap;
	}

}
