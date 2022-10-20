import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * If a class is runnable, it means that it can be passed to a Thread or Thread pool, and
 * then can be executed concurrently alongside with other classes that implement the Runnable interface.
 *
 * I'm going to have a server that will constantly listen for incoming connections (so for client
 * requests to connect). Then, it's going to accept these connection requests, and then we're going to
 * open a new connection handler for each client that connects. So, we're going to have an inner class
 * @class ConnectionHandler that handles client connections. I'm going pass a client to it, and then it's
 * going to handle individual connections.
 */
public class Server implements Runnable {
    private ArrayList<ConnectionHandler> connections;
    private ServerSocket server;
    private boolean done;

    /**
     * Now, that we didn't do yet is we didn't run the individual threads. So, now all these handlers need to be run
     * in a thread pool. A thread pool is basically the number of threads that can be reused all the time, so we don't
     * need to run a new thread and close. I mean, we don't have to create a new specific thread in order to run a
     * handler, because we have a lot of different client connections then they disconnect again, whereas the server is
     * running in one thread all the time, client connections are short-lived oftentimes, and because of that I'm going
     * to use a thread pool.
     *
     * I'm going to define the thread pool locally.
     */
    private ExecutorService pool;


    public Server() {
        connections = new ArrayList<>();
        done = false;
    }
    /**
     * First, I want to create a ServerSocket for the server itself. All we need to pass to arguments is a port number.
     * And, in here, we have to handle the IOException because it's something we have always work with when we
     * use some writers, or readers, or sockets in Java.
     *
     * This server has the accept() method and this method returns client sockets.
     *
     * And we don't want to deals with the client in here, so we want to open a new instance
     * of ConnectionHandler, because we need to handle multiple clients concurrently.
     */
    @Override
    public void run() {
        try {
            server = new ServerSocket(8080);
            pool = Executors.newCachedThreadPool();
            // We have to always accept connections, so, while done isn't true, we accept them.
            while (!done) {
                Socket client = server.accept();
                // Whenever we create a new client, what we want to do is we want to save a new ConnectionHandler.
                ConnectionHandler handler = new ConnectionHandler(client);
                connections.add(handler);
                // Every time we add a new connection, we want to add it into the thread pool.
                pool.execute(handler);
            }
        } catch (IOException e) {
            shutdown();
        }
    }

    /**
     * In order to broadcast to all the different clients that are connected all we need to do is we need
     * to implement a broadcast function.
     */
    public void broadcast(String message) {
        for (ConnectionHandler ch : connections) {
            if (ch != null) {
                ch.sendMessage(message);
            }
        }
    }

    /**
     * Function that shuts down the server. All I need to do is to shut down the server socket as well as each
     * individual connection.
     */
    public void shutdown() {
        try {
            done = true;
            if (!server.isClosed()) {
                server.close();
            }
            for (ConnectionHandler ch : connections) {
                ch.shutdown();
            }
        } catch (IOException e) {
            // ignore
        }

    }

    class ConnectionHandler implements Runnable {
        /**
         * Also, I'm going to have a BufferedReader (to get the stream from the sockets, so when a client sends
         * something, we're going to get it from in) and PrintWriter (when we want to write something to the client).
         */
        private Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private String nickname;

        /**
         * So, I'm going to define a constructor here that accepts the Socket that's a client
         * we are going to deal with.
         */
        public ConnectionHandler(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            /**
             * Here we're going to deals with the clients. We have to initialize the in and out streams.
             * I have to pass the output stream to the constructor of the PrintWriter that I can get directly from
             * the client. Also, we can set the autoFlush to true.
             * Then, I have to pass the InputStreamReader to the constructor of the BufferedReader, and pass an
             * input stream itself that can be fetched directly from the client.
             *
             * So, we can print something to the client using out.println("Hello") and read something from the client
             * using in.readLine().
             */
            try {
                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                // First, I want to send to the client a prompt that asks for the nickname
                out.println("Please, enter a nickname:");
                // Then, we're going to wait for the input from the client.
                // Of course, here I can add different sorts of check cases (whether a string is empty and so on).
                nickname = in.readLine();
                // Then, I'm going to say as a server log
                System.out.println("Server: " + nickname + " connected.");
                // Then, I want to broadcast it to all the other clients. For that, we're going to need
                // list of clients that are connected, and because of that we go to the Server class and define
                // a private ArrayList of connection handlers
                broadcast(nickname + " joined the chat!");
                // And, for this particular client, we want to always have a loop that asks for new messages.
                String message;
                while ((message = in.readLine()) != null) {
                    // Here I want to implement some commands
                    if (message.startsWith("/nick ")) {
                        // TODO: handle nickname
                        String[] messageSplit = message.split(" ", 2);
                        if (messageSplit.length == 2) {
                            broadcast(nickname + " renamed themselves to " + messageSplit[1]);
                            System.out.println("Server: " + nickname + " renamed themselves to " + messageSplit[1]);
                            nickname = messageSplit[1];
                            // Also, we want to say to the client that they have change the nickname successfully.
                            out.println("Nickname successfully changed to " + nickname);
                        } else {
                            out.println("No nickname was provided.");
                        }
                    } else if (message.startsWith(("/quit"))) {
                        broadcast(nickname + " left the chat.");
                        shutdown();
                    } else {
                        // Otherwise, broadcast the message immediately to all the other
                        broadcast(nickname + ": " + message);
                    }
                }
            } catch (IOException e) {
                shutdown();
            }
        }

        /**
         * In order to be able to send something to the client via the handler, we need to
         * implement a function here which is just going to send a message.
         */
        public void sendMessage(String message) {
            out.println(message);
        }

        public void shutdown() {
            try {
                in.close();
                out.close();
                if (!client.isClosed()) {
                    client.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }
}