// ChatState
//package cs149.chat;

import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ChatState {
    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition(); 

    private static final int MAX_HISTORY = 32;

    private final String name;
    private final LinkedList<Map.Entry<Long, String>> history = new LinkedList<Map.Entry<Long, String>>();
    private long lastID = System.currentTimeMillis();

    public ChatState(final String name) {
        this.name = name;
        history.addLast(new AbstractMap.SimpleEntry<Long, String>(lastID, "Hello " + name + "!"));
    }

    public String getName() {
        return name;
    }

    public void addMessage(final String msg) throws InterruptedException {
        lock.lock();
        try {
            history.addLast(new AbstractMap.SimpleEntry<Long, String>(++lastID, msg));
            while (history.size() > MAX_HISTORY) {
            	history.removeFirst();
            }
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public String recentMessages(long mostRecentSeenID) throws InterruptedException {
        lock.lock();
        try {
            final StringBuilder sb = new StringBuilder();
            for (Map.Entry<Long, String> kv : history) 
                if (kv.getKey() > mostRecentSeenID)
                sb.append(kv.getKey() + ": " + kv.getValue() + "\n");
            if (!sb.toString().equals("")) 
                return sb.toString();
            else if (!notEmpty.await(15, TimeUnit.SECONDS)) 
                return "";
            else {
                for (Map.Entry<Long, String> kv : history) 
                	if (kv.getKey() > mostRecentSeenID){
                	sb.append(kv.getKey() + ": " + kv.getValue() + "\n");
                }
                return sb.toString();
            }
        } finally {
            lock.unlock();
        }
    }
}
