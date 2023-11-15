package shared;

import client.Client;
import client.FileChunkingWriter;
import net.jpountz.xxhash.StreamingXXHash64;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileHeader {

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

    public void write(DataOutputStream dataOut) {
        try {
            DataInputStream fileReader = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(full_path))));

            dataOut.writeByte(COMMAND.WRITE.type);
            dataOut.writeUTF(relative_path);

            FileChunkingWriter writer = new FileChunkingWriter(dataOut, fileReader, FileUtil.READER_SIZE, FileUtil.SEED);

            while (fileReader.available() > 0)
                writer.processChunk();

            writer.close();
            fileReader.close();
        } catch (Exception e) {
            ExceptionLogger.log(e);
        }
    }

    public static void receive(DataInputStream reader) {
        try {
            String path = createPath(reader.readUTF());
            System.out.println("Writing to file: " + path);

            DataOutputStream writer = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(path))));

            StreamingXXHash64 computedStreamHash = FileUtil.XX_HASH_FACTORY.newStreamingHash64(FileUtil.SEED);
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
                int len = FileUtil.DECOMPRESSOR.decompress(data, 0, restored, 0, uncompressed_size);

                assert(len == uncompressed_size);

                long computedHash = FileUtil.HASH_64.hash(restored, 0, uncompressed_size, FileUtil.SEED);
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
