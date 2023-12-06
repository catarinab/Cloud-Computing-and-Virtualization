package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.Map;
import java.util.HashMap;

import java.io.IOException;

import java.lang.InterruptedException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import javassist.CannotCompileException;
import javassist.CtBehavior;
import javassist.CtConstructor;
import javassist.bytecode.BadBytecode;
import javassist.NotFoundException;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.Opcode;
import javassist.ClassPool;
import javassist.bytecode.ClassFile;


public class ICount extends AbstractJavassistTool {

    private static class Counters {
        public String requestType = "";
        
        /**
         * Number of executed basic blocks.
         */
        public long nblocks = 0;

        /**
         * Number of executed methods.
         */
        public long nmethods = 0;

        /**
         * Number of executed instructions.
         */
        public long ninsts = 0;

        /*
         * Number of load/store instructions.
         */
        public long nloadstore = 0;

        /*
         * CPU Time (in nanoseconds).
         */
        public ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        public long cpuTime = -threadMXBean.getCurrentThreadCpuTime();
    }

    public static class DynamoDBInfo {
        public String requestID;
        public String type;
        public int att1;
        public int att2;
        public float att3;
        public long insts = 0;
        public long nloadstore = 0;
        public long cpuTime = 0;
    }

    private static ConcurrentHashMap<Long, Counters> threadCounters;

    private static BlockingQueue<String> requestTypes;
    private static BlockingQueue<DynamoDBInfo> dynamoDBQueue;

    private static Map<String, Integer> compressiontypes = new HashMap<String, Integer>();

    public ICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
        compressiontypes.put("png", 0);
        compressiontypes.put("jpeg", 1);
        compressiontypes.put("bmp", 2);
        threadCounters = new ConcurrentHashMap<Long, Counters>();
        requestTypes = new LinkedBlockingQueue<>();
        dynamoDBQueue = new LinkedBlockingQueue<>();
    }


    public static void newThread() {
        long threadID = Thread.currentThread().getId();
        threadCounters.put(threadID, new Counters());
    }

    public static void saveFields(String requestType) {
        requestTypes.add(requestType);
    }
    
    public static DynamoDBInfo getDynamoDBInfo() throws InterruptedException {
        return dynamoDBQueue.take();
    }

    public static void getFields() throws InterruptedException {
        long threadID = Thread.currentThread().getId();
        Counters threadCounter = threadCounters.get(threadID);
        threadCounter.requestType = requestTypes.take();
    }

    public static void incBasicBlock(int position, int length) {
        long threadID = Thread.currentThread().getId();
        if (!threadCounters.containsKey(threadID)) return;
        Counters counters = threadCounters.get(threadID);
        counters.nblocks++;
        counters.ninsts += length;
    }

    public static void incBehavior(String name) {
        long threadID = Thread.currentThread().getId();
        if (!threadCounters.containsKey(threadID)) return;
        Counters counters = threadCounters.get(threadID);
        counters.nmethods++;
    }

    public static void incInstructions(String classname, String methodname) throws BadBytecode, NotFoundException {
        long threadID = Thread.currentThread().getId();
        if (!threadCounters.containsKey(threadID)){
            return;
        }
        Counters counters = threadCounters.get(threadID);
        ClassPool classPool = ClassPool.getDefault();
        ClassFile classFile = classPool.get(classname).getClassFile();
        MethodInfo methodInfo = classFile.getMethod(methodname);
        CodeAttribute codeAttribute =  methodInfo.getCodeAttribute();
        if(codeAttribute == null) return;
        CodeIterator iterator = codeAttribute.iterator();
        iterator.begin();
        while (iterator.hasNext()) {
            int index = iterator.next();
            int opcode = iterator.byteAt(index);
            if (opcode == Opcode.LLOAD || opcode == Opcode.DLOAD || opcode == Opcode.FLOAD || opcode == Opcode.ILOAD ||
                opcode == Opcode.ALOAD ||opcode == Opcode.LSTORE || opcode == Opcode.DSTORE ||opcode == Opcode.FSTORE || 
                opcode == Opcode.ISTORE || opcode == Opcode.ASTORE) {
                    counters.nloadstore++;
            }
        }
    }



    public static void printStatistics() throws IOException, InterruptedException {
        long threadID = Thread.currentThread().getId();
        Counters counters = threadCounters.get(threadID);
        counters.cpuTime += counters.threadMXBean.getCurrentThreadCpuTime();

        //split the counters.requestType string
        String[] requestTypeSplit = counters.requestType.split(" ");
        String [] attributes = counters.requestType.split("[/: ]");

        int att2 = 0;

        try{
            att2 = compressiontypes.get(attributes[attributes.length - 2]);
        }
        catch (NullPointerException e){
            att2 = Integer.parseInt(attributes[attributes.length - 2]);
        }


        DynamoDBInfo dynamoDB = new DynamoDBInfo();
        dynamoDB.requestID = counters.requestType;
        dynamoDB.type = requestTypeSplit[0];
        dynamoDB.att1 = Integer.parseInt(attributes[attributes.length - 3]);
        dynamoDB.att2 = att2;
        dynamoDB.att3 = Float.parseFloat(attributes[attributes.length - 1]);
        dynamoDB.insts = counters.ninsts;
        dynamoDB.nloadstore = counters.nloadstore;
        dynamoDB.cpuTime = counters.cpuTime;
        

        dynamoDBQueue.add(dynamoDB);

        

        String statistics = String.format("[%s] %s\n[%s] Number of executed methods: %s\n[%s] Number of executed basic blocks: %s\n[%s] Number of executed instructions: %s\n[%s] Number of executed load/store instructions: %s\n[%s] CPU Time: %s (ns)", 
        ICount.class.getSimpleName(), counters.requestType, ICount.class.getSimpleName(), counters.nmethods, ICount.class.getSimpleName(), 
        counters.nblocks, ICount.class.getSimpleName(), counters.ninsts, ICount.class.getSimpleName(), counters.nloadstore, 
        ICount.class.getSimpleName(), counters.cpuTime);
        
        System.out.println(statistics);
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
        behavior.insertAfter(String.format("%s.incBehavior(\"%s\");", 
            ICount.class.getName(), behavior.getLongName()));
        if (!(behavior instanceof CtConstructor)) {
            behavior.insertAfter(String.format("%s.incInstructions(\"%s\", \"%s\");", 
                ICount.class.getName(), behavior.getDeclaringClass().getName(), behavior.getName()));
        }

        if (behavior.getName().equals("process") || behavior.getName().equals("war") || 
            behavior.getName().equals("runSimulation")) {
            behavior.insertBefore(String.format("%s.getFields();", 
                ICount.class.getName()));
            behavior.insertBefore(String.format("%s.newThread();", 
                ICount.class.getName()));
            behavior.insertAfter(String.format("%s.printStatistics();", 
                ICount.class.getName()));
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);

        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s);", 
            ICount.class.getName(), block.getPosition(), block.getLength()));
    }

}
