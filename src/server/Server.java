package server;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import shared.ExceptionLogger;
import shared.OTelUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {

    private static final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static final int SERVER_PORT = 42069;

    private volatile boolean running = true;

    private static final OpenTelemetry ot = OTelUtils.create();

    public Server() {
        Tracer main = ot.getTracer("Main Server", "0.69");
        System.out.println("Starting server");
        SpanBuilder sb = main.spanBuilder("Start Server");
        Span sbs = sb.startSpan();
        try {
            sbs.addEvent("Server Start", System.nanoTime(), TimeUnit.NANOSECONDS);
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("Server Started");

            while (running)
                executor.execute(new Connection(this, main, sbs, serverSocket.accept()));

            serverSocket.close();
        } catch (IOException e) {
            sbs.recordException(e);
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