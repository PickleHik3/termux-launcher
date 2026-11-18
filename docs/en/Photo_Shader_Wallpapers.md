# Photo shader wallpapers

A shader wallpaper can move your own photo: leaves sway, clouds drift, the picture leans a little as
you slide between pages, and a touch pushes it and lets it settle. An AI tool you already use writes
the shader; the launcher runs it. This page covers the three steps, what a depth image is and how to
make or bring one.

## Make one from a photo

Open **Wallpaper**, choose **Shader** and tap **From a photo**. The card shows the steps:

1. **Copy the instructions.** Tap **Copy**. They tell an AI tool what the launcher's shader can read
   and how much it may cost per frame, so the shader it writes fits.
2. **Paste them into an AI tool with your photo.** Any tool that takes a picture and writes code
   will do. Ask for the shader, then copy its answer.
3. **Bring the result back.** Tap **Paste shader**. The editor opens on a new shader holding the
   text, and checks it as it checks any pasted shader. A compile error shows under the code; paste
   it back to the AI tool and it will return a fixed shader.

Then choose the same photo in the editor with **Choose image** on the **Photo** row, and tap
**Apply**. From there it is the usual flow: the wallpaper previews, **Done** keeps it and Back goes
back to what you had.

The wallpaper works without a depth image. A depth image adds to it.

## What a depth image is

A depth image is a grey picture of the same photo, with the same framing: white where something is
near, black where it is far. A shader uses it to move near things more than far ones, so the photo
seems to have depth.

It has to line up with the photo. If it is cropped or framed differently, parts of the picture move
in the wrong places.

## Make depth

On the **Depth** row, tap **Make**. The launcher makes a depth image from your photo on the phone.
The first time it downloads a small model once, and tells you the size before it starts; nothing is
downloaded without your yes. After that, **Make** starts at once. The photo never leaves the phone.

You can cancel a download or a run. If it fails, **Try again** starts it over, or choose a file
yourself. The model can be deleted from the Model Centre's Installed list (**Wallpaper depth**); the
next **Make** downloads it again.

Changing or removing the photo removes a depth image made from the old photo.

## Bring your own depth image

Tap **Choose image** on the **Depth** row and pick any greyscale image of the same photo with the
same framing, white near. If you change the photo afterwards the row says **May not match the new
photo**: tap **Make** for a new one, or **Remove** it.

## Without depth

**Remove** takes the depth image away. The shader then works from the photo alone, and the wallpaper
looks right either way.
