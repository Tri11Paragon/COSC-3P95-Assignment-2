package server;

import shared.ExceptionLogger;
import java.io.IOException;
import java.net.ServerSocket;

public class Server {

    public static final int SERVER_PORT = 42069;

    private volatile boolean running = true;

    public Server() {
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);

            while (running) {
                new Connection(this, serverSocket.accept()).start();
            }

            serverSocket.close();
        } catch (IOException e) {
            ExceptionLogger.log(e);
        }
    }

    public boolean isRunning(){
        return running;
    }

    public static void main(String[] args) {
        new Server();
    }

}