// ChatServer
//package cs149.chat;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatServer {
    private static final Charset utf8 = Charset.forName("UTF-8");

    private static final String OK = "200 OK";
    private static final String NOT_FOUND = "404 NOT FOUND";
    private static final String HTML = "text/html";
    private static final String TEXT = "text/plain";

    private static final Pattern PAGE_REQUEST
            = Pattern.compile("GET /([^ /]+)/chat\\.html HTTP.*");
    private static final Pattern PULL_REQUEST
            = Pattern.compile("POST /([^ /]+)/pull\\?last=([0-9]+) HTTP.*");
    private static final Pattern PUSH_REQUEST
            = Pattern.compile("POST /([^ /]+)/push\\?msg=([^ ]*) HTTP.*");

    private static final String CHAT_HTML;
    static {
        try {
            CHAT_HTML = getResourceAsString("chat.html");
        } catch (final IOException xx) {
            throw new Error("unable to start server", xx);
        }
    }

    private final int port;
    private final Map<String,ChatState> stateByName = new HashMap<String,ChatState>();
    private final ChatState allRoom = new ChatState("all");

    /** Constructs a new <code>ChatServer</code> that will service requests on
     *  the specified <code>port</code>.  <code>state</code> will be used to
     *  hold the current state of the chat.
     */
    public ChatServer(final int port) throws IOException {
        this.port = port;
    }
    
    private class ThreadPool {
    	private final int nThreads;
    	private final Worker[] threads;
    	private final LinkedList<Runnable> taskQueue = new LinkedList<Runnable>();
    	
    	public ThreadPool(int _nThreads) {
    		nThreads = _nThreads;
    		threads = new Worker[nThreads];
    		for (int i = 0; i < nThreads; i++) {
    			threads[i] = new Worker();
    			threads[i].start();
    		}
    	}
    	public void submit(Runnable r) {
            synchronized(taskQueue) {
            	taskQueue.addLast(r);
            	taskQueue.notify();
            }
    	}
    	private class Worker extends Thread {
    		public void run() {
    			while (true) {
    				Runnable r;
    				synchronized(taskQueue) {
    					while (taskQueue.isEmpty()) {
    						try {
								taskQueue.wait();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
    					}
    					r = taskQueue.removeFirst();
    				}
    				r.run();	
    			}
    		}
    	}
    }
    public void runForever() throws Exception {
        final ThreadPool executor = new ThreadPool(8);
        final ServerSocket server = new ServerSocket(port);
        while (true) {
            final Socket connection = server.accept();
            Runnable handler = new Runnable() { public void run() { handle(connection); } };
            executor.submit(handler);
        }
    }

    private void handle(final Socket connection) {
        try {
            final BufferedReader xi
                    = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            final OutputStream xo = connection.getOutputStream();

            final String request = xi.readLine();
            System.out.println(Thread.currentThread() + ": " + request);
            
            if (request == null)
            	return;
            
            Matcher m;
            if (PAGE_REQUEST.matcher(request).matches()) {
                sendResponse(xo, OK, HTML, CHAT_HTML);
            }
            else if ((m = PULL_REQUEST.matcher(request)).matches()) {
                final String room = m.group(1);
                final long last = Long.valueOf(m.group(2));
                if (room.equals("all")) {
                	sendResponse(xo, OK, TEXT, allRoom.recentMessages(last));
                } else {
                    sendResponse(xo, OK, TEXT, getState(room).recentMessages(last));
                }
            }
            else if ((m = PUSH_REQUEST.matcher(request)).matches()) {
                final String room = m.group(1);
                final String msg = m.group(2);
                if (room.equals("all")) {
                	synchronized (allRoom) {
	                    synchronized (stateByName) {
	                        for (ChatState state : stateByName.values()) {
	                           state.addMessage(msg);
	                        }
	                        allRoom.addMessage(msg);
	                    }
                	}
                } else {
                	synchronized (allRoom) {
                		getState(room).addMessage(msg);
                		allRoom.addMessage(msg);
                	}
                }
                sendResponse(xo, OK, TEXT, "ack");
            }
            else {
                sendResponse(xo, NOT_FOUND, TEXT, "Nobody here with that name.");
            }

            connection.close();
        }
        catch (final Exception xx) {
            xx.printStackTrace();
            try {
                connection.close();
            }
            catch (final Exception yy) {
                // silently discard any exceptions here
            }
        }
    }

    /** Writes a minimal-but-valid HTML response to <code>output</code>. */
    private static void sendResponse(final OutputStream output,
                                     final String status,
                                     final String contentType,
                                     final String content) throws IOException {
        final byte[] data = content.getBytes(utf8);
        final String headers =
                "HTTP/1.0 " + status + "\n" +
                "Content-Type: " + contentType + "; charset=utf-8\n" +
                "Content-Length: " + data.length + "\n\n";

        final BufferedOutputStream xo = new BufferedOutputStream(output);
        xo.write(headers.getBytes(utf8));
        xo.write(data);
        xo.flush();

        System.out.println(Thread.currentThread() + ": replied with " + data.length + " bytes");
    }

    private ChatState getState(final String room) {
        synchronized (stateByName) {
            ChatState state = stateByName.get(room);
            if (state == null) {
                state = new ChatState(room);
                stateByName.put(room, state);
            }
            return state;
        }
   }

    /** Reads the resource with the specified name as a string, and then
     *  returns the string.  Resource files are searched for using the same
     *  classpath mechanism used for .class files, so they can either be in the
     *  same directory as bare .class files or included in the .jar file.
     */
    private static String getResourceAsString(final String name) throws IOException {
        final Reader xi = new InputStreamReader(
                ChatServer.class.getClassLoader().getResourceAsStream(name));
        try {
            final StringBuffer result = new StringBuffer();
            final char[] buf = new char[8192];
            int n;
            while ((n = xi.read(buf)) > 0) {
                result.append(buf, 0, n);
            }
            return result.toString();
        } finally {
            try {
                xi.close();
            } catch (final IOException xx) {
                // discard
            }
        }
    }

    /** Runs a chat server, with a default port of 8080. */
    public static void main(final String[] args) throws Exception {
        final int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        new ChatServer(port).runForever();
    }
}
