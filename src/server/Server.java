package server;

import java.io.IOException;
import java.net.ServerSocket;

public class Server {

    public static final int SERVER_PORT = 42069;

    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public Server() {
        try {
            serverSocket = new ServerSocket(SERVER_PORT);

            while (running) {
                new Connection(this, serverSocket.accept()).start();
            }

            serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isRunning(){
        return running;
    }

    public static void main(String[] args) {
        new Server();
    }

}