package JSqueak.image;

import java.lang.ref.WeakReference;
import java.util.Hashtable;

import JSqueak.vm.SqueakObject;

class ObjectMutator {
	
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
	
	public static void verifySameLengths(Object[] sourceObjects, Object[] targetClasses) {
        if (sourceObjects.length != targetClasses.length) 
            throw new SourceAndTargedHaveDifferentLengths();
	}

	static void mutateClasses(Hashtable mutations, WeakReference[] stObjectTable, int objectTableLength) {
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

	static void setupMutationsTargetToSource(Object[] sourceObjects,
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

	static void setupMutationsSourceToTarget(Object[] sourceObjects,
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
}
