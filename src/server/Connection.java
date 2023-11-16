package server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
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
    private Span fileSend;

    public Connection(Server server, Tracer trace, Span parent, Socket clientSocket) {
        this.server = server;
        this.clientSocket = clientSocket;
        try {
            out = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
            in = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
        } catch (Exception e) {
            ExceptionLogger.log(e);
        }
        parent.addEvent("Connection Established", System.nanoTime(), TimeUnit.NANOSECONDS);
        SpanBuilder sb = trace.spanBuilder("New Connection");
        Context ctx = Context.current();
        parent.storeInContext(ctx);
        sb.setParent(ctx);
        sb.setAttribute("INetAddress", clientSocket.getInetAddress().toString());
        sb.setAttribute("Port", clientSocket.getPort());
        sb.setAttribute("LocalPort", clientSocket.getLocalPort());
        fileSend = sb.startSpan();
    }

    @Override
    public void run() {
        while (server.isRunning()) {
            if (!clientSocket.isConnected())
                break;
            try {
                if (in.available() > 0) {
                    byte command = in.readByte();

                    if (command == FileUtil.COMMAND.WRITE.type)
                        FileUtil.receive(in, fileSend);
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
