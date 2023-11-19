package shared;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.logging.SystemOutLogRecordExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.ResourceAttributes;

public class OTelUtils {

    public static class SexyContainer {
        public OpenTelemetry ot;
        public SdkTracerProvider tp;
        public BatchSpanProcessor bp;
        public SpanExporter ox;
        public SexyContainer(OpenTelemetry ot, SdkTracerProvider tp, BatchSpanProcessor bp, SpanExporter ox){
            this.ot = ot;
            this.tp = tp;
            this.bp = bp;
            this.ox = ox;
        }
    }

    public static OpenTelemetry createLogger(){
        Resource resource = Resource.getDefault().toBuilder().put(ResourceAttributes.SERVICE_NAME, "cum").put(ResourceAttributes.SERVICE_VERSION, "0.1.0").build();

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
                .setResource(resource)
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setMeterProvider(createLoggingMeter(resource))
                .setLoggerProvider(createLoggerProvider(resource))
                .setPropagators(ContextPropagators.create(TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance())))
                .buildAndRegisterGlobal();
    }

    public static SexyContainer create(String name){
        Resource resource = Resource.getDefault().toBuilder().put(ResourceAttributes.SERVICE_NAME.getKey(), name).put(ResourceAttributes.SERVICE_VERSION.getKey(), "1.3.37").build();

        SpanExporter otlpExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://sc.on.underlying.skynet.tpgc.me:4317")
                .setCompression("gzip")
                .build();

        BatchSpanProcessor batchSpanProcessor = BatchSpanProcessor.builder(otlpExporter)
                .setMaxQueueSize(2048)
                .setMaxExportBatchSize(512)
                .build();

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(batchSpanProcessor)
                .setResource(resource)
                .build();

        return new SexyContainer(OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setMeterProvider(createLoggingMeter(resource))
                .setLoggerProvider(createLoggerProvider(resource))
                .setPropagators(ContextPropagators.create(TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance())))
                .buildAndRegisterGlobal(), sdkTracerProvider, batchSpanProcessor, otlpExporter);
    }

    private static SdkMeterProvider createLoggingMeter(Resource resource){
        return SdkMeterProvider.builder()
                .registerMetricReader(PeriodicMetricReader.builder(LoggingMetricExporter.create()).build())
                .setResource(resource)
                .build();
    }

    private static SdkLoggerProvider createLoggerProvider(Resource resource){
        return SdkLoggerProvider.builder()
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(SystemOutLogRecordExporter.create()).build())
                .setResource(resource)
                .build();
    }

}
