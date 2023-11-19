package client;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import net.jpountz.xxhash.StreamingXXHash64;
import shared.ArrayData;
import shared.FileUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ChunkedCompressedChecksumFileWriter {

    private final DataOutputStream networkStreamWriter;
    private final StreamingXXHash64 streamHash;
    private final DataInputStream fileInputReader;
    private final int bufferSize;
    private final long seed;

    private Span currentSpan = null;
    private Scope currentScope = null;

    private long count = 0;
    private long uncompressed_bytes = 0;
    private long compressed_bytes = 0;

    public ChunkedCompressedChecksumFileWriter(DataOutputStream networkStreamWriter, DataInputStream fileInputReader, int bufferSize, long seed) {
        this.networkStreamWriter = networkStreamWriter;
        this.streamHash = FileUtil.XX_HASH_FACTORY.newStreamingHash64(seed);
        this.fileInputReader = fileInputReader;
        this.bufferSize = bufferSize;
        this.seed = seed;
    }

    public void processChunk(Tracer trace) throws IOException {
        if (++count >= FileUtil.MAX_COUNT) {
            currentSpan.addEvent("--{End Write}--");
            currentSpan.setStatus(StatusCode.OK);
            currentScope.close();
            currentSpan.end();
            currentSpan = null;
            currentScope = null;
            count = 0;
        }
        if (currentSpan == null) {
            currentSpan = trace.spanBuilder("Chunk Write").startSpan();
            currentScope = currentSpan.makeCurrent();
        }
        // read / write files in chunks
        currentSpan.addEvent("--{Begin Write}--");
        byte[] uncompressed = readSome();
        if (uncompressed.length == 0)
            return;

        // create a checksum for this chunk + update the overall checksum
        currentSpan.addEvent("Hash");
        long hash = hash(uncompressed);

        // apply compression
        currentSpan.addEvent("Compress");
        ArrayData compressed = compress(uncompressed);

        // track data
        currentSpan.addEvent("Attribute: Write Uncompressed = " + uncompressed.length);
        currentSpan.addEvent("Attribute: Write Compressed = " + compressed.getActualLength());
        currentSpan.addEvent("Attribute: Compression Ratio = " + ((double)uncompressed.length / (double)compressed.getActualLength()));
        currentSpan.addEvent("Attribute: Write Hash = " + hash);
        uncompressed_bytes += uncompressed.length;
        compressed_bytes += compressed.getActualLength();
        count++;

        // write data
        currentSpan.addEvent("Write");
        writeHeader(uncompressed.length, compressed.getActualLength(), hash);
        networkStreamWriter.write(compressed.getData(), 0, compressed.getActualLength());
        networkStreamWriter.flush();
    }

    public void close() throws IOException {
        networkStreamWriter.writeInt(0);
        networkStreamWriter.writeLong(streamHash.getValue());
        networkStreamWriter.flush();
        if (currentSpan != null) {
            currentSpan.addEvent("--{End Read}--");
            currentScope.close();
            currentSpan.end();
        }
    }

    private void writeHeader(int uncompressed, int compressed, long hash) throws IOException {
        networkStreamWriter.writeInt(uncompressed);
        networkStreamWriter.writeInt(compressed);
        networkStreamWriter.writeLong(hash);
    }

    private byte[] readSome() throws IOException {
        byte[] readBytes = new byte[Integer.min(fileInputReader.available(), bufferSize)];

        int totalRead = fileInputReader.read(readBytes);
        assert (readBytes.length == totalRead);

        return readBytes;
    }

    private long hash(byte[] input) {
        streamHash.update(input, 0, input.length);
        return FileUtil.HASH_64.hash(input, 0, input.length, seed);
    }

    private ArrayData compress(byte[] input) {
        int maxCompressedLength = FileUtil.COMPRESSOR.maxCompressedLength(input.length);
        byte[] compressedBytes = new byte[maxCompressedLength];
        int compressedLength = FileUtil.COMPRESSOR.compress(input, 0, input.length, compressedBytes, 0, maxCompressedLength);
        return new ArrayData(compressedBytes, compressedLength);
    }

    public long getCompressedBytes() {
        return compressed_bytes;
    }

    public long getUncompressedBytes() {
        return uncompressed_bytes;
    }

    public double getRatio() {
        if (compressed_bytes == 0)
            return 0;
        return (double) uncompressed_bytes / (double) compressed_bytes;
    }

}
