/*
SqueakVM.java
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

package jsqueak.vm;

import static jsqueak.vm.SqueakMath.*;

import java.io.FileInputStream;
import java.util.Arrays;

import jsqueak.Squeak;
import jsqueak.display.ScreenFactory;
import jsqueak.image.SqueakImage;
import jsqueak.io.KeyboardFactory;
import jsqueak.monitor.Monitor;

/**
 * @author Daniel Ingalls
 *
 * The virtual machinery for executing Squeak bytecode.
 */
public class SqueakVM {
    private static final int MAX_STACK_DEPTH = 100;
	private static final int MAX_COUNTER = 100000;
	// static state:
    private SqueakImage image;
    SqueakPrimitiveHandler primHandler;
    
    public SqueakObject nilObj;
    private SqueakObject falseObj;
    private SqueakObject trueObj;
    Object[] specialObjects;
    Object[] specialSelectors;
    // dynamic state:
    Object receiver= nilObj;
    private SqueakObject activeContext= nilObj;
    SqueakObject homeContext= nilObj;
    private int sp;
    private SqueakObject method= nilObj;
    byte[] methodBytes;
    private int pc;
    private boolean success;
    private SqueakObject freeContexts;
    private SqueakObject freeLargeContexts;
    private int reclaimableContextCount; //Not #available, but how far down the current stack is recyclable
    private SqueakObject verifyAtSelector;
    private SqueakObject verifyAtClass;
    
    private boolean screenEvent = false;
    
    private int lowSpaceThreshold;
    private int interruptCheckCounter;
    private int interruptCheckCounterFeedBackReset;
    private int interruptChecksEveryNms;
    private int nextPollTick;
    private int nextWakeupTick;
    private int lastTick;
    private int interruptKeycode;
    private boolean interruptPending;
    private boolean semaphoresUseBufferA;
    private int semaphoresToSignalCountA;
    private int semaphoresToSignalCountB;
    private boolean deferDisplayUpdates;
    private int pendingFinalizationSignals;
    
	// 31-bit small Integers, range:
	public static final int MAX_SMALL_INT =  0x3FFFFFFF;
	public static final int MIN_SMALL_INT = -0x40000000;
	public static final int MILLISECOND_CLOCK_MASK = MAX_SMALL_INT>>1; //keeps ms logic in small int range
    
    int byteCount= 0;
    FileInputStream byteTracker;
    int nRecycledContexts= 0;
    int nAllocatedContexts= 0;
    Object[] stackedReceivers= new Object[MAX_STACK_DEPTH];
    Object[] stackedSelectors= new Object[MAX_STACK_DEPTH];
	private Monitor monitor;
	private MethodCache methodCache;
    
	public SqueakVM(SqueakImage anImage, Monitor monitor, 
			ScreenFactory screenFactory, KeyboardFactory keyboardFactory) {
		this.monitor = monitor; 
		monitor.logMessage("Creating VM");
		// canonical creation
		this.methodCache = new MethodCache();
		setImage(anImage);
		getImage().bindVM(this);
		primHandler = new SqueakPrimitiveHandler(this, screenFactory, keyboardFactory );
		loadImageState();
		initVMState();
		loadInitialContext();
	}

	public void clearCaches() {
		// Some time store null above SP in contexts
		primHandler.clearAtCache();
		methodCache.clearMethodCache();
		freeContexts = nilObj;
		freeLargeContexts = nilObj;
	}
    
	private void loadImageState() {
		SqueakObject specialObjectsArray = getImage().getSpecialObjectsArray();
		specialObjects = specialObjectsArray.pointers;
		nilObj = getSpecialObject(Squeak.splOb_NilObject);
		falseObj = getSpecialObject(Squeak.splOb_FalseObject);
		trueObj = getSpecialObject(Squeak.splOb_TrueObject);
		SqueakObject ssObj = getSpecialObject(Squeak.splOb_SpecialSelectors);
		specialSelectors = ssObj.pointers;
	}

	public SqueakObject getSpecialObject(int zeroBasedIndex) {
		return (SqueakObject) specialObjects[zeroBasedIndex];
	}
    
	public void setSpecialObject(int zeroBasedIndex, Object obj) {
		specialObjects[zeroBasedIndex] = obj;
	}
	
	public Object getSpecialSelector(int zeroBasedIndex) {
		return specialSelectors[zeroBasedIndex];
	}
	
	private void initVMState() {
		interruptCheckCounter = 0;
		interruptCheckCounterFeedBackReset = 1000;
		interruptChecksEveryNms = 3;
		nextPollTick = 0;
		setNextWakeupTick(0);
		lastTick = 0;
		interruptKeycode = 2094; // "cmd-."
		interruptPending = false;
		semaphoresUseBufferA = true;
		semaphoresToSignalCountA = 0;
		semaphoresToSignalCountB = 0;
		deferDisplayUpdates = false;
		pendingFinalizationSignals = 0;
		freeContexts = nilObj;
		freeLargeContexts = nilObj;
		setReclaimableContextCount(0);
		methodCache.initMethodCache();
	}

	private void loadInitialContext() {
		SqueakObject schedAssn = getSpecialObject(Squeak.splOb_SchedulerAssociation);
		SqueakObject sched = schedAssn.getPointerNI(Squeak.Assn_value);
		SqueakObject proc = sched.getPointerNI(Squeak.ProcSched_activeProcess);
		activeContext = proc.getPointerNI(Squeak.Proc_suspendedContext);
		fetchContextRegisters(getActiveContext());
		setReclaimableContextCount(0);
	}

	public void newActiveContext(SqueakObject newContext) {
		storeContextRegisters();
		activeContext = newContext; // We're off and running...
		fetchContextRegisters(newContext);
	}
        
	public void fetchContextRegisters(SqueakObject ctxt) {
		Object meth = ctxt.getPointer(Squeak.CONTEXT_METHOD);
		if (isSmallInt(meth)) {
			// if the Method field is an integer, activeCntx is a block context
			homeContext = (SqueakObject) ctxt
					.getPointer(Squeak.BLOCK_CONTEXT_HOME);
			meth = homeContext.getPointerNI(Squeak.CONTEXT_METHOD);
		} else {
			// otherwise home==ctxt
			// if (! primHandler.isA(meth,Squeak.splOb_ClassCompiledMethod))
			// meth= meth; // <-- break here
			homeContext = (SqueakObject) ctxt;
		}
		receiver = homeContext.getPointer(Squeak.CONTEXT_RECEIVER);
		method = (SqueakObject) meth;
		methodBytes = getMethod().getBitsAsMethodBytes();
		pc = decodeSqueakPC(
				ctxt.getPointerI(Squeak.CONTEXT_INSTRUCTION_POINTER), method);
		if (getPc() < -1)
			dumpStack();
		sp = decodeSqueakSP(ctxt.getPointerI(Squeak.CONTEXT_STACK_POINTER));
	}
    
	public void storeContextRegisters() {
		// Save pc, sp into activeContext object, prior to change of context
		// see fetchContextRegisters for symmetry
		// expects activeContext, pc, sp, and method state vars to still be
		// valid
		getActiveContext().setPointer(Squeak.CONTEXT_INSTRUCTION_POINTER,
				encodeSqueakPC(getPc(), getMethod()));
		getActiveContext().setPointer(Squeak.CONTEXT_STACK_POINTER,
				encodeSqueakSP(getSp()));
	}
    
	public Integer encodeSqueakPC(int intPC, SqueakObject aMethod) {
		// Squeak pc is offset by header and literals
		// and 1 for z-rel addressing, and 1 for pre-increment of fetch
		return smallFromInt(intPC
				+ (((aMethod.methodNumLits() + 1) * 4) + 1 + 1));
	}
        
	public int decodeSqueakPC(Integer squeakPC, SqueakObject aMethod) {
		return intFromSmall(squeakPC)
				- (((aMethod.methodNumLits() + 1) * 4) + 1 + 1);
	}
        
    public Integer encodeSqueakSP(int intSP) {
		// sp is offset by tempFrameStart, -1 for z-rel addressing
		return smallFromInt(intSP - (Squeak.CONTEXT_TEMP_FRAME_START - 1));
	}
        
	public int decodeSqueakSP(Integer squeakPC) {
		return intFromSmall(squeakPC) + (Squeak.CONTEXT_TEMP_FRAME_START - 1);
	}
        
    //SmallIntegers are stored as Java (boxed)Integers
	public static boolean canBeSmallInt(int anInt) {
		return (anInt >= MIN_SMALL_INT) && (anInt <= MAX_SMALL_INT);
	}
    
	public static Integer smallFromInt(int raw) {
		if (canBeSmallInt(raw))
			return Integer.valueOf(raw);
		else
			return null;
	}
    
    public static boolean isSmallInt(Object obj) {
        return obj instanceof Integer; 
    }
    
    public static int intFromSmall(Integer smallInt) {
        return smallInt.intValue(); 
    }
    
    // MEMORY ACCESS:
    public SqueakObject getClass(Object obj) {
        if (isSmallInt(obj))
            return getSpecialObject(Squeak.splOb_ClassInteger);
        return ((SqueakObject)obj).getSqClass();
    }
    
    // STACKFRAME ACCESS:
    public boolean isContext(SqueakObject obj) {
        //either block or methodContext
        if (obj.sqClass == specialObjects[Squeak.splOb_ClassMethodContext]) 
            return true;
        if (obj.sqClass == specialObjects[Squeak.splOb_ClassBlockContext]) 
            return true;
        return false;
    }
    
    public boolean isMethodContext(SqueakObject obj) {
        if (obj.sqClass == specialObjects[Squeak.splOb_ClassMethodContext]) 
            return true;
        return false;
    }
    
    public Object pop() {
        //Note leaves garbage above SP.  Serious reclaim should store nils above SP
        return getActiveContext().pointers[sp--]; 
    }
    
    public void popN(int nToPop) {
    	sp-= nToPop; 
    }
    
    public void push(Object oop) {
        getActiveContext().pointers[++sp] = oop; 
    }
    
    public void popNandPush(int nToPop,Object oop) 
    {
        getActiveContext().pointers[sp-= nToPop-1] = oop; 
    }
    
    public Object top() {
        return getActiveContext().pointers[getSp()]; 
    }
    
    public Object stackValue(int depthIntoStack) {
        return getActiveContext().pointers[getSp()-depthIntoStack]; 
    }
    
    // INNER BYTECODE INTERPRETER:
    public int nextByte() {
        return methodBytes[++pc] & 0xff; 
    }
    
    public void run() throws java.io.IOException {
    	long masterCounter = 0;  
    	long counter = MAX_COUNTER;
    	monitor.logMessage("Entered the main RUN LOOP");
	    int b2;
        while(true) {
            //...Here's the basic evaluator loop...'
            //        printContext();
            //        byteCount++;
            //        int b= nextByte(); 
        	/*counter--;
        	if (counter < 0) {
        		counter = MAX_COUNTER;
        		masterCounter++;
        		monitor.setStatus( Long.toString(masterCounter) );
        	}*/
        	int byteCode= methodBytes[++pc] & 0xff;
        	//monitor.logMessage("Processing instruction: "+byteCode);
            switch (byteCode) { /* The Main Bytecode Dispatch Loop */ 
              // load receiver variable
              case 0: case 1: case 2: case 3: case 4: case 5: case 6: case 7: 
              case 8: case 9: case 10: case 11: case 12: case 13: case 14: case 15: 
                  push(((SqueakObject)receiver).getPointer(byteCode&0xF)); break;
      
              // load temporary variable
              case 16: case 17: case 18: case 19: case 20: case 21: case 22: case 23: 
              case 24: case 25: case 26: case 27: case 28: case 29: case 30: case 31: 
                  push(homeContext.getPointer(Squeak.CONTEXT_TEMP_FRAME_START+(byteCode&0xF))); break;
      
              // loadLiteral
              case 32: case 33: case 34: case 35: case 36: case 37: case 38: case 39: 
              case 40: case 41: case 42: case 43: case 44: case 45: case 46: case 47: 
              case 48: case 49: case 50: case 51: case 52: case 53: case 54: case 55: 
              case 56: case 57: case 58: case 59: case 60: case 61: case 62: case 63: 
                  push(getMethod().methodGetLiteral(byteCode&0x1F)); break;
      
              // loadLiteralIndirect
              case 64: case 65: case 66: case 67: case 68: case 69: case 70: case 71: 
              case 72: case 73: case 74: case 75: case 76: case 77: case 78: case 79: 
              case 80: case 81: case 82: case 83: case 84: case 85: case 86: case 87: 
              case 88: case 89: case 90: case 91: case 92: case 93: case 94: case 95: 
                  push(((SqueakObject)getMethod().methodGetLiteral(byteCode&0x1F)).getPointer(Squeak.Assn_value)); break;
      
              // storeAndPop rcvr, temp
              case 96: case 97: case 98: case 99: case 100: case 101: case 102: case 103: 
                  ((SqueakObject)receiver).setPointer(byteCode&7,pop()); break;
              case 104: case 105: case 106: case 107: case 108: case 109: case 110: case 111: 
                  homeContext.setPointer(Squeak.CONTEXT_TEMP_FRAME_START+(byteCode&7),pop()); break;
      
              // Quick push constant
              case 112: push(receiver); break;
              case 113: push(getTrueObj()); break;
              case 114: push(getFalseObj()); break;
              case 115: push(nilObj); break;
              case 116: push(smallFromInt(-1)); break;
              case 117: push(smallFromInt(0)); break;
              case 118: push(smallFromInt(1)); break;
              case 119: push(smallFromInt(2)); break;
      
              // Quick return
              case 120: doReturn(receiver,homeContext.getPointerNI(Squeak.CONTEXT_SENDER)); break;
              case 121: doReturn(getTrueObj(),homeContext.getPointerNI(Squeak.CONTEXT_SENDER)); break;
              case 122: doReturn(getFalseObj(),homeContext.getPointerNI(Squeak.CONTEXT_SENDER)); break;
              case 123: doReturn(nilObj,homeContext.getPointerNI(Squeak.CONTEXT_SENDER)); break;
              case 124: doReturn(pop(),homeContext.getPointerNI(Squeak.CONTEXT_SENDER)); break;
              case 125: doReturn(pop(),getActiveContext().getPointerNI(Squeak.BLOCK_CONTEXT_CALLER)); break;
              case 126: nono(); break;
              case 127: nono(); break;
  
              // Sundry
              case 128: extendedPush(nextByte()); break;
              case 129: extendedStore(nextByte()); break;
              case 130: extendedStorePop(nextByte()); break;
              // singleExtendedSend
              case 131: b2= nextByte(); send(getMethod().methodGetSelector(b2&31),b2>>5,false); break;
              case 132: doubleExtendedDoAnything(nextByte()); break;
              // singleExtendedSendToSuper
              case 133: b2= nextByte(); send(getMethod().methodGetSelector(b2&31),b2>>5,true); break;
              // secondExtendedSend
              case 134: b2= nextByte(); send(getMethod().methodGetSelector(b2&63),b2>>6,false); break;
              case 135: pop(); break; // pop
              case 136: push(top()); break;   // dup
              // push thisContext
              case 137: push(getActiveContext()); setReclaimableContextCount(0); break;
  
              //Unused...
              case 138: case 139: case 140: case 141: case 142: case 143: 
                  nono(); break;
  
              // Short jmp
              case 144: case 145: case 146: case 147: case 148: case 149: case 150: case 151: 
            	    pc+= (byteCode&7)+1; break;
              // Short bfp
              case 152: case 153: case 154: case 155: case 156: case 157: case 158: case 159: 
                  jumpif (false,(byteCode&7)+1); break;
              // Long jump, forward and back
              case 160: case 161: case 162: case 163: case 164: case 165: case 166: case 167: 
                  b2=nextByte();
                  pc+= (((byteCode&7)-4)*256 + b2);
                  if ((byteCode&7)<4) checkForInterrupts();  //check on backward jumps (loops)
                  break;
              // Long btp
              case 168: case 169: case 170: case 171:
                  jumpif (true,(byteCode&3)*256 + nextByte()); break;
              // Long bfp
              case 172: case 173: case 174: case 175: 
                  jumpif (false,(byteCode&3)*256 + nextByte()); break;
  
              // Arithmetic Ops... + - < > <= >= = ~=    * / \ @ lshift: lxor: land: lor:
              case 176: setSuccess(true);
                  if (!pop2AndPushIntResult(stackInteger(1)+stackInteger(0))) sendSpecial(byteCode&0xF); break;   // PLUS +
              case 177: setSuccess(true);
                  if (!pop2AndPushIntResult(stackInteger(1)-stackInteger(0))) sendSpecial(byteCode&0xF); break;   // PLUS +
              case 178: setSuccess(true);
                  if (!pushBoolAndPeek(stackInteger(1) < stackInteger(0))) sendSpecial(byteCode&0xF); break;  // LESS <
              case 179: setSuccess(true);
                  if (!pushBoolAndPeek(stackInteger(1) > stackInteger(0))) sendSpecial(byteCode&0xF); break;  // GRTR >
              case 180: setSuccess(true);
                  if (!pushBoolAndPeek(stackInteger(1) <= stackInteger(0)))  sendSpecial(byteCode&0xF); break;  // LEQ <=
              case 181: setSuccess(true);
                  if (!pushBoolAndPeek(stackInteger(1) >= stackInteger(0)))  sendSpecial(byteCode&0xF); break;  // GEQ >=
              case 182: setSuccess(true);
                  if (!pushBoolAndPeek(stackInteger(1) == stackInteger(0)))  sendSpecial(byteCode&0xF); break;  // EQU =
              case 183: setSuccess(true);
                  if (!pushBoolAndPeek(stackInteger(1) != stackInteger(0)))  sendSpecial(byteCode&0xF); break;  // NEQ ~=
              case 184: setSuccess(true);
                  if (!pop2AndPushIntResult(safeMultiply(stackInteger(1), stackInteger(0)))) sendSpecial(byteCode&0xF); break;  // TIMES *
              case 185: setSuccess(true);
                  if (!pop2AndPushIntResult(quickDivide(stackInteger(1), stackInteger(0)))) sendSpecial(byteCode&0xF); break;  // Divide /
              case 186: setSuccess(true);
                  if (!pop2AndPushIntResult(mod(stackInteger(1), stackInteger(0)))) sendSpecial(byteCode&0xF); break;  // MOD \\
              case 187: setSuccess(true);
                  if (!primHandler.primitiveMakePoint()) sendSpecial(byteCode&0xF); break;  // MakePt int@int
              case 188: setSuccess(true); // Something is wrong with this one...
                  /*if (!pop2AndPushIntResult(safeShift(stackInteger(1),stackInteger(0))))*/ sendSpecial(byteCode&0xF); break; // bitShift:
              case 189: setSuccess(true);
                  if (!pop2AndPushIntResult(div(stackInteger(1), stackInteger(0)))) sendSpecial(byteCode&0xF); break;  // Divide //
              case 190: setSuccess(true);
                  if (!pop2AndPushIntResult(stackInteger(1) & stackInteger(0))) sendSpecial(byteCode&0xF); break; // bitAnd:
              case 191: setSuccess(true);
                  if (!pop2AndPushIntResult(stackInteger(1) | stackInteger(0))) sendSpecial(byteCode&0xF); break; // bitOr:
  
              // at:, at:put:, size, next, nextPut:, ...
              case 192: case 193: case 194: case 195: case 196: case 197: case 198: case 199: 
              case 200: case 201: case 202: case 203: case 204: case 205: case 206: case 207: 
                  if (!primHandler.quickSendOther(receiver,byteCode&0xF))
                      sendSpecial((byteCode&0xF)+16); break;
  
              // Send Literal Selector with 0, 1, and 2 args
              case 208: case 209: case 210: case 211: case 212: case 213: case 214: case 215: 
              case 216: case 217: case 218: case 219: case 220: case 221: case 222: case 223: 
                  send(getMethod().methodGetSelector(byteCode&0xF),0,false); break;
              case 224: case 225: case 226: case 227: case 228: case 229: case 230: case 231: 
              case 232: case 233: case 234: case 235: case 236: case 237: case 238: case 239: 
                  send(getMethod().methodGetSelector(byteCode&0xF),1,false); break;
              case 240: case 241: case 242: case 243: case 244: case 245: case 246: case 247: 
              case 248: case 249: case 250: case 251: case 252: case 253: case 254: case 255:
                  send(getMethod().methodGetSelector(byteCode&0xF),2,false); break; 
            }
        }
    }

    public void checkForInterrupts()  {
        //Check for interrupts at sends and backward jumps
        SqueakObject sema;
        int now;
        if (interruptCheckCounter-- > 0) 
            return; //only really check every 100 times or so
        //Mask so same wrap as primitiveMillisecondClock
        now= (int) (System.currentTimeMillis() & (long)MILLISECOND_CLOCK_MASK);
        if (now < lastTick) 
        {
            //millisecond clock wrapped"
            nextPollTick= now + (nextPollTick - lastTick);
            if (getNextWakeupTick() != 0)
                setNextWakeupTick(now + (getNextWakeupTick() - lastTick)); 
        }
        //Feedback logic attempts to keep interrupt response around 3ms...
        if ((now - lastTick) < interruptChecksEveryNms)  //wrapping is not a concern
        {
            interruptCheckCounterFeedBackReset += 10;
        }
        else
        {
            if (interruptCheckCounterFeedBackReset <= 1000)
                interruptCheckCounterFeedBackReset= 1000;
            else
                interruptCheckCounterFeedBackReset -= 12; 
        }
        interruptCheckCounter= interruptCheckCounterFeedBackReset; //reset the interrupt check counter"
        lastTick= now; //used to detect wraparound of millisecond clock
        //  if (signalLowSpace) {
        //            signalLowSpace= false; //reset flag
        //            sema= getSpecialObject(Squeak.splOb_TheLowSpaceSemaphore);
        //            if (sema != nilObj) synchronousSignal(sema); }
        //  if (now >= nextPollTick) {
        //            ioProcessEvents(); //sets interruptPending if interrupt key pressed
        //            nextPollTick= now + 500; } //msecs to wait before next call to ioProcessEvents"
        if (interruptPending) 
        {
            interruptPending= false; //reset interrupt flag
            sema= getSpecialObject(Squeak.splOb_TheInterruptSemaphore);
            if (sema != nilObj)
                primHandler.synchronousSignal(sema); 
        }
        if ((getNextWakeupTick() != 0) && (now >= getNextWakeupTick())) 
        {
            setNextWakeupTick(0); //reset timer interrupt
            sema= getSpecialObject(Squeak.splOb_TheTimerSemaphore);
            if (sema != nilObj) 
                primHandler.synchronousSignal(sema); 
        }
        //  if (pendingFinalizationSignals > 0) { //signal any pending finalizations
        //            sema= getSpecialObject(Squeak.splOb_ThefinalizationSemaphore);
        //            pendingFinalizationSignals= 0;
        //            if (sema != nilObj) primHandler.synchronousSignal(sema); }
        //  if ((semaphoresToSignalCountA > 0) || (semaphoresToSignalCountB > 0)) {
        //            signalExternalSemaphores(); }  //signal all semaphores in semaphoresToSignal
    }

    private void jumpif (boolean condition, int delta) {
        Object top= pop();
        if (top == (condition? trueObj : falseObj))
        {
        	pc+= delta; 
            return; 
        }
        if (top == (condition? falseObj : trueObj))
            return;
        push(top); //Uh-oh it's not even a boolean (that we know of ;-).  Restore stack...
        send((SqueakObject) specialObjects[Squeak.splOb_SelectorMustBeBoolean],1,false); 
    }
    
    public void sendSpecial(int lobits) {
        send((SqueakObject) specialSelectors[lobits*2],
             ((Integer) specialSelectors[(lobits*2)+1]).intValue(),
             false);   //specialSelectors is  {...sel,nArgs,sel,nArgs,...)
    } 
    
    public void extendedPush(int nextByte) {
        int lobits= nextByte&63;
        switch (nextByte>>6) {
            case 0: push(((SqueakObject)receiver).getPointer(lobits));break;
            case 1: push(homeContext.getPointer(Squeak.CONTEXT_TEMP_FRAME_START+lobits)); break;
            case 2: push(getMethod().methodGetLiteral(lobits)); break;
            case 3: push(((SqueakObject)getMethod().methodGetLiteral(lobits)).getPointer(Squeak.Assn_value)); break;
        }
    }

    public void extendedStore(int nextByte) {
        int lobits= nextByte&63;
        switch (nextByte>>6) {
            case 0: ((SqueakObject)receiver).setPointer(lobits,top()); break;
            case 1: homeContext.setPointer(Squeak.CONTEXT_TEMP_FRAME_START+lobits,top()); break;
            case 2: nono(); break;
            case 3: ((SqueakObject)getMethod().methodGetLiteral(lobits)).setPointer(Squeak.Assn_value,top()); break;
        }
    }

    public void extendedStorePop(int nextByte) {
        int lobits= nextByte&63;
        switch (nextByte>>6) {
            case 0: ((SqueakObject)receiver).setPointer(lobits,pop()); break;
            case 1: homeContext.setPointer(Squeak.CONTEXT_TEMP_FRAME_START+lobits,pop()); break;
            case 2: nono(); break;
            case 3: ((SqueakObject)getMethod().methodGetLiteral(lobits)).setPointer(Squeak.Assn_value,pop()); break;
        }
    }

    public void doubleExtendedDoAnything(int nextByte) {
        int byte3= nextByte();
        switch (nextByte>>5) {
            case 0: send(getMethod().methodGetSelector(byte3),nextByte&31,false); break;
            case 1: send(getMethod().methodGetSelector(byte3),nextByte&31,true); break;
            case 2: push(((SqueakObject)receiver).getPointer(byte3)); break;
            case 3: push(getMethod().methodGetLiteral(byte3)); break;
            case 4: push(((SqueakObject)getMethod().methodGetLiteral(byte3)).getPointer(Squeak.Assn_key)); break;
            case 5: ((SqueakObject)receiver).setPointer(byte3,top()); break;
            case 6: ((SqueakObject)receiver).setPointer(byte3,pop()); break;
            case 7: ((SqueakObject)getMethod().methodGetLiteral(byte3)).setPointer(Squeak.Assn_key,top()); break;
        }
    }

	public void doReturn(Object returnValue, SqueakObject targetContext) {
		if (targetContext == nilObj)
			cannotReturn();
		if (targetContext.getPointer(Squeak.CONTEXT_INSTRUCTION_POINTER) == nilObj)
			cannotReturn();
		SqueakObject thisContext = getActiveContext();
		while (thisContext != targetContext) {
			if (thisContext == nilObj)
				cannotReturn();
			if (isUnwindMarked(thisContext))
				aboutToReturn(returnValue, thisContext);
			thisContext = thisContext.getPointerNI(Squeak.CONTEXT_SENDER);
		}
		// No unwind to worry about, just peel back the stack (usually just to
		// sender)
		SqueakObject nextContext;
		thisContext = getActiveContext();
		while (thisContext != targetContext) {
			nextContext = thisContext.getPointerNI(Squeak.CONTEXT_SENDER);
			thisContext.setPointer(Squeak.CONTEXT_SENDER, nilObj);
			thisContext.setPointer(Squeak.CONTEXT_INSTRUCTION_POINTER, nilObj);
			if (getReclaimableContextCount() > 0) {
				setReclaimableContextCount(getReclaimableContextCount() - 1);
				recycleIfPossible(thisContext);
			}
			thisContext = nextContext;
		}
		activeContext = thisContext;
		fetchContextRegisters(getActiveContext());
		push(returnValue);
		// System.err.println("***returning " + printString(returnValue));
	}
    
    public void cannotReturn() {}
    
	public boolean isUnwindMarked(SqueakObject ctxt) {
		return false;
	}
    
    public void aboutToReturn(Object obj, SqueakObject ctxt) {}
    
	public void nono() {
		throw new RuntimeException("bad code");
	}
    
	int stackInteger(int nDeep) {
		return checkSmallInt(stackValue(nDeep));
	}
    
	int checkSmallInt(Object maybeSmall) {
		// returns an int and sets success
		if (isSmallInt(maybeSmall))
			return intFromSmall(((Integer) maybeSmall));
		setSuccess(false);
		return 1;
	}
    
	public boolean pop2AndPushIntResult(int intResult) {
		// Note returns sucess boolean
		if (!isSuccess())
			return false;
		Object smallInt = smallFromInt(intResult);
		if (smallInt != null) {
			popNandPush(2, smallInt);
			return true;
		}
		return false;
	}
    
	public boolean pushBoolAndPeek(boolean boolResult) {
		// Peek ahead to see if next bytecode is a conditional jump
		if (!isSuccess())
			return false;
		int originalPC = getPc();
		int nextByte = nextByte();
		if (nextByte >= 152 && nextByte < 160) {
			// It's a BFP
			popN(2);
			if (boolResult)
				return true;
			else
				pc += (nextByte - 152 + 1);
			return true;
		}
		if (nextByte == 172) {
			// It's a long BFP
			// Could check for all long cond jumps later
			popN(2);
			nextByte = nextByte();
			if (boolResult)
				return true;
			else
				pc += nextByte;
			return true;
		}
		popNandPush(2, boolResult ? getTrueObj() : getFalseObj());
		pc = originalPC;
		return true;
	}

	public void send(SqueakObject selector, int argCount, boolean doSuper) {
		SqueakObject newMethod;
		int primIndex;
		Object newRcvr = stackValue(argCount);
		// if (printString(selector).equals("error:"))
		// dumpStack();// <---break here
		// int stackDepth=stackDepth();
		// stackedReceivers[stackDepth]=newRcvr;
		// stackedSelectors[stackDepth]=selector;
		SqueakObject lookupClass = getClass(newRcvr);
		if (doSuper) {
			lookupClass = getMethod().methodClassForSuper();
			lookupClass = lookupClass.getPointerNI(Squeak.CLASS_SUPERCLASS);
		}
		int priorSP = getSp(); // to check if DNU changes argCount
		MethodCache.MethodCacheEntry entry = findSelectorInClass(selector, argCount,
				lookupClass);
		newMethod = entry.method;
		primIndex = entry.primIndex;
		if (primIndex > 0) {
			// note details for verification of at/atput primitives
			setVerifyAtSelector(selector);
			setVerifyAtClass(lookupClass);
		}
		executeNewMethod(newRcvr, newMethod, argCount + (getSp() - priorSP),
				primIndex);
	} // DNU may affect argCount

	public MethodCache.MethodCacheEntry findSelectorInClass(SqueakObject selector,
			int argCount, SqueakObject startingClass) {
		MethodCache.MethodCacheEntry cacheEntry 
				= methodCache.findMethodCacheEntry(selector, startingClass);
		if (cacheEntry.method != null)
			return cacheEntry; // Found it in the method cache
		SqueakObject currentClass = startingClass;
		SqueakObject mDict;
		while (!(currentClass == nilObj)) {
			mDict = currentClass.getPointerNI(Squeak.CLASS_MDICT);
			if (mDict == nilObj) {
				// ["MethodDict pointer is nil (hopefully due a swapped out
				// stub)
				// -- raise exception #cannotInterpret:."
				// self createActualMessageTo: class.
				// messageSelector _ self splObj: SelectorCannotInterpret.
				// ^ self lookupMethodInClass: (self superclassOf:
				// currentClass)]
			}
			SqueakObject newMethod = lookupSelectorInDict(mDict, selector);
			if (!(newMethod == nilObj)) {
				// load cache entry here and return
				cacheEntry.method = newMethod;
				cacheEntry.primIndex = newMethod.methodPrimitiveIndex();
				return cacheEntry;
			}
			currentClass = currentClass.getPointerNI(Squeak.CLASS_SUPERCLASS);
		}

		// Could not find a normal message -- send #doesNotUnderstand:
		// if (printString(selector).equals("zork"))
		// System.err.println(printString(selector));
		SqueakObject dnuSel = getSpecialObject(Squeak.splOb_SelectorDoesNotUnderstand);
		if (selector == dnuSel) // Cannot find #doesNotUnderstand: --
								// unrecoverable error.
			throw new RuntimeException(
					"Recursive not understood error encountered");
		SqueakObject dnuMsg = createActualMessage(selector, argCount,
				startingClass); // The argument to doesNotUnderstand:
		popNandPush(argCount, dnuMsg);
		return findSelectorInClass(dnuSel, 1, startingClass);
	}

	public SqueakObject createActualMessage(SqueakObject selector,
			int argCount, SqueakObject cls) {
		// Bundle up receiver, args and selector as a messageObject
		SqueakObject argArray = instantiateClass(
				getSpecialObject(Squeak.splOb_ClassArray), argCount);
		System.arraycopy(getActiveContext().pointers, getSp() - argCount + 1,
				argArray.pointers, 0, argCount); // copy args from stack
		SqueakObject message = instantiateClass(
				getSpecialObject(Squeak.splOb_ClassMessage), 0);
		message.setPointer(Squeak.Message_selector, selector);
		message.setPointer(Squeak.Message_arguments, argArray);
		if (message.pointers.length < 3)
			return message; // Early versions don't have lookupClass
		message.setPointer(Squeak.Message_lookupClass, cls);
		return message;
	}

	public SqueakObject lookupSelectorInDict(SqueakObject mDict,
			SqueakObject messageSelector) {
		// Returns a method or nilObject
		int dictSize = mDict.pointersSize();
		int mask = (dictSize - Squeak.MethodDict_selectorStart) - 1;
		int index = (mask & messageSelector.getHash())
				+ Squeak.MethodDict_selectorStart;
		// If there are no nils(should always be), then stop looping on second
		// wrap.
		boolean hasWrapped = false;
		while (true) {
			SqueakObject nextSelector = mDict.getPointerNI(index);
			// System.err.println("index= "+index+" "+printString(nextSelector));
			if (nextSelector == messageSelector) {
				SqueakObject methArray = mDict
						.getPointerNI(Squeak.MethodDict_array);
				return methArray.getPointerNI(index
						- Squeak.MethodDict_selectorStart);
			}
			if (nextSelector == nilObj)
				return nilObj;
			if (++index == dictSize) {
				if (hasWrapped)
					return nilObj;
				index = Squeak.MethodDict_selectorStart;
				hasWrapped = true;
			}
		}
	}

	public void executeNewMethod(Object newRcvr, SqueakObject newMethod,
			int argumentCount, int primitiveIndex) {
		if (primitiveIndex > 0)
			if (tryPrimitive(primitiveIndex, argumentCount))
				return; // Primitive succeeded -- end of story
		SqueakObject newContext = allocateOrRecycleContext(newMethod
				.methodNeedsLargeFrame());
		int methodNumLits = getMethod().methodNumLits();
		// Our initial IP is -1, so first fetch gets bits[0]
		// The stored IP should be 1-based index of *next* instruction, offset
		// by hdr and lits
		int newPC = -1;
		int tempCount = newMethod.methodTempCount();
		int newSP = tempCount;
		newSP += Squeak.CONTEXT_TEMP_FRAME_START - 1; // -1 for z-rel addressing
		newContext.setPointer(Squeak.CONTEXT_METHOD, newMethod);
		// Following store is in case we alloc without init; all other fields
		// get stored
		newContext.setPointer(Squeak.BLOCK_CONTEXT_INITIAL_IP, nilObj);
		newContext.setPointer(Squeak.CONTEXT_SENDER, getActiveContext());
		// Copy receiver and args to new context
		// Note this statement relies on the receiver slot being contiguous with
		// args...
		System.arraycopy(getActiveContext().pointers, getSp() - argumentCount,
				newContext.pointers, Squeak.CONTEXT_TEMP_FRAME_START - 1,
				argumentCount + 1);
		// ...and fill the remaining temps with nil
		Arrays.fill(newContext.pointers, Squeak.CONTEXT_TEMP_FRAME_START
				+ argumentCount, Squeak.CONTEXT_TEMP_FRAME_START + tempCount,
				nilObj);
		popN(argumentCount + 1);
		setReclaimableContextCount(getReclaimableContextCount() + 1);
		storeContextRegisters();
		activeContext = newContext; // We're off and running...
		// Following are more efficient than fetchContextRegisters in
		// newActiveContext:
		homeContext = newContext;
		method = newMethod;
		methodBytes = (byte[]) getMethod().getBits();
		pc = newPC;
		sp = newSP;
		storeContextRegisters(); // not really necessary, I claim
		receiver = newContext.getPointer(Squeak.CONTEXT_RECEIVER);
		if (receiver != newRcvr)
			System.err.println("receiver doesnt match");
		checkForInterrupts();
	}
    
	public boolean tryPrimitive(int primIndex, int argCount) {
		if ((primIndex > 255) && (primIndex < 520)) {
			if (primIndex >= 264) {
				// return instvars
				popNandPush(1,
						((SqueakObject) top()).getPointer(primIndex - 264));
				return true;
			} else {
				if (primIndex == 256)
					return true; // return self
				if (primIndex == 257) {
					popNandPush(1, getTrueObj()); // return true
					return true;
				}
				if (primIndex == 258) {
					popNandPush(1, getFalseObj()); // return false
					return true;
				}
				if (primIndex == 259) {
					popNandPush(1, nilObj); // return nil
					return true;
				}
				popNandPush(1, smallFromInt(primIndex - 261)); // return -1...2
				return true;
			}
		} else {
			int spBefore = getSp();
			boolean success = primHandler.doPrimitive(primIndex, argCount);
			// if (success) {
			// if (primIndex>=81 && primIndex<=88) return success; // context
			// switches and perform
			// if (primIndex>=43 && primIndex<=48) return success; // boolean
			// peeks
			// if (sp != (spBefore-argCount))
			// System.err.println("***Stack imbalance on primitive #" +
			// primIndex); }
			// else{
			// if (sp != spBefore)
			// System.err.println("***Stack imbalance on primitive #" +
			// primIndex);
			// if (primIndex==103) return success; // scan chars
			// if (primIndex==230) return success; // yield
			// if (primIndex==19) return success; // fail
			// System.err.println("At bytecount " + byteCount +
			// " failed primitive #" + primIndex);
			// if (primIndex==80) {
			// dumpStack();
			// int a=primIndex; } // <-- break here
			// }
			return success;
		}
	}

	public boolean primitivePerform(int argCount) {
		SqueakObject selector = (SqueakObject) stackValue(argCount - 1);
		Object rcvr = stackValue(argCount);
		// NOTE: findNewMethodInClass may fail and be converted to
		// #doesNotUnderstand:,
		// (Whoah) so we must slide args down on the stack now, so that would
		// work
		int trueArgCount = argCount - 1;
		int selectorIndex = getSp() - trueArgCount;
		Object[] stack = getActiveContext().pointers; // slide everything down...
		System.arraycopy(stack, selectorIndex + 1, stack, selectorIndex,
				trueArgCount);
		sp--; // adjust sp accordingly
		MethodCache.MethodCacheEntry entry 
				= findSelectorInClass(selector, trueArgCount, getClass(rcvr));
		SqueakObject newMethod = entry.method;
		executeNewMethod(rcvr, newMethod, newMethod.methodNumArgs(),
				entry.primIndex);
		return true;
	}
                                        
	public boolean primitivePerformWithArgs(SqueakObject lookupClass) {
		Object rcvr = stackValue(2);
		SqueakObject selector = (SqueakObject) stackValue(1);
		if (isSmallInt(stackValue(0)))
			return false;
		SqueakObject args = (SqueakObject) stackValue(0);
		if (args.pointers == null)
			return false;
		int trueArgCount = args.pointers.length;
		System.arraycopy(args.pointers, 0, getActiveContext().pointers, getSp() - 1,
				trueArgCount);
		sp = sp - 2 + trueArgCount; // pop selector and array then push args
		MethodCache.MethodCacheEntry entry 
				= findSelectorInClass(selector, trueArgCount, lookupClass);
		SqueakObject newMethod = entry.method;
		if (newMethod.methodNumArgs() != trueArgCount)
			return false;
		executeNewMethod(rcvr, newMethod, newMethod.methodNumArgs(),
				entry.primIndex);
		return true;
	}
                                        
	public boolean primitivePerformInSuperclass(SqueakObject lookupClass) {
		// verify that lookupClass is actually in reciver's inheritance
		SqueakObject currentClass = getClass(stackValue(3));
		while (currentClass != lookupClass) {
			currentClass = (SqueakObject) currentClass.pointers[Squeak.CLASS_SUPERCLASS];
			if (currentClass == nilObj)
				return false;
		}
		pop(); // pop the lookupClass for now
		if (primitivePerformWithArgs(lookupClass))
			return true;
		push(lookupClass); // restore lookupClass if failed
		return false;
	}
                                        
	public void recycleIfPossible(SqueakObject ctxt) {
		if (!isMethodContext(ctxt))
			return;
		// if (isContext(ctxt)) return; //Defeats recycling of contexts
		if (ctxt.pointersSize() == (Squeak.CONTEXT_TEMP_FRAME_START + Squeak.CONTEXT_SMALL_FRAME_SIZE)) {
			// Recycle small contexts
			ctxt.setPointer(0, freeContexts);
			freeContexts = ctxt;
		} else {
			// Recycle large contexts
			if (ctxt.pointersSize() != (Squeak.CONTEXT_TEMP_FRAME_START + Squeak.CONTEXT_lARGE_FRAME_SIZE)) {
				// freeContexts=freeContexts;
				return; // <-- break here
			}
			ctxt.setPointer(0, freeLargeContexts);
			freeLargeContexts = ctxt;
		}
	}
    
	public SqueakObject allocateOrRecycleContext(boolean needsLarge) {
		// Return a recycled context or a newly allocated one if none is
		// available for recycling."
		SqueakObject freebie;
		if (needsLarge) {
			if (freeLargeContexts != nilObj) {
				freebie = freeLargeContexts;
				freeLargeContexts = freebie.getPointerNI(0);
				nRecycledContexts++;
				return freebie;
			}
			nAllocatedContexts++;
			return instantiateClass(
					(SqueakObject) specialObjects[Squeak.splOb_ClassMethodContext],
					Squeak.CONTEXT_lARGE_FRAME_SIZE);
		} else {
			if (freeContexts != nilObj) {
				freebie = freeContexts;
				freeContexts = freebie.getPointerNI(0);
				nRecycledContexts++;
				return freebie;
			}
			nAllocatedContexts++;
			return instantiateClass(
					(SqueakObject) specialObjects[Squeak.splOb_ClassMethodContext],
					Squeak.CONTEXT_SMALL_FRAME_SIZE);
		}
	}

	public SqueakObject instantiateClass(int specialObjectClassIndex,
			int indexableSize) {
		return instantiateClass(
				(SqueakObject) specialObjects[specialObjectClassIndex],
				indexableSize);
	}
    
    // FIXME: remove this method
	public SqueakObject instantiateClass(SqueakObject theClass,
			int indexableSize) {
		return new SqueakObject(getImage(), theClass, indexableSize, nilObj);
	}
    
	public void printContext() {
		if ((byteCount % MAX_STACK_DEPTH) == 0 && stackDepth() > MAX_STACK_DEPTH) {
			System.err.println("******Stack depth over " + MAX_STACK_DEPTH + "******");
			dumpStack();
			// byteCount= byteCount; // <-- break here
		}
		// if (mod(byteCount,1000) != 0) return;
		if (byteCount != -1)
			return;
		System.err.println();
		System.err.println(byteCount + " rcvr= " + printString(receiver));
		System.err.println("depth= " + stackDepth() + "; top= "
				+ printString(top()));
		System.err.println("pc= " + getPc() + "; sp= " + getSp() + "; nextByte= "
				+ (((byte[]) getMethod().getBits())[getPc() + 1] & 0xff));
		// if (byteCount==1764)
		// byteCount= byteCount; // <-- break here
	}

	int stackDepth() {
		SqueakObject ctxt = getActiveContext();
		int depth = 0;
		while ((ctxt = ctxt.getPointerNI(Squeak.CONTEXT_SENDER)) != nilObj)
			depth = depth + 1;
		return depth;
	}

	String printString(Object obj) {
		// Handles SqueakObjs and SmallInts as well
		if (obj == null)
			return "null";
		if (isSmallInt(obj))
			return "=" + ((Integer) obj).intValue();
		else
			return ((SqueakObject) obj).asString();
	}

	void dumpStack() {
		for (int i = 0; i < stackDepth(); i++)
			if (stackedSelectors != null)
				System.err.println(stackedReceivers[i] + " >> "
						+ stackedSelectors[i]);
	}

	public FormCache newFormCache(SqueakObject aForm) {
		return new FormCache(this,aForm);
	}

	public FormCache newFormCache() {
		return new FormCache(this);
	}

	public void wakeVM() {
		screenEvent = true;
		synchronized (this) {
			notify();
		}
		try {
			Thread.sleep(0, 200);
		} catch (InterruptedException e) {
		}

		screenEvent = false;
	}

	public SqueakImage getImage() {
		return image;
	}

	public void setImage(SqueakImage image) {
		this.image = image;
	}

	public SqueakObject getVerifyAtSelector() {
		return verifyAtSelector;
	}

	public void setVerifyAtSelector(SqueakObject verifyAtSelector) {
		this.verifyAtSelector = verifyAtSelector;
	}

	public SqueakObject getVerifyAtClass() {
		return verifyAtClass;
	}

	public void setVerifyAtClass(SqueakObject verifyAtClass) {
		this.verifyAtClass = verifyAtClass;
	}

	public SqueakObject getTrueObj() {
		return trueObj;
	}

	public SqueakObject getFalseObj() {
		return falseObj;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public int getPc() {
		return pc;
	}

	public SqueakObject getMethod() {
		return method;
	}

	public SqueakObject getActiveContext() {
		return activeContext;
	}

	public int getSp() {
		return sp;
	}

	public int getLowSpaceThreshold() {
		return lowSpaceThreshold;
	}

	public void setLowSpaceThreshold(int lowSpaceThreshold) {
		this.lowSpaceThreshold = lowSpaceThreshold;
	}

	public int getReclaimableContextCount() {
		return reclaimableContextCount;
	}

	public void setReclaimableContextCount(int reclaimableContextCount) {
		this.reclaimableContextCount = reclaimableContextCount;
	}

	public int getNextWakeupTick() {
		return nextWakeupTick;
	}

	public void setNextWakeupTick(int nextWakeupTick) {
		this.nextWakeupTick = nextWakeupTick;
	}

	public boolean isScreenEvent() {
		return screenEvent;
	}

	public boolean clearMethodCache() {
		return methodCache.clearMethodCache();
	}

	public boolean flushMethodCacheForMethod(SqueakObject method) {
		return methodCache.flushMethodCacheForMethod(method);
	}

	public boolean flushMethodCacheForSelector(SqueakObject selector) {
		return methodCache.flushMethodCacheForSelector(selector);
	}
}
