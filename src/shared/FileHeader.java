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
            DataInputStream fileReader = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(full_path))));

            writer.writeByte(COMMAND.WRITE.type);
            writer.writeUTF(relative_path);

            StreamingXXHash64 streamHash = XX_HASH_FACTORY.newStreamingHash64(SEED);
            while (fileReader.available() > 0) {
                // read / write files in chunks
                byte[] readBytes = new byte[Integer.min(fileReader.available(), READER_SIZE)];

                int totalRead = fileReader.read(readBytes);
                if (totalRead <= 0)
                    break;

                // create a checksum for this chunk + update the overall checksum
                streamHash.update(readBytes, 0, totalRead);
                long hash = HASH_64.hash(readBytes, 0, totalRead, SEED);

                // apply compression
                int maxCompressedLength = COMPRESSOR.maxCompressedLength(readBytes.length);
                byte[] compressedBytes = new byte[maxCompressedLength];
                int compressedLength = COMPRESSOR.compress(readBytes, 0, readBytes.length, compressedBytes, 0, maxCompressedLength);

                writer.writeInt(totalRead);
                writer.writeInt(compressedLength);
                writer.writeLong(hash);
                writer.write(compressedBytes, 0, compressedLength);
                writer.flush();
            }
            fileReader.close();
            writer.writeInt(0);
            writer.writeLong(streamHash.getValue());
            writer.flush();
        } catch (Exception e) {
            ExceptionLogger.log(e);
        }
    }

    public static void receive(DataInputStream reader) {
        try {
            String path = createPath(reader.readUTF());
            System.out.println("Writing to file: " + path);

            DataOutputStream writer = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(path))));

            StreamingXXHash64 computedStreamHash = XX_HASH_FACTORY.newStreamingHash64(SEED);
            while (true) {
                int uncompressed_size = reader.readInt();

                if (uncompressed_size <= 0)
                    break;

                int compressed_size = reader.readInt();
                long hash = reader.readLong();
                byte[] data = new byte[compressed_size];
                int amount = reader.read(data, 0, compressed_size);

                assert(amount == compressed_size);

                byte[] restored = new byte[uncompressed_size];
                int len = DECOMPRESSOR.decompress(data, 0, restored, 0, uncompressed_size);

                assert(len == uncompressed_size);

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
        } catch (Exception e) {
            ExceptionLogger.log(e);
        }
    }

    private static String createPath(String userFile) {
        String[] pathParts = userFile.split("/");
        String userDirectory = userFile.replace(pathParts[pathParts.length - 1], "");

        File ld = new File(System.getProperty("user.dir") + "/write/" + userDirectory);
        if (!ld.exists() && !ld.mkdirs())
            System.out.println("Failed to create directory");

        return System.getProperty("user.dir") + "/write/" + userFile;
    }

}
