package JSqueak.image;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import JSqueak.Squeak;
import JSqueak.util.HexUtils;
import JSqueak.vm.SqueakObject;

public class SqueakImageReader {

	private DataInput in;
	private boolean doSwap;
    
	public SqueakImageReader(DataInput in) {
		this.in = in;
	}

	public ImageInfo readImage() throws IOException {
		ImageInfo imageInfo = new ImageInfo();
		
		doSwap = determineEndianness(in);
        System.err.println("version passes with swap= " + doSwap);
        int headerSize= intFromInputSwapped(in);
        int endOfMemory= intFromInputSwapped(in); //first unused location in heap
        int oldBaseAddr= intFromInputSwapped(in); //object memory base address of image
        imageInfo.setOldBaseAddr(oldBaseAddr);
        int specialObjectsOopInt= intFromInputSwapped(in); //oop of array of special oops
        imageInfo.setSpecialObjectsOopInt(specialObjectsOopInt);
        int lastHash= intFromInputSwapped(in); //Should be loaded from, and saved to the image header
        imageInfo.setLastHash(lastHash);
        int savedWindowSize= intFromInputSwapped(in);
        int fullScreenFlag= intFromInputSwapped(in);
        int extraVMMemory= intFromInputSwapped(in);
        in.skipBytes(headerSize - (9*4)); //skip to end of header
        
        for (int i= 0; i<endOfMemory;) {
            int nWords= 0;
            int classInt= 0;
            int[] data;
            int format= 0;
            int hash= 0;
            int header= intFromInputSwapped(in);
            switch (header & Squeak.HEADER_TYPE_MASK) {
                case Squeak.HEADER_TYPE_SIZE_AND_CLASS:
                    nWords= header>>2;
                    classInt= intFromInputSwapped(in) - Squeak.HEADER_TYPE_SIZE_AND_CLASS;
                    header= intFromInputSwapped(in);
                    i= i+12;
                    break;
                case Squeak.HEADER_TYPE_CLASS:
                    classInt= header - Squeak.HEADER_TYPE_CLASS;
                    header= intFromInputSwapped(in);
                    i= i+8;
                    nWords= (header>>2) & 63;
                    break;
                case Squeak.HEADER_TYPE_FREE_BLOCK:
                    throw new IOException("Unexpected free block");
                case Squeak.HEADER_TYPE_SHORT:
                    i= i+4;
                    classInt= (header>>12) & 31; //compact class index
                    //Note classInt<32 implies compact class index
                    nWords= (header>>2) & 63;
                    break;
            }
            int baseAddr = i - 4; //0-rel byte oop of this object (base header)
            nWords--;  //length includes base header which we have already read
            format= ((header>>8) & 15);
            hash= ((header>>17) & 4095);
            
            // Note classInt and data are just raw data; no base addr adjustment and no Int conversion
            data= new int[nWords];
            for (int j= 0; j<nWords; j++) {
            	data[j]= intFromInputSwapped(in);
            }
            String rawDataChunk = HexUtils.translateRawData(data);
            //monitor.logMessage(rawDataChunk);
            i= i+(nWords*4);
            
            SqueakObject squeakObject= new SqueakObject(new Integer (classInt),(short)format,(short)hash,data);
            imageInfo.getObjects().put(oldBaseAddr+baseAddr,squeakObject);
        }
		return imageInfo;
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
        
}
