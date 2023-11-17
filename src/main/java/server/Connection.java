package server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import shared.ExceptionLogger;
import shared.FileUtil;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class Connection implements Runnable {

    private final Socket clientSocket;
    private final Server server;
    private DataOutputStream out;
    private DataInputStream in;
    private Tracer trace;
    private Span fileSend;

    public Connection(Server server, Tracer trace, Span parent, Socket clientSocket) {
        this.server = server;
        this.clientSocket = clientSocket;
        this.trace = trace;
        try {
            out = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
            in = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
        } catch (Exception e) {
            ExceptionLogger.log(e);
        }
        parent.addEvent("Connection Established");
        SpanBuilder sb = trace.spanBuilder("New Connection");
        sb.setAttribute("INetAddress", clientSocket.getInetAddress().toString());
        sb.setAttribute("Port", clientSocket.getPort());
        sb.setAttribute("LocalPort", clientSocket.getLocalPort());
        fileSend = sb.startSpan();
    }

    @Override
    public void run() {
        try (Scope scope = fileSend.makeCurrent()) {
            int filesReceived = 0;
            while (server.isRunning()) {
                if (!clientSocket.isConnected()) {
                    System.out.println("Client Disconnected");
                    break;
                }
                try {
                    if (in.available() > 0) {
                        fileSend.addEvent("File Received");
                        Span fileIn = trace.spanBuilder("File Received").setAttribute("Files Received", filesReceived).startSpan();
                        try (Scope s = fileIn.makeCurrent()){
                            byte command = in.readByte();

                            if (command == FileUtil.COMMAND.CLOSE.type) {
                                System.out.println("Client sent disconnect signal!");
                                break;
                            }
                            if (command == FileUtil.COMMAND.WRITE.type)
                                FileUtil.receive(in, trace, fileIn);
                        } finally {
                            fileIn.end();
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            fileSend.end();
        }
        try {
            out.close();
            in.close();
            clientSocket.close();
        } catch (Exception ignored) {}
        Server.running = false;
        Server.close();
    }

}
