package client;

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

    public ChunkedCompressedChecksumFileWriter(DataOutputStream networkStreamWriter, DataInputStream fileInputReader, int bufferSize, long seed){
        this.networkStreamWriter = networkStreamWriter;
        this.streamHash = FileUtil.XX_HASH_FACTORY.newStreamingHash64(seed);
        this.fileInputReader = fileInputReader;
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
        writeHeader(uncompressed.length, compressed.getActualLength(), hash);
        networkStreamWriter.write(compressed.getData(), 0, compressed.getActualLength());
        networkStreamWriter.flush();
    }

    public void close() throws IOException {
        networkStreamWriter.writeInt(0);
        networkStreamWriter.writeLong(streamHash.getValue());
        networkStreamWriter.flush();
    }

    private void writeHeader(int uncompressed, int compressed, long hash) throws IOException {
        networkStreamWriter.writeInt(uncompressed);
        networkStreamWriter.writeInt(compressed);
        networkStreamWriter.writeLong(hash);
    }

    private byte[] readSome() throws IOException {
        byte[] readBytes = new byte[Integer.min(fileInputReader.available(), bufferSize)];

        int totalRead = fileInputReader.read(readBytes);
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
