package client;

import server.Server;
import shared.ExceptionLogger;
import shared.FileHeader;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class Client {

    public static class ClientInvalidUsageException extends RuntimeException {
        public ClientInvalidUsageException(String str){
            super(str);
        }
    }

    private final Socket serverConnection;
    private final DataOutputStream out;
    private final DataInputStream in;

    public Client(String address, int port) throws IOException {
        serverConnection = new Socket(address, port);
        out = new DataOutputStream(new BufferedOutputStream(serverConnection.getOutputStream()));
        in = new DataInputStream(new BufferedInputStream(serverConnection.getInputStream()));
    }

    void sendFile(String path){
        if (new File(path).isDirectory())
            throw new ClientInvalidUsageException("Unable to send directory. Did you mean sendDir()?");
        System.out.println("Sending path " + path);
        new FileHeader(path).write(out);
        try {
            out.flush();
        } catch (IOException e) {
            ExceptionLogger.log(e);
        }
    }

    void sendDir(String path){
        File p = new File(path);
        ArrayDeque<File> filesToCheck = new ArrayDeque<>(Arrays.asList(Objects.requireNonNull(p.listFiles())));
        while (!filesToCheck.isEmpty()) {
            File f = filesToCheck.remove();
            if (f.isDirectory()){
                filesToCheck.add(f);
            } else
                sendFile(f.getPath());
        }
    }

    void close(){
        try {
            in.close();
            out.close();
            serverConnection.close();
        } catch (Exception e){
            ExceptionLogger.log(e);
        }
    }

    public static void main(String[] args) {
        try {
            new Client("localhost", Server.SERVER_PORT).sendDir("in/");
        } catch (Exception e){
            ExceptionLogger.log(e);
        }
    }

}
