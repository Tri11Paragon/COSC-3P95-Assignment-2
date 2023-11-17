package shared;

import client.ChunkedCompressedChecksumFileWriter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import server.ChunkedCompressedChecksumFileReader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class FileUtil {

    // do not change it breaks stuff
    protected static final int READER_SIZE = 8192;
    public static final long SEED = 691;

    private static final LZ4Factory LZ_FACTORY = LZ4Factory.fastestInstance();
    public static final LZ4Compressor COMPRESSOR = LZ_FACTORY.highCompressor();
    public static final LZ4FastDecompressor DECOMPRESSOR = LZ_FACTORY.fastDecompressor();

    public static final XXHashFactory XX_HASH_FACTORY = XXHashFactory.fastestInstance();
    public static final XXHash64 HASH_64 = XX_HASH_FACTORY.hash64();

    public enum COMMAND {
        CLOSE((byte) 1),
        WRITE((byte) 2);
        public final byte type;

        COMMAND(byte type) {
            this.type = type;
        }
    }

    public static void write(String path, DataOutputStream dataOut) {
        validatePath(path);
        String relative_path = path.replace(System.getProperty("user.dir"), "");
        try {
            DataInputStream fileReader = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(path))));

            dataOut.writeByte(COMMAND.WRITE.type);
            dataOut.writeUTF(relative_path);

            ChunkedCompressedChecksumFileWriter writer = new ChunkedCompressedChecksumFileWriter(dataOut, fileReader, FileUtil.READER_SIZE, FileUtil.SEED);

            while (fileReader.available() > 0)
                writer.processChunk();

            writer.close();
            fileReader.close();
        } catch (Exception e) {
            ExceptionLogger.log(e);
        }
    }

    public static void receive(DataInputStream dataIn, Tracer trace, Span sp) {
        try {
            String path = createPath(dataIn.readUTF());
            sp.addEvent("Sending file " + path);
            System.out.println("Writing to file: " + path);
            sp.setAttribute("File", path);
            sp.addEvent("File Received");

            ChunkedCompressedChecksumFileReader reader = new ChunkedCompressedChecksumFileReader(dataIn, path, FileUtil.SEED);

            // ugh I want while(reader.readChunk().getUncompressed()); but it makes warnings!!!
            while(true) {
                if (reader.readChunk(trace, sp).getUncompressed() == 0) {
                    sp.addEvent("Chunk Read");
                    break;
                }
            }
            reader.close();
            System.out.println("Writing " + path + " complete");
            sp.addEvent("File Written");
        } catch (Exception e) {
            ExceptionLogger.log(e);
        }
    }

    public static class InvalidUsageException extends RuntimeException {
        public InvalidUsageException(String str) {
            super(str);
        }
    }

    private static void validatePath(String path) {
        File pf = new File(path);
        if (!pf.exists())
            throw new InvalidUsageException("Unable to send a file which doesn't exist!");
        if (pf.isDirectory())
            throw new InvalidUsageException("Path is a directory unable to send! Did you mean sendDir()?");
    }

    private static String createPath(String userFile) {
        String[] pathParts = userFile.split("/");
        String userDirectory = userFile.replace(pathParts[pathParts.length - 1], "");

        File ld = new File(System.getProperty("user.dir") + "/write/" + userDirectory);
        if (!ld.exists() && !ld.mkdirs())
            throw new RuntimeException("Failed to create directory");

        return System.getProperty("user.dir") + "/write/" + userFile;
    }

}

