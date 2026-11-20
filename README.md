# PrimeItemValue

Client-side Fabric mod for PrimeBlocks, built for Minecraft 26.3 and Java 25.

## Features

- Searchable item panel beside the inventory, with separate custom and vanilla tabs.
- Search by German or English item name, registry ID, model number or collection.
- Unit prices and optional stack totals in item tooltips.
- Item details with market history for 7, 30 or 90 days.
- Automatic CityBuild detection and a manual override for CB1, CB2 and CB3.
- Movable, resizable panel with adjustable scale and background.
- German and English interface text, selectable independently of the game language.

Prices come from the PrimeBlocks market API and are cached locally. Example values
shown before market data is available are marked as placeholders. A market price
is an estimate based on recorded trades, not a guaranteed selling price.

The mod is active only on matching server addresses. The default filter is
`primeblocks`.

## Requirements

- Minecraft 26.3
- Java 25
- Fabric Loader 0.19.5 or later
- Fabric API for Minecraft 26.3

## Build

Set `JAVA_HOME` to a Java 25 installation, then run:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```sh
./gradlew build
```

The installable mod is `build/libs/primeitemvalue-26.3.jar`.
The file ending in `-sources.jar` contains source code and is not the installable mod.

## Configuration

Settings are saved in `config/primeitemvalue.json`. Panel controls cover the usual
options. Advanced settings include:

| Setting | Purpose |
| --- | --- |
| `serverAddressFilter` | Server address substring that enables the mod; empty enables it everywhere. |
| `database` | CityBuild override: `cb1`, `cb2`, `cb3`, or `null` for automatic detection. |
| `language` | `de`, `en`, or `auto` to follow the game language. |
| `customDataKey` | Custom-data field used to identify server items; defaults to `pb_id`. |
| `marketPeriodDays` | Period used for catalog prices; defaults to 30 days. |
| `debugTiming` | Log panel rendering times. |
| `debugSearch` | Log search input and result counts. |

The panel toggle has no default key binding to avoid conflicts with other mods.
Assign one in Minecraft's controls if needed.

## Source layout

| Package | Purpose |
| --- | --- |
| `catalog` | Item identities, names, collections and search. |
| `market` | API requests, cached pages, CityBuild detection and background loading. |
| `ui` | Inventory panel, drawing and input handling. |
| `tooltip` | Item price tooltips. |
| `value` | Price models, history, formatting and provider interface. |
| `mixin` | Keyboard routing and access to inventory and tab-list geometry. |

## Checks

Build first, then check translation keys and the packaged language files:

```sh
python tools/check-lang.py
```

`tools/CoreCheck.java` checks search, price formatting, CityBuild detection,
example histories, cache serialization and resize calculations. Run it with Java 25
and a classpath containing `build/classes/java/main`, the Minecraft jar from the
Loom cache, and Gson:

```powershell
java -cp "build/classes/java/main;<minecraft.jar>;<gson.jar>" tools/CoreCheck.java
```

Check mixin targets against the same Minecraft jar:

```sh
python tools/check-mixins.py "<minecraft.jar>" "<path-to-javap>" src/main/java
```

These checks do not replace testing input, rendering and server transfers in the game.

## License

MIT. See [LICENSE](LICENSE).
