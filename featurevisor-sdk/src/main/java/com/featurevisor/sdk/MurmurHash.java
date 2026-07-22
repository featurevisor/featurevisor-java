package com.featurevisor.sdk;

import java.nio.charset.StandardCharsets;

/**
 * MurmurHash v3 implementation for Featurevisor SDK
 * Based on the TypeScript implementation from: https://github.com/perezd/node-murmurhash
 */
public class MurmurHash {

    /**
     * MurmurHash v3 implementation
     * @param key The key to hash
     * @param seed The seed value
     * @return The hash value as an int representing unsigned 32-bit integer
     */
    public static int murmurHashV3(String key, int seed) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        return murmurHashV3(keyBytes, seed);
    }

    /**
     * MurmurHash v3 implementation for byte arrays
     * @param key The key bytes to hash
     * @param seed The seed value
     * @return The hash value as an int representing unsigned 32-bit integer
     */
    public static int murmurHashV3(byte[] key, int seed) {
        int remainder, bytes, h1, h1b, c1, c2, k1, i;

        remainder = key.length & 3; // key.length % 4
        bytes = key.length - remainder;
        h1 = seed;
        c1 = 0xcc9e2d51;
        c2 = 0x1b873593;
        i = 0;

        while (i < bytes) {
            k1 = (key[i] & 0xff) |
                 ((key[++i] & 0xff) << 8) |
                 ((key[++i] & 0xff) << 16) |
                 ((key[++i] & 0xff) << 24);
            ++i;

            k1 = ((k1 & 0xffff) * c1 + ((((k1 >>> 16) * c1) & 0xffff) << 16)) & 0xffffffff;
            k1 = (k1 << 15) | (k1 >>> 17);
            k1 = ((k1 & 0xffff) * c2 + ((((k1 >>> 16) * c2) & 0xffff) << 16)) & 0xffffffff;

            h1 ^= k1;
            h1 = (h1 << 13) | (h1 >>> 19);
            h1b = ((h1 & 0xffff) * 5 + ((((h1 >>> 16) * 5) & 0xffff) << 16)) & 0xffffffff;
            h1 = (h1b & 0xffff) + 0x6b64 + ((((h1b >>> 16) + 0xe654) & 0xffff) << 16);
        }

        k1 = 0;

        switch (remainder) {
            case 3:
                k1 ^= (key[i + 2] & 0xff) << 16;
            case 2:
                k1 ^= (key[i + 1] & 0xff) << 8;
            case 1:
                k1 ^= key[i] & 0xff;

                k1 = ((k1 & 0xffff) * c1 + ((((k1 >>> 16) * c1) & 0xffff) << 16)) & 0xffffffff;
                k1 = (k1 << 15) | (k1 >>> 17);
                k1 = ((k1 & 0xffff) * c2 + ((((k1 >>> 16) * c2) & 0xffff) << 16)) & 0xffffffff;
                h1 ^= k1;
        }

        h1 ^= key.length;

        h1 ^= h1 >>> 16;
        h1 = ((h1 & 0xffff) * 0x85ebca6b + ((((h1 >>> 16) * 0x85ebca6b) & 0xffff) << 16)) & 0xffffffff;
        h1 ^= h1 >>> 13;
        h1 = ((h1 & 0xffff) * 0xc2b2ae35 + ((((h1 >>> 16) * 0xc2b2ae35) & 0xffff) << 16)) & 0xffffffff;
        h1 ^= h1 >>> 16;

        // Convert to unsigned 32-bit integer
        return (int) (h1 & 0xffffffffL);
    }
}
