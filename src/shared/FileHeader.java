package shared;

import client.Client;

import java.io.*;

public class FileHeader {

    private static final int READER_SIZE = 8192;

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
            DataInputStream reader = new DataInputStream(new BufferedInputStream(new FileInputStream(full_path)));

            writer.writeByte(COMMAND.WRITE.type);
            writer.writeUTF(relative_path);

            while (reader.available() > 0) {
                int read = Integer.min(reader.available(), READER_SIZE);
                byte[] bytes = new byte[read];

                int amount = reader.read(bytes);
                if (amount <= 0)
                    break;
                System.out.println("Writing " + amount + "  bytes");
                writer.writeInt(amount);
                writer.write(bytes, 0, amount);
            }
            reader.close();
            writer.writeInt(0);
            writer.flush();
        } catch (Exception ignored) {
        }
    }

    public static void receive(DataInputStream reader) {
        try {
            String path = System.getProperty("user.dir") + "/out-" + reader.readUTF();
            System.out.println("Writing to file: " + path);

            DataOutputStream writer = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
            int size = 0;
            while ((size = reader.readInt()) > 0){
                byte[] data = new byte[size];
                int amount = reader.read(data, 0, size);
                writer.write(data, 0, amount);
            }
        } catch (Exception ignored){
        }
    }

}
