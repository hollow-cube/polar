# Polar v1.0

The polar format resembles the anvil format in many ways, though it is binary, not NBT.

### Header

| Name           | Type   | Notes                                                                   |
|----------------|--------|-------------------------------------------------------------------------|
| Magic Number   | int    | `Polr`                                                                  |
| Version        | short  |                                                                         |
| Compression    | byte   | 0 = None, 1 = Zstd                                                      |
| Length of data | varint | Uncompressed length of data (or just length of data if `Compression=0`) |
| World          | world  |                                                                         |

### World

| Name             | Type         | Notes                                    |
|------------------|--------------|------------------------------------------|
| Min Section      | byte         | For example, -4 in a vanilla world       |
| Max Section      | byte         | For example, 19 in a vanilla world       |
| Number of Chunks | varint       | Number of entries in the following array |
| Chunks           | array[chunk] | Chunk data                               |

### Chunk

Entities or some other extra data field needs to be added to chunks in the future.

| Name                     | Type                | Notes                                                                                |
|--------------------------|---------------------|--------------------------------------------------------------------------------------|
| Chunk X                  | varint              |                                                                                      |
| Chunk Z                  | varint              |                                                                                      |
| Sections                 | array[section]      | `maxSection-minSection+1` entries                                                    |
| Number of Block Entities | varint              | Number of entries in the following array                                             |
| Block Entities           | array[block entity] |                                                                                      |
| Heightmap Mask           | int                 | A mask indicating which heightmaps are present. See `AnvilChunk` for flag constants. |
| Heightmaps               | array[bytes]        | One heightmap for each bit present in Heightmap Mask                                 |
| Length of user data      | varint              | Number of entries in the following array                                             |
| User data                | array[byte]         |                                                                                      |

### Sections

| Name                      | Type          | Notes                                                             |
|---------------------------|---------------|-------------------------------------------------------------------|
| Is Empty                  | bool          | If set, nothing follows                                           |
| Block Palette Size        | varint        |                                                                   |
| Block Palette             | array[string] | Entries are in the form `minecraft:block[key1=value1,key2=value2] |
| Block Palette Data Length | varint        | Only present if `Block Palette Size > 1`                          |
| Block Palette Data        | array[long]   | See the anvil format for more information about this type         |
| Biome Palette Size        | varint        |                                                                   |
| Biome Palette             | array[string] |                                                                   |
| Biome Palette Data Length | varint        | Only present if `Biome Palette Size > 1`                          |
| Biome Palette Data        | array[long]   | See the anvil format for more information about this type         |
| Has Block Light Data      | bool          | If unset, block light is ommitted                                 |
| Block Light               | bytes         | A 2048 byte long nibble array                                     |
| Has Sky Light Data        | bool          | If unset, sky light is ommitted                                   |
| Sky Light                 | bytes         | A 2048 byte long nibble array                                     |

### Block Entity

| Name            | Type   | Notes                                |
|-----------------|--------|--------------------------------------|
| Chunk Pos       | int    |                                      |
| Has ID          | bool   | If unset, Block Entity ID is omitted |
| Block Entity ID | string |                                      |
| Has NBT Data    | bool   | If unset, NBT Data is omitted        |
| NBT Data        | nbt    |                                      |
