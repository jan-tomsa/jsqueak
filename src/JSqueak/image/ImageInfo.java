package JSqueak.image;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import JSqueak.vm.SqueakObject;

public class ImageInfo {
	private int baseAddr;
	private int oldBaseAddr;
	private int lastHash;
	private int specialObjectsOopInt;
	private Map<Integer,SqueakObject> objects;

	public ImageInfo() {
		objects = new HashMap<Integer,SqueakObject>();
	}


	public int getBaseAddr() {
		return baseAddr;
	}

	public void setBaseAddr(int baseAddr) {
		this.baseAddr = baseAddr;
	}

	public int getOldBaseAddr() {
		return oldBaseAddr;
	}

	public void setOldBaseAddr(int oldBaseAddr) {
		this.oldBaseAddr = oldBaseAddr;
	}

	public Map<Integer, SqueakObject> getObjects() {
		return objects;
	}

	public void setObjects(Map<Integer, SqueakObject> objects) {
		this.objects = objects;
	}

	public int getLastHash() {
		return lastHash;
	}

	public void setLastHash(int lastHash) {
		this.lastHash = lastHash;
	}


	public int getSpecialObjectsOopInt() {
		return specialObjectsOopInt;
	}


	public void setSpecialObjectsOopInt(int specialObjectsOopInt) {
		this.specialObjectsOopInt = specialObjectsOopInt;
	}
	
}
