package shared;

public class ArrayData {

    private final byte[] data;
    private final int actualLength;

    public ArrayData(byte[] data, int actualLength){
        this.data = data;
        this.actualLength = actualLength;
    }

    public byte[] getData() {
        return data;
    }

    public int getActualLength() {
        return actualLength;
    }
}
