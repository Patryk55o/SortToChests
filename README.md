# SortToChests

SortToChests is a lightweight, fully client-side Fabric mod designed to sort items, _really_ quickly. With a single command, it scans nearby to look for chests, so it can remove useless items from your inventory into nearby chests.

## Requirements
- Fabric API

## Features
- Use `X` to filter which items you want to sort to chests, and which ones to NOT sort to chests, Blacklisted items are **highlighted** with the color red, which is saved in `config/chestsort-blacklist.json`, so it can persist with every session. Rebindable with the Keybind Settings
- Use `/cchestsort` to use the mod's function. If there aren't any chests nearby that you can sort to the mod will exit early, this also counts if you do not have any sortable items
- Safe Delays are used to not get kicked/banned from the server you are playing on, this option **cannot be turned off**.

## How to Build?

- On Windows go to the directory and run this in cmd
```
./gradlew.bat build
```
- On Linux/Mac OS
```
./gradlew build
```
