# Pipewire Filter Chain Editor

This is a tool to easily edit and create pipewire filter chains. 

### Features

- Support for a decent chunk of pipewire filters
- Support for LADSPA filters

### Limitations

- Assumes every input and output is stereo
- Assumes nothing other than the filter chain is in the .conf files
- May drop attributes from existing files if opened and saved again(?)
