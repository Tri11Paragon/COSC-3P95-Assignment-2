package server;

import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import shared.ExceptionLogger;
import shared.FileHeader;

import java.io.*;
import java.net.Socket;

public class Connection extends Thread {

    private final Socket clientSocket;
    private final Server server;
    private DataOutputStream out;
    private DataInputStream in;

    public Connection(Server server, Socket clientSocket) {
        this.server = server;
        this.clientSocket = clientSocket;
        try {
            out = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
            in = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
        } catch (Exception e) {
            ExceptionLogger.log(e);
        }
    }

    @Override
    public void run() {
        while (server.isRunning()) {
            if (!clientSocket.isConnected())
                break;
            try {
                if (in.available() > 0) {
                    byte command = in.readByte();

                    if (command == FileHeader.COMMAND.WRITE.type)
                        FileHeader.receive(in);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            out.close();
            in.close();
            clientSocket.close();
        } catch (Exception ignored) {}
    }

}
