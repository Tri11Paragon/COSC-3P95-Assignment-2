package shared;

import client.Client;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileHeader {

    private static final int READER_SIZE = 8192;

    private static final LZ4Factory LZ_FACTORY = LZ4Factory.fastestInstance();
    private static final LZ4Compressor COMPRESSOR = LZ_FACTORY.highCompressor();
    private static final LZ4FastDecompressor DECOMPRESSOR = LZ_FACTORY.fastDecompressor();

    private static final XXHashFactory XX_HASH_FACTORY = XXHashFactory.fastestInstance();
    private static final XXHash64 HASH_64 = XX_HASH_FACTORY.hash64();

    private static final long SEED = 691;

    public enum COMMAND {
        WRITE((byte) 1);
        public final byte type;

        COMMAND(byte type) {
            this.type = type;
        }
    }

    private final String relative_path;
    private final String full_path;

    public FileHeader(String path) {
        File pf = new File(path);
        if (!pf.exists())
            throw new Client.ClientInvalidUsageException("Unable to send a file which doesn't exist!");
        if (pf.isDirectory())
            throw new Client.ClientInvalidUsageException("Path is a directory unable to send!");
        String workingDirectory = System.getProperty("user.dir");
        this.full_path = path;
        this.relative_path = path.replace(workingDirectory, "");
        System.out.println(relative_path);
    }

    public void write(DataOutputStream writer) {
        try {
            DataInputStream reader = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(full_path))));

            writer.writeByte(COMMAND.WRITE.type);
            writer.writeUTF(relative_path);

            StreamingXXHash64 streamHash = XX_HASH_FACTORY.newStreamingHash64(SEED);
            while (reader.available() > 0) {
                // read / write files in chunks
                int read = Integer.min(reader.available(), READER_SIZE);
                byte[] bytes = new byte[read];

                int amount = reader.read(bytes);
                if (amount <= 0)
                    break;

                // create a checksum for this chunk + update the overall checksum
                streamHash.update(bytes, 0, amount);
                long hash = HASH_64.hash(bytes, 0, amount, SEED);

                // apply compression
                int maxCompressedLength = COMPRESSOR.maxCompressedLength(bytes.length);
                byte[] compressed = new byte[maxCompressedLength];
                int compressedLength = COMPRESSOR.compress(bytes, 0, bytes.length, compressed, 0, maxCompressedLength);

                System.out.println("Writing " + compressedLength + "  bytes");
                writer.writeInt(amount);
                writer.writeInt(compressedLength);
                writer.writeLong(hash);
                writer.write(compressed, 0, compressedLength);
                writer.flush();
            }
            reader.close();
            writer.writeInt(0);
            writer.writeLong(streamHash.getValue());
            writer.flush();
        } catch (Exception e) {
            ExceptionLogger.log(e);
        }
    }

    public static void receive(DataInputStream reader) {
        try {
            String userFile = reader.readUTF();
            String[] pathParts = userFile.split("/");
            String userDirectory = userFile.replace(pathParts[pathParts.length-1], "");

            File ld = new File(System.getProperty("user.dir") + "/write/" + userDirectory);
            if (!ld.exists())
                if(!ld.mkdirs())
                    System.out.println("Failed to create directory");

            String path = System.getProperty("user.dir") + "/write/" + userFile;
            System.out.println("Writing to file: " + path);

            DataOutputStream writer = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(path))));
            int uncompressed_size = 0;
            StreamingXXHash64 computedStreamHash = XX_HASH_FACTORY.newStreamingHash64(SEED);
            while ((uncompressed_size = reader.readInt()) > 0) {
                int compressed_size = reader.readInt();
                long hash = reader.readLong();
                byte[] data = new byte[compressed_size];
                int amount = reader.read(data, 0, compressed_size);

                byte[] restored = new byte[uncompressed_size];
                int len = DECOMPRESSOR.decompress(data, 0, restored, 0, uncompressed_size);

                long computedHash = HASH_64.hash(restored, 0, uncompressed_size, SEED);
                computedStreamHash.update(restored, 0, uncompressed_size);

                if (hash != computedHash)
                    throw new RuntimeException(hash + " HELP! " + computedHash);

                writer.write(restored, 0, uncompressed_size);
            }
            long streamHash = reader.readLong();
            if (computedStreamHash.getValue() != streamHash)
                throw new RuntimeException("HELP 22");
            writer.flush();
            writer.close();
        } catch (Exception e){
            ExceptionLogger.log(e);
        }
    }

}
