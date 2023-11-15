package server;

import shared.ExceptionLogger;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static final int SERVER_PORT = 42069;

    private volatile boolean running = true;

    public Server() {
        System.out.println("Starting server");
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("Server Started");

            while (running)
                executor.execute(new Connection(this, serverSocket.accept()));

            serverSocket.close();
        } catch (IOException e) {
            ExceptionLogger.log(e);
        }
        executor.shutdown();
    }

    public boolean isRunning(){
        return running;
    }

    public static void main(String[] args) {
        new Server();
    }

}