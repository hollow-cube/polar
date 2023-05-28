# Polar v1.0
The polar format resembles the anvil format in many ways, though it is binary, not NBT.

### Header
```
int - magic number
byte - major version
byte - minor version
byte - compression type (0 = none, 1 = zstd)
varint - length of the rest of the data

todo include world data here
- min, max coordinate
- min section y
- what else

varint - number of chunks
-- the rest of the data is a series of chunks
```

### Chunk
```
varint - chunk x
varint - chunk z

-- sections

int - heightmap bitmask
32 bytes - for each heightmap present, in order

todo entities
```

### Sections
```
bool - is empty (if set, nothing follows)

-- blocks
varint - length of palette
string - for each palette entry
varint - length of data
byte[] - data

-- biomes
varint - length of palette
string - for each palette entry
varint - length of data
byte[] - data

bool - has light data (if unset, nothing follows)
2048 bytes - block light
2048 bytes - sky light
```
