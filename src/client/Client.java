package client;

import server.Server;
import shared.ExceptionLogger;
import shared.FileUtil;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class Client {

    private final Socket serverConnection;
    private final DataOutputStream out;
    private final DataInputStream in;

    public Client(String address, int port) throws IOException {
        serverConnection = new Socket(address, port);
        out = new DataOutputStream(new BufferedOutputStream(serverConnection.getOutputStream()));
        in = new DataInputStream(new BufferedInputStream(serverConnection.getInputStream()));
    }

    public Client sendFile(String path){
        System.out.println("Sending path " + path);
        FileUtil.write(path, out);
        return this;
    }

    public Client sendDir(String path){
        File p = new File(path);
        ArrayDeque<File> filesToCheck = new ArrayDeque<>(Arrays.asList(Objects.requireNonNull(p.listFiles())));
        while (!filesToCheck.isEmpty()) {
            File f = filesToCheck.remove();
            if (f.isDirectory()){
                filesToCheck.add(f);
            } else
                sendFile(f.getPath());
        }
        return this;
    }

    public void close(){
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
            new Client("localhost", Server.SERVER_PORT).sendDir("in/").close();
            //new Client("localhost", Server.SERVER_PORT).sendFile("in/ihaveafile.txt").close();
        } catch (Exception e){
            ExceptionLogger.log(e);
        }
    }

}
