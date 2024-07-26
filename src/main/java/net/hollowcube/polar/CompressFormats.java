package net.hollowcube.polar;

import net.jpountz.lz4.LZ4Factory;

public final class CompressFormats {

    private CompressFormats() {}

    public static final LZ4Factory LZ_4_FACTORY = LZ4Factory.fastestInstance();
}
