package server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import net.jpountz.xxhash.StreamingXXHash64;
import shared.FileUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ChunkedCompressedChecksumFileReader {

    private final DataInputStream networkStreamReader;
    private final StreamingXXHash64 streamHash;
    private final DataOutputStream fileOutputWriter;
    private final long seed;

    private Span currentSpan = null;
    private Scope currentScope = null;

    private long count = 0;
    private long uncompressed_bytes = 0;
    private long compressed_bytes = 0;

    public ChunkedCompressedChecksumFileReader(DataInputStream networkStreamReader, String fileOutputPath, long seed) throws IOException {
        this.networkStreamReader = networkStreamReader;
        this.streamHash = FileUtil.XX_HASH_FACTORY.newStreamingHash64(seed);
        this.fileOutputWriter = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(fileOutputPath))));
        this.seed = seed;
    }

    public FileHeader readChunk(Tracer trace) throws IOException {
        if (++count >= FileUtil.MAX_COUNT) {
            currentSpan.addEvent("--{End Read}--");
            currentSpan.setStatus(StatusCode.OK);
            currentScope.close();
            currentSpan.end();
            currentSpan = null;
            currentScope = null;
            count = 0;
        }
        if (currentSpan == null) {
            currentSpan = trace.spanBuilder("Chunk Read").startSpan();
            currentScope = currentSpan.makeCurrent();
        }
        FileHeader header = readHeader();
        uncompressed_bytes += header.getUncompressed();
        compressed_bytes += header.getCompressed();
        currentSpan.addEvent("--{Begin Read}--");
        currentSpan.addEvent("Attribute: Read Uncompressed = " + header.getUncompressed());
        currentSpan.addEvent("Attribute: Read Compressed = " + header.getCompressed());
        currentSpan.addEvent("Attribute: Compression Ratio = " + ((double)header.getUncompressed() / header.getCompressed()));
        currentSpan.addEvent("Attribute: Read Hash = " + header.getHash());
        if (header.getUncompressed() == 0)
            return header;
        currentSpan.addEvent("Read Data");
        byte[] data = readSome(header);
        currentSpan.addEvent("Decompress Data");
        byte[] decompressed = decompress(header, data);
        currentSpan.addEvent("Hash");
        hash(header, decompressed);
        currentSpan.addEvent("Write");
        fileOutputWriter.write(decompressed, 0, decompressed.length);
        return header;
    }

    public void close() throws IOException {
        long streamHash = networkStreamReader.readLong();
        if (streamHash != this.streamHash.getValue())
            throw new RuntimeException("Stream total hash doesn't match the client's sent hash!");
        fileOutputWriter.flush();
        fileOutputWriter.close();
        if (currentSpan != null) {
            currentSpan.addEvent("--{End Read}--");
            currentScope.close();
            currentSpan.end();
        }
    }

    public long getCompressedBytes(){
        return compressed_bytes;
    }

    public long getUncompressedBytes(){
        return uncompressed_bytes;
    }

    public double getRatio(){
        if (compressed_bytes == 0)
            return 0;
        return (double) uncompressed_bytes / (double) compressed_bytes;
    }

    private FileHeader readHeader() throws IOException {
        return new FileHeader().read(networkStreamReader);
    }

    private byte[] readSome(FileHeader header) throws IOException {
        byte[] data = new byte[header.getCompressed()];
        int amount = networkStreamReader.read(data, 0, header.getCompressed());
        assert (header.getCompressed() == amount);
        return data;
    }

    private byte[] decompress(FileHeader header, byte[] data) {
        byte[] restored = new byte[header.getUncompressed()];
        int len = FileUtil.DECOMPRESSOR.decompress(data, 0, restored, 0, header.getUncompressed());
        assert (header.getUncompressed() == len);
        return restored;
    }

    private void hash(FileHeader header, byte[] data) {
        long computedHash = FileUtil.HASH_64.hash(data, 0, data.length, seed);
        streamHash.update(data, 0, data.length);
        if (computedHash != header.getHash())
            throw new RuntimeException("Computed hash doesn't match sent hash! File corrupted?");
    }

}
