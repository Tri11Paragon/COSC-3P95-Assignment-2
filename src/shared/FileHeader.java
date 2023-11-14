package shared;

import client.Client;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.lz4.LZ4SafeDecompressor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileHeader {

    public static final int READER_SIZE = 8192;

    private static final LZ4Factory factory = LZ4Factory.fastestInstance();
    private static final LZ4Compressor compressor = factory.highCompressor();
    private static final LZ4FastDecompressor decompressor = factory.fastDecompressor();

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

            while (reader.available() > 0) {
                // read / write files in chunks
                int read = Integer.min(reader.available(), READER_SIZE);
                byte[] bytes = new byte[read];

                int amount = reader.read(bytes);
                if (amount <= 0)
                    break;

                // apply compression
                int maxCompressedLength = compressor.maxCompressedLength(bytes.length);
                byte[] compressed = new byte[maxCompressedLength];
                int compressedLength = compressor.compress(bytes, 0, bytes.length, compressed, 0, maxCompressedLength);

                System.out.println("Writing " + compressedLength + "  bytes");
                writer.writeInt(amount);
                writer.writeInt(compressedLength);
                writer.write(compressed, 0, compressedLength);
                writer.flush();
            }
            reader.close();
            writer.writeInt(0);
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
            while ((uncompressed_size = reader.readInt()) > 0) {
                int compressed_size = reader.readInt();
                byte[] data = new byte[compressed_size];
                int amount = reader.read(data, 0, compressed_size);

                byte[] restored = new byte[uncompressed_size];

                int len = decompressor.decompress(data, 0, restored, 0, uncompressed_size);

                writer.write(restored, 0, uncompressed_size);
            }
            writer.flush();
            writer.close();
        } catch (Exception e){
            ExceptionLogger.log(e);
        }
    }

}
