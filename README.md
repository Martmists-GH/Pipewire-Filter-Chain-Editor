# Pipewire Filter Chain Editor

This is a tool to easily edit and create pipewire filter chains. 

### Features

- Support for a decent chunk of pipewire filters
- Support for LADSPA filters

### Limitations

- Assumes every input and output is stereo
- Assumes nothing other than the filter chain is in the .conf files
- May drop attributes from existing files if opened and saved again(?)

## Installation

### Arch

```bash
git clone https://github.com/Martmists-GH/Pipewire-Filter-Chain-Editor.git pfce
cd pfce/build_files
makepkg -si
```

### Debian/Ubuntu

1. Grab the most recent .deb file from releases
2. Install it(?)
