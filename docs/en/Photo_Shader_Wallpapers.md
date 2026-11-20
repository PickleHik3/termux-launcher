# Photo shader wallpapers

A shader wallpaper can move your own photo: leaves sway, clouds drift, the picture leans a little as
you slide between pages, and a touch pushes it and lets it settle. An AI tool you already use writes
the shader; the launcher runs it. This page covers the three steps, what a depth image is and how to
generate or bring one.

## Make one from a photo

Open **Wallpaper**, choose **Shader** and tap **From a photo**. One page holds the whole flow, top
to bottom: the name, the **Quality** and **Frame rate** choices, then the steps.

1. **Copy the instructions.** Tap **Copy**. They tell an AI tool what the launcher's shader can read
   and how much it may cost per frame, so the shader it writes fits.
2. **Paste them into an AI tool with your photo.** Any tool that takes a picture and writes code
   will do. Ask for the shader, then copy its answer. Back in the launcher, choose the same photo
   with **Choose image** on the **Photo** row.
3. **Bring the result back.** At the bottom, under **Bring the result back**, tap **Paste**. The
   shader is checked as any pasted shader is. A compile error shows under the code; paste it back to
   the AI tool and it will return a fixed shader.

Then tap **Apply**. From there it is the usual flow: the wallpaper previews, **Done** keeps it and
Back goes back to what you had. **New shader** and the pencil on a shader open the same page without
the copy step.

The wallpaper works without a depth image. A depth image adds to it.

## What a depth image is

A depth image is a grey picture of the same photo, with the same framing: white where something is
near, black where it is far. A shader uses it to move near things more than far ones, so the photo
seems to have depth.

It has to line up with the photo. If it is cropped or framed differently, parts of the picture move
in the wrong places.

## Generate depth

Once a photo is chosen, **Generate depth** appears under the **Photo** row. Tap it and the launcher
generates a depth image from your photo on the phone. The first time it downloads a small model
once, and tells you the size before it starts; nothing is downloaded without your yes. After that,
**Generate depth** starts at once. The photo never leaves the phone.

You can cancel a download or a run. If it fails, **Try again** starts it over, or choose a file
yourself. The model can be deleted from the Model Centre's Installed list (**Wallpaper depth**); the
next **Generate depth** downloads it again.

Changing or removing the photo removes a depth image generated from the old photo.

## Bring your own depth image

Tap **Choose depth image** under the **Photo** row and pick any greyscale image of the same photo
with the same framing, white near. If you change the photo afterwards the depth line says **May not
match the new photo**: tap **Generate** for a new one, or **Remove** it.

## Without depth

**Remove** takes the depth image away. The shader then works from the photo alone, and the wallpaper
looks right either way.
