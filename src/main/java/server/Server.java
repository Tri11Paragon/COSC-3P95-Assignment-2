package server;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
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

    public static volatile boolean running = true;

    private static final OpenTelemetry ot = OTelUtils.create();

    public Server() {
        Tracer main = ot.getTracer("Main Server", "0.69");
        Span sbs = main.spanBuilder("Start Server").setAttribute("Server Port", SERVER_PORT).startSpan();
        try (Scope scope = sbs.makeCurrent()) {
            System.out.println("Starting server");
            sbs.addEvent("Server Start", System.nanoTime(), TimeUnit.NANOSECONDS);
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    System.out.println("Closing Server");
                    running = false;
                    sbs.end();
                    executor.shutdown();
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            System.out.println("Server Started");

            while (running)
                executor.execute(new Connection(this, main, sbs, serverSocket.accept()));

            serverSocket.close();
        } catch (IOException e) {
            sbs.recordException(e);
            ExceptionLogger.log(e);
        } finally {
            sbs.end();
        }
        System.out.println("Closing thread pool");
        executor.shutdown();
        System.out.println("Server exited!");
    }

    public boolean isRunning(){
        return running;
    }

    private static Server srv;

    public static void main(String[] args) {
        srv = new Server();
    }

    public static void close(){
        srv.notifyAll();
    }

}