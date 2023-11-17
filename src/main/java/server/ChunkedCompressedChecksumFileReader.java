package server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import net.jpountz.xxhash.StreamingXXHash64;
import shared.ArrayData;
import shared.FileUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class ChunkedCompressedChecksumFileReader {

    private final DataInputStream networkStreamReader;
    private final StreamingXXHash64 streamHash;
    private final DataOutputStream fileOutputWriter;
    private final long seed;

    public ChunkedCompressedChecksumFileReader(DataInputStream networkStreamReader, String fileOutputPath, long seed) throws IOException {
        this.networkStreamReader = networkStreamReader;
        this.streamHash = FileUtil.XX_HASH_FACTORY.newStreamingHash64(seed);
        this.fileOutputWriter = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(fileOutputPath))));
        this.seed = seed;
    }

    public FileHeader readChunk(Tracer trace, Span sp) throws IOException {
        //Span gf = trace.spanBuilder("Chunk Read").setParent(Context.current().with(sp)).startSpan();
        FileHeader header = readHeader();
        //try (Scope scope = gf.makeCurrent()) {
            if (header.getUncompressed() == 0)
                return header;
            sp.addEvent("Read Data");
            byte[] data = readSome(header);
            sp.addEvent("Decompress Data");
            byte[] decompressed = decompress(header, data);
            sp.addEvent("Hash");
            hash(header, decompressed);
            sp.addEvent("Write");
            fileOutputWriter.write(decompressed, 0, decompressed.length);
            sp.addEvent("End");
//        } finally {
//            gf.end();
//        }
        return header;
    }

    public void close() throws IOException {
        long streamHash = networkStreamReader.readLong();
        if (streamHash != this.streamHash.getValue())
            throw new RuntimeException("Stream total hash doesn't match the client's sent hash!");
        fileOutputWriter.flush();
        fileOutputWriter.close();
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
