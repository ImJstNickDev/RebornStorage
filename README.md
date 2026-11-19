# Reborn Storage for Forge 1.16.5

Build one multiblock crafter for your Refined Storage network. Put more CPUs inside when you need faster crafting, or give that space to pattern storage as your recipe collection grows. This unofficial port brings the crafter, wireless equipment and disks from Reborn Storage 1.18.2 to Minecraft 1.16.5, with four more item disk sizes above 16384k. [Original Reborn Storage project](https://github.com/TechReborn/RebornStorage)

## Installation

Download the Reborn Storage JAR and put it in the `mods` folder on the server and each client, alongside **Refined Storage 1.9.17**. The release was tested on **Forge 36.2.26**. Refined Storage is the only required mod. [Download Reborn Storage](https://github.com/ImJstNickDev/RebornStorage/releases/latest)

## Building a multiblock crafter

A **3 × 3 × 4** crafter takes **24 Crafting Frames, 10 Heat Conductors, one Crafting CPU and one Crafting Storage**. Place frames along every edge and at the corners. Fill the rest of the six faces with heat conductors, including the top and bottom. The CPU and storage block go in the two spaces inside.

Connect an RS cable from a powered network to the outside of the crafter. Right-click the structure to insert crafting patterns. Each Crafting Storage block gives you a page of 78 slots. Shift-click a page arrow to skip up to ten pages. For processing patterns, such as smelting in a furnace, use an RS crafter facing the machine.

The same layout works for larger rectangular builds. Fill every interior space with a CPU or a storage block. Each side can be 3 to 16 blocks long by default. You can raise the limit to 64 per side in `config/rebornstorage-common.toml`. For very large builds, check the RS controller's energy capacity as well as the power supply. At the default costs, each CPU draws 5 FE/t and each storage block draws 10 FE/t.

## Disks and components

| Storage | Starting component from Refined Storage | Reborn disk sizes, in crafting order |
| --- | --- | --- |
| Items | 64k Storage Part | 256k, 1024k, 4096k, 16384k, 32768k, 65536k, 131072k, 262144k |
| Fluids | 4096k Fluid Storage Part | 16384k, 65536k, 262144k, 1048576k |

Each new component takes three of the previous component, four Advanced Processors and one Quartz Enriched Iron. The center ingredient is redstone for items and an empty bucket for fluids. For example, the 32768k item component takes three **Reborn 16384k components**. The largest item disk holds 262,144,000 items. The largest fluid disk holds 1,048,576 buckets.

Combine a component with an RS Storage Housing to make a disk. **If ExtraStorage is installed**, use the shaped recipe for the four original item tiers and all four fluid tiers to get the Reborn disk. Put three Quartz Enriched Iron across the top, redstone on either side of the component in the middle, and glass, redstone, glass along the bottom. The four larger item tiers always use the housing recipe.

To take a disk apart, empty it and shift-right-click it in your hand. You get back the component and housing. With Patchouli installed, a book and a piece of Refined Storage silicon craft the in-game guide.

## Wireless access

The **Super Wireless Grid** combines an item crafting grid, a fluid grid and a crafting monitor. Bind it by right-clicking your controller with it. **Ctrl+G** opens the grid and **Ctrl+H** cycles the modes. You can also switch modes by shift-right-clicking with the grid in hand. The rechargeable version holds 100,000 FE. A creative version is available, and Curios adds a dedicated grid slot.

The **Advanced Wireless Transmitter** reaches 1,000 blocks by default. Each of its four Range Upgrade slots adds 500 blocks, for 3,000 with all slots filled. It draws 100 FE/t plus the cost of installed upgrades. If you use RSInfinityBooster, its Infinity and Dimension cards work in both the advanced transmitter and the ordinary RS transmitter. Smelt a Raw Super Advanced Processor to make the processor used in the advanced transmitter and the Super Wireless Grid.

## Crafting performance

The default setting uses Refined Storage's crafting engine. To enable the optimized engine for the multiblock, set `experimental_crafting = "TASK"` under `[general]` in `config/rebornstorage-common.toml` and restart. It works with RS, Reborn and ExtraStorage disks. You can follow an active order in the Super Wireless Grid's crafting monitor as its count falls. [Crafting engine details](docs/EXPERIMENT-CRAFTING-ENGINE.md)

Pack choice matters at the largest sizes. In the 64³ benchmark of the engine shipped here, the same nested hopper-minecart order ran at 19.99 TPS on the reduced Forge installation and 5.00 TPS on the Cuboid Outpost LE server pack. Both used 238,327 CPUs, one pattern storage block and a controller buffer of 2,000,000 FE. [Benchmarks from 4³ to 64³](docs/benchmarks/2026-11-18-release4/README.md)

## Building and testing

Use JDK 17 for Gradle and JDK 8 for development game runs. Run `./gradlew build` to produce the mod JAR in `build/libs/`.

The integration suite starts a dedicated server twice to check that disks, patterns and active orders survive a restart. The client test opens the wireless screens and crafter menu in a copy of that world. The following commands use a disposable test world and accept the Minecraft EULA for that server.

```sh
python3 scripts/test_port.py --accept-eula --tag local
python3 scripts/test_port.py --client --tag local
```

Add `--with-extra-storage` and use a different tag to test with ExtraStorage installed. The 0.1.4 release was also tested with a full Cuboid Outpost LE client and dedicated server. A nested order for 32,000 sticks resumed after the crafter's chunk unloaded and delivered the exact count. [Release verification](docs/PORT-1.16.5.md#verification-of-014-on-forge-36226)
