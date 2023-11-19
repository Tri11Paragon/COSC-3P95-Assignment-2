package client;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import server.Server;
import shared.ExceptionLogger;
import shared.FileUtil;
import shared.OTelUtils;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class Client {

    private final Socket serverConnection;
    private final DataOutputStream out;
    private final DataInputStream in;
    private OTelUtils.SexyContainer ot = OTelUtils.create("CumClient"); // Computational Unit Machine Client
    private final Tracer tracer;
    private final Scope sc;
    private final Span s;

    public Client(String address, int port) throws IOException {
        serverConnection = new Socket(address, port);
        out = new DataOutputStream(new BufferedOutputStream(serverConnection.getOutputStream()));
        in = new DataInputStream(new BufferedInputStream(serverConnection.getInputStream()));
        tracer = ot.ot.getTracer("A Client", "1.33.7");
        s = tracer.spanBuilder("Client Connection").setAttribute("Server Address", address).setAttribute("Server Port", port).startSpan();
        sc = s.makeCurrent();
    }

    public Client sendFile(String path) {
        Span sp = tracer.spanBuilder("Send File").setAttribute("File", path).startSpan();
        try (Scope seethe = sp.makeCurrent()) {
            System.out.println("Sending path " + path);
            FileUtil.write(path, out, tracer, sp);
            System.out.println("Sent path " + path);
        } finally {
            sp.end();
        }
        ot.tp.forceFlush();
        ot.bp.forceFlush();
        ot.ox.flush();
        return this;
    }

    public Client sendDir(String path) {
        Span sd = tracer.spanBuilder("Send Directory").setAttribute("Directory", path).startSpan();
        try (Scope cope = sd.makeCurrent()) {
            File p = new File(path);
            ArrayDeque<File> filesToCheck = new ArrayDeque<>(Arrays.asList(Objects.requireNonNull(p.listFiles())));
            while (!filesToCheck.isEmpty()) {
                File f = filesToCheck.remove();
                System.out.println("Processing file " + f.getPath());
                if (f.isDirectory())
                    filesToCheck.addAll(Arrays.asList(Objects.requireNonNull(f.listFiles())));
                else
                    sendFile(f.getPath());
            }
        } finally {
            sd.setStatus(StatusCode.OK);
            sd.end();
        }
        System.out.println("Sent directory " + path);
        return this;
    }

    public Client close() {
        try {
            sc.close();
            s.setStatus(StatusCode.OK);
            s.end();
            out.writeByte(FileUtil.COMMAND.CLOSE.type);
            out.flush();
            in.close();
            out.close();
            serverConnection.close();
        } catch (Exception e) {
            ExceptionLogger.log(e);
        }
        System.out.println("Disconnected!");
        return this;
    }

    public OTelUtils.SexyContainer getContainer() {
        return ot;
    }

    public static void main(String[] args) {
        try {
            OTelUtils.SexyContainer con = new Client("localhost", Server.SERVER_PORT).sendDir("in/").close().getContainer();
            con.tp.forceFlush();
            con.tp.shutdown();
            //new Client("localhost", Server.SERVER_PORT).sendFile("in/ihaveafile.txt").close();
        } catch (Exception e) {
            ExceptionLogger.log(e);
        }
    }

}
