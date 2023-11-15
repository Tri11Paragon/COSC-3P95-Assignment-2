package client;

import net.jpountz.xxhash.StreamingXXHash64;
import shared.ArrayData;
import shared.FileUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class FileChunkingWriter {

    private final DataOutputStream writer;
    private final StreamingXXHash64 streamHash;
    private final DataInputStream fileReader;
    private final int bufferSize;
    private final long seed;

    public FileChunkingWriter(DataOutputStream writer, DataInputStream fileReader, int bufferSize, long seed){
        this.writer = writer;
        this.streamHash = FileUtil.XX_HASH_FACTORY.newStreamingHash64(seed);
        this.fileReader = fileReader;
        this.bufferSize = bufferSize;
        this.seed = seed;
    }

    public void processChunk() throws IOException {
        // read / write files in chunks
        byte[] uncompressed = readSome();
        if (uncompressed.length == 0)
            return;

        // create a checksum for this chunk + update the overall checksum
        long hash = hash(uncompressed);

        // apply compression
        ArrayData compressed = compress(uncompressed);

        // write data
        writer.writeInt(uncompressed.length);
        writer.writeInt(compressed.getActualLength());
        writer.writeLong(hash);
        writer.write(compressed.getData(), 0, compressed.getActualLength());
        writer.flush();
    }

    public void close() throws IOException {
        writer.writeInt(0);
        writer.writeLong(streamHash.getValue());
        writer.flush();
    }

    private byte[] readSome() throws IOException {
        byte[] readBytes = new byte[Integer.min(fileReader.available(), bufferSize)];

        int totalRead = fileReader.read(readBytes);
        assert(readBytes.length == totalRead);

        return readBytes;
    }

    private long hash(byte[] input){
        streamHash.update(input, 0, input.length);
        return FileUtil.HASH_64.hash(input, 0, input.length, seed);
    }

    private ArrayData compress(byte[] input){
        int maxCompressedLength = FileUtil.COMPRESSOR.maxCompressedLength(input.length);
        byte[] compressedBytes = new byte[maxCompressedLength];
        int compressedLength = FileUtil.COMPRESSOR.compress(input, 0, input.length, compressedBytes, 0, maxCompressedLength);
        return new ArrayData(compressedBytes, compressedLength);
    }

}
