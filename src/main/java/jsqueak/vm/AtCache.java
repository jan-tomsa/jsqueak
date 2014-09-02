package jsqueak.vm;

public class AtCache {

	static class AtCacheInfo {
	    SqueakObject array;
	    int size;
	    int ivarOffset;
	    boolean convertChars; 
	}

	private AtCacheInfo[] atCache;
	public static final int atCacheSize= 32; // must be power of 2
	public static final int atCacheMask= atCacheSize-1; //...so this is a mask
	// Its purpose of the at-cache is to allow fast (bytecode) access to at/atput code
	// without having to check whether this object has overridden at, etc.
	private AtCacheInfo[] atPutCache;
	private AtCacheInfo nonCachedInfo;
	private SqueakVM vm;
	private SqueakPrimitiveHandler squeakPrimitiveHandler;
	
	public AtCache(SqueakVM squeakVM, SqueakPrimitiveHandler squeakPrimitiveHandler) {
		this.vm = squeakVM;
		this.squeakPrimitiveHandler = squeakPrimitiveHandler;
	    atCache= new AtCacheInfo[atCacheSize];
	    atPutCache= new AtCacheInfo[atCacheSize];
	    nonCachedInfo= new AtCacheInfo();
	    for(int i= 0; i<atCacheSize; i++) {
	        atCache[i]= new AtCacheInfo();
	        atPutCache[i]= new AtCacheInfo();
	    } 
	}
	
	public AtCacheInfo[] cache() {
		return atCache;
	}
	
	public AtCacheInfo[] atPutCache() {
		return atCache;
	}
	
	/**
	 * Clear at-cache pointers (prior to GC). 
	 */
	void clearAtCache() {
	    for(int i= 0; i<atCacheSize; i++) {
	        atCache[i].array= null;
	        atPutCache[i].array= null;
	    }
	}
	
	AtCacheInfo makeCacheInfo(AtCacheInfo[] atOrPutCache, Object atOrPutSelector, SqueakObject array, boolean convertChars, boolean includeInstVars) {
	    // Make up an info object and store it in the atCache or the atPutCache.
	    // If it's not cacheable (not a non-super send of at: or at:put:)
	    // then return the info in nonCachedInfo.
	    // Note that info for objectAt (includeInstVars) will have
	    // a zero ivarOffset, and a size that includes the extra instVars
	    AtCacheInfo info;
	    boolean cacheable= (vm.getVerifyAtSelector() == atOrPutSelector) //is at or atPut
	        && (vm.getVerifyAtClass() == array.getSqClass())         //not a super send
	        && (array.getFormat()==3 && vm.isContext(array));        //not a context (size can change)
	    if (cacheable) 
	        info= atOrPutCache[array.hashCode() & atCacheMask];
	    else
	        info= nonCachedInfo;
	    info.array= array;
	    info.convertChars= convertChars; 
	    if (includeInstVars) {
	        info.size= Math.max(0,squeakPrimitiveHandler.indexableSize(array)) + array.instSize();
	        info.ivarOffset= 0; 
	    } else {
	        info.size= squeakPrimitiveHandler.indexableSize(array);
	        info.ivarOffset= (array.getFormat()<6) ? array.instSize() : 0; 
	    }
	    return info; 
	}

}
