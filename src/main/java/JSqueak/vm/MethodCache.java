package JSqueak.vm;

public class MethodCache {

	class MethodCacheEntry {
		SqueakObject lkupClass;
		SqueakObject selector;
		SqueakObject method;
		int primIndex;
		int tempCount;
	}
	static int methodCacheSize= 1024; // must be power of two
	static int methodCacheMask= methodCacheSize-1; // so this is a mask
	MethodCacheEntry[] methodCache= new MethodCacheEntry[methodCacheSize];
	static int randomish= 0;
	public boolean clearMethodCache() {
		// clear method cache entirely (prim 89)
		for (int i = 0; i < methodCacheSize; i++) {
			methodCache[i].selector = null; // mark it free
			methodCache[i].method = null; // release the method
		}
		return true;
	}
	public boolean flushMethodCacheForMethod(SqueakObject method) {
		// clear cache entries for selector (prim 116)
		for (int i = 0; i < methodCacheSize; i++) {
			if (methodCache[i].method == method) {
				methodCache[i].selector = null; // mark it free
				methodCache[i].method = null; // release the method
			}
		}
		return true;
	}
	public boolean flushMethodCacheForSelector(SqueakObject selector) {
		// clear cache entries for selector (prim 119)
		for (int i = 0; i < methodCacheSize; i++) {
			if (methodCache[i].selector == selector) {
				methodCache[i].selector = null; // mark it free
				methodCache[i].method = null; // release the method
			}
		}
		return true;
	}
	public MethodCacheEntry findMethodCacheEntry(SqueakObject selector,
			SqueakObject lkupClass) {
		// Probe the cache, and return the matching entry if found
		// Otherwise return one that can be used (selector and class set) with
		// method= null.
		// Initial probe is class xor selector, reprobe delta is selector
		// We don not try to optimize probe time -- all are equally 'fast'
		// compared to lookup
		// Instead we randomize the reprobe so two or three very active
		// conflicting entries
		// will not keep dislodging each other
		MethodCacheEntry entry;
		int nProbes = 4;
		randomish = (randomish + 1) % nProbes;
		int firstProbe = (selector.getHash() ^ lkupClass.getHash()) & methodCacheMask;
		int probe = firstProbe;
		for (int i = 0; i < 4; i++) {
			// 4 reprobes for now
			entry = methodCache[probe];
			if (entry.selector == selector && entry.lkupClass == lkupClass)
				return entry;
			if (i == randomish)
				firstProbe = probe;
			probe = (probe + selector.getHash()) & methodCacheMask;
		}
		entry = methodCache[firstProbe];
		entry.lkupClass = lkupClass;
		entry.selector = selector;
		entry.method = null;
		return entry;
	}
	void initMethodCache() {
		methodCache = new MethodCacheEntry[methodCacheSize];
		for (int i = 0; i < methodCacheSize; i++) {
			methodCache[i] = new MethodCacheEntry();
		}
	}

}
