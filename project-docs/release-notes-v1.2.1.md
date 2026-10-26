# v1.2.1

Linux apps stop freezing phones with a known-bad graphics chip, graphics setup runs without questions, and Arch Linux installs on arm64 phones again.

## Fixes

### Linux apps

- Phones whose graphics chip has frozen under hardware drawing (Mali-G52 and its kin) start every Linux app in software drawing, whatever is installed, and the Graphics page says why (#162).
- Settings › Linux apps › Graphics acceleration tests each choice on a private display that never shows, installs the Vulkan wrapper from a pinned copy that pacman accepts, and never asks a question mid-run.
- The Adreno and OpenGL choices draw through the driver they name, no longer through Zink by mistake.
- Phones whose build has no X server are never offered the display and never try to start it.
- Setting up Linux apps with Arch Linux works on arm64 phones again; Arch comes from the Arch Linux ARM image.

### Keyboard

- Ctrl+Shift+V on a hardware keyboard pastes the clipboard (#148).

## Editions

Nothing exclusive to the Nix or VAJ build in this release.
