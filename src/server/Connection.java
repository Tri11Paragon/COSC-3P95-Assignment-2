package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class Connection extends Thread {

    private final Socket clientSocket;
    private final Server server;
    private BufferedWriter out;
    private BufferedReader in;

    public Connection(Server server, Socket clientSocket) {
        this.server = server;
        this.clientSocket = clientSocket;
        try {
            out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void run() {
        while (server.isRunning()) {

        }
        try {
            out.close();
            in.close();
            clientSocket.close();
        } catch (Exception ignored) {}
    }

}
