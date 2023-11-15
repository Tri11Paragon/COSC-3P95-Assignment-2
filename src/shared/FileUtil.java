package shared;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;

public class FileUtil {

    protected static final int READER_SIZE = 8192;
    public static final long SEED = 691;

    private static final LZ4Factory LZ_FACTORY = LZ4Factory.fastestInstance();
    public static final LZ4Compressor COMPRESSOR = LZ_FACTORY.highCompressor();
    public static final LZ4FastDecompressor DECOMPRESSOR = LZ_FACTORY.fastDecompressor();

    public static final XXHashFactory XX_HASH_FACTORY = XXHashFactory.fastestInstance();
    public static final XXHash64 HASH_64 = XX_HASH_FACTORY.hash64();

}

