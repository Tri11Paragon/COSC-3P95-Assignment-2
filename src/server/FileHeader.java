package server;

import shared.ExceptionLogger;

import java.io.DataInputStream;
import java.io.IOException;

public class FileHeader {

    private int uncompressed;
    private int compressed;
    private long hash;

    public FileHeader() {}

    public FileHeader read(DataInputStream reader) throws IOException{
        uncompressed = reader.readInt();
        if (uncompressed == 0)
            return this;
        compressed = reader.readInt();
        hash = reader.readLong();
        return this;
    }

    public int getUncompressed() {
        return uncompressed;
    }

    public int getCompressed() {
        return compressed;
    }

    public long getHash() {
        return hash;
    }
}
