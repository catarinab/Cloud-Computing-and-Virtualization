package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;

import javassist.CannotCompileException;
import javassist.CtBehavior;

public class ICount extends CodeDumper {

    private static class Counters {
        public int n_generations, world, n_scenario;
        
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
    }

    private static ConcurrentHashMap<Long, Counters> threadCounters;

    public ICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
        threadCounters = new ConcurrentHashMap<Long, Counters>();
    }

    public static void newThread() {
        long threadID = Thread.currentThread().getId();
        threadCounters.put(threadID, new Counters());
    }

    public static void saveFields(int n_generations, int world, int n_scenario) {
        long threadID = Thread.currentThread().getId();
        Counters threadCounter = threadCounters.get(threadID);
        threadCounter.n_generations = n_generations;
        threadCounter.world = world;
        threadCounter.n_scenario = n_scenario;
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

    public static void printStatistics() throws IOException {
        long threadID = Thread.currentThread().getId();
        Counters counters = threadCounters.get(threadID);
        String statistics = String.format("[%s] Generations/World/Scenario: %s/%s/%s\n[%s] Number of executed methods: %s\n[%s] Number of executed basic blocks: %s\n[%s] Number of executed instructions: %s", ICount.class.getSimpleName(), counters.n_generations, counters.world, counters.n_scenario, ICount.class.getSimpleName(), counters.nmethods, ICount.class.getSimpleName(), counters.nblocks, ICount.class.getSimpleName(), counters.ninsts);
        
        System.out.println(statistics);

        String path = String.format("Statistics/%s.txt", threadID);
        File file = new File(path);
        if (!file.exists()) file.createNewFile();
        FileWriter fr = new FileWriter(file, true);
        fr.write(statistics + "\n\n");
        fr.close();
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);

        behavior.insertAfter(String.format("%s.incBehavior(\"%s\");", 
            ICount.class.getName(), behavior.getLongName()));

        if (behavior.getName().equals("handle")) {
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
