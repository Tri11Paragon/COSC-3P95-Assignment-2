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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {

    private static final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static final int SERVER_PORT = 42069;

    public static volatile boolean running = true;

    private static final OpenTelemetry ot = OTelUtils.create("CumServer");

    public Server() {
        Tracer main = ot.getTracer("Main Server", "0.69");
        try {
            System.out.println("Starting server");
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("Server Started");

            while (running)
                executor.execute(new Connection(this, main, serverSocket.accept()));

            serverSocket.close();
        } catch (IOException e) {
            ExceptionLogger.log(e);
        }
        System.out.println("Closing thread pool");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)){
                List<Runnable> runs = executor.shutdownNow();
                System.out.println("Hello runs " + runs.size());
                if (!executor.awaitTermination(1, TimeUnit.SECONDS))
                    System.out.println("Unable to terminate");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            ExceptionLogger.log(e);
        }
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
        if (srv != null)
            srv.notifyAll();
    }

}