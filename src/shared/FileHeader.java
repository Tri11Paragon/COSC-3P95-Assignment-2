package shared;

import java.io.*;

public class FileHeader {

    private static final int READER_SIZE = 8192;

    public enum COMMAND {
        WRITE((byte) 1);
        private final byte type;

        private COMMAND(byte type) {
            this.type = type;
        }
    }

    private final String relative_path;
    private final String full_path;

    public FileHeader(String path) {
        String workingDirectory = System.getProperty("user.dir");
        this.full_path = path;
        this.relative_path = path.replace(workingDirectory, "");
        System.out.println(relative_path);
        this.size = 0;
    }

    void write(DataOutputStream writer) {
        try {
            DataInputStream reader = new DataInputStream(new BufferedInputStream(new FileInputStream(full_path)));

            writer.write(COMMAND.WRITE.type);
            writer.writeUTF(relative_path);

            while (reader.available() > 0) {
                int read = Integer.min(reader.available(), READER_SIZE);
                byte[] bytes = new byte[read];

                int amount = reader.read(bytes);
                writer.writeInt(amount);
                writer.write(bytes, 0, amount);
            }
        } catch (Exception ignored) {
        }
    }

    void receive(DataInputStream reader) {
        try {
            String relative = reader.readUTF();


            DataOutputStream writer = new DataOutputStream(new BufferedOutputStream(new FileOutputStream()));
        } catch (Exception ignored){
        }
    }

}
