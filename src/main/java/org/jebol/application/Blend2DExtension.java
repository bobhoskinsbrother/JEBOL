package org.jebol.application;

import org.jebol.domain.eval.blend2d.Blend2DCanvas;
import org.jebol.domain.eval.blend2d.Blend2DNotPortedYet;
import org.jebol.domain.eval.blend2d.Blend2DSurface;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.ImageStorage;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Locale;

final class Blend2DExtension {

    private static final String HEADER = "REBOL [Title: {Rebol Blend2D Extension} Name: blend2d Type: module "
            + "Exports: [draw] Version: 0.11.5.0 Author: Oldes Date: 27-Nov-2024/16:19:42 License: Apache-2.0 "
            + "Url: https://github.com/Siskin-framework/Rebol-Blend2D]";

    private static final String SCRIPT = "init-words words: [" + String.join(" ", Blend2DDrawing.COMMAND_WORDS)
            + "] [" + String.join(" ", Blend2DDrawing.ARGUMENT_WORDS) + "]\nprotect/hide 'init-words";

    private static final String IMAGE_HANDLE = "BLImage";
    private static final String PATH_HANDLE = "BLPath";
    private static final String FONT_HANDLE = "BLFontFace";

    private static final String NO_IMAGE_HANDLE = "Blend2D failed to make an image handle!";
    private static final String NO_IMAGE = "Blend2D's blImageCreateFromData failed!";

    private static final int FORMAT_PREMULTIPLIED = 1;
    private static final int BYTES_PIXEL = 4;
    private static final int LONGEST_SIDE = 0xFFFF;

    record ImageHandle(ImageValue image) {
    }

    record PathHandle() {
    }

    record FontHandle() {
    }

    private Blend2DExtension() {
    }

    static BundledExtension create() {
        return new BundledExtension(new Extension("blend2d", "0.12.0", List.of(
                new ExtensionCommand("init-words", "cmd-words [block!] arg-words [block!]",
                        call -> LogicValue.of(true)),
                new ExtensionCommand("draw", "{Draws scalable vector graphics to an image} "
                        + "image [image! pair!] commands [block!]", Blend2DExtension::draw),
                new ExtensionCommand("path", "{Prepares path object} commands [block!]",
                        call -> {
                            throw new IllegalStateException(new Blend2DNotPortedYet("a path").getMessage());
                        }),
                new ExtensionCommand("font", "{Prepares font handle} file [file! string!] "
                        + "{Font location or name}",
                        call -> {
                            throw new IllegalStateException(new Blend2DNotPortedYet("with a font").getMessage());
                        }),
                new ExtensionCommand("image", "{Prepares Blend2D's native image} from [pair! image! file!]",
                        Blend2DExtension::makeImage),
                new ExtensionCommand("info", "{Returns info about Blend2D library} "
                        + "/of handle [handle!] {Blend2D object}", Blend2DExtension::info),
                new ExtensionCommand("set-threads", "{Sets number of threads to be used} count [integer!] "
                        + "{0 means synchronous rendering; 1 is for async on main thread only else thread pool is used}",
                        call -> call.arguments().getFirst())))
                .withHandleType(new HandleType(IMAGE_HANDLE, ImageHandle.class))
                .withHandleType(new HandleType(PATH_HANDLE, PathHandle.class))
                .withHandleType(new HandleType(FONT_HANDLE, FontHandle.class))
                .withScript(SCRIPT), HEADER);
    }

    static boolean isImageHandle(Value value) {
        return value instanceof HandleValue handle && handle.typeName().equals(IMAGE_HANDLE);
    }

    static boolean isPathHandle(Value value) {
        return value instanceof HandleValue handle && handle.typeName().equals(PATH_HANDLE);
    }

    private static Value draw(Command call) {
        ImageValue image = call.arguments().getFirst() instanceof PairValue size
                ? makeWhiteImage(size)
                : ((ImageValue) call.arguments().getFirst()).head();
        ImageStorage storage = image.storage();
        int wide = storage.wide();
        int high = storage.high();
        if (wide == 0 || high == 0) {
            throw new IllegalStateException(NO_IMAGE);
        }
        Blend2DSurface surface = new Blend2DSurface(wide, high, readPixels(storage, wide * high));
        try {
            Blend2DDrawing.draw(call, new Blend2DCanvas(surface), (AnyBlockValue) call.arguments().get(1));
        } catch (Blend2DNotPortedYet notYet) {
            writePixels(surface, storage);
            throw new IllegalStateException(notYet.getMessage());
        }
        writePixels(surface, storage);
        return image;
    }

    private static ImageValue makeWhiteImage(PairValue size) {
        int wide = (int) Math.floor((float) size.x() + 0.5);
        int high = (int) Math.floor((float) size.y() + 0.5);
        if (Integer.compareUnsigned(wide, LONGEST_SIDE) > 0 || Integer.compareUnsigned(high, LONGEST_SIDE) > 0) {
            throw Raised.of(EvaluationFailure.SIZE_LIMIT, WordValue.of("image!"));
        }
        return ImageValue.of(wide, high);
    }

    private static int[] readPixels(ImageStorage storage, int count) {
        int[] pixels = new int[count];
        for (int pixel = 0; pixel < count; pixel++) {
            int[] rgba = storage.readPixelAt(pixel + 1);
            pixels[pixel] = rgba[3] << 24 | rgba[0] << 16 | rgba[1] << 8 | rgba[2];
        }
        return pixels;
    }

    private static void writePixels(Blend2DSurface surface, ImageStorage storage) {
        int[] pixels = surface.pixels();
        for (int pixel = 0; pixel < pixels.length; pixel++) {
            int argb = pixels[pixel];
            storage.setColourAt(pixel + 1, (argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF);
            storage.setAlphaAt(pixel + 1, argb >>> 24);
        }
    }

    private static Value makeImage(Command call) {
        Value from = call.arguments().getFirst();
        if (from instanceof ImageValue image) {
            if (image.storage().wide() == 0 || image.storage().high() == 0) {
                throw new IllegalStateException(NO_IMAGE_HANDLE);
            }
            return call.makeHandle(IMAGE_HANDLE, new ImageHandle(image.head()));
        }
        if (from instanceof PairValue) {
            throw new IllegalStateException(NO_IMAGE_HANDLE);
        }
        throw new IllegalStateException(new Blend2DNotPortedYet("an image read from a file").getMessage());
    }

    private static Value info(Command call) {
        if (!call.refinements().contains("of")) {
            return StringValue.of(describeLibrary());
        }
        Value handle = call.arguments().getFirst();
        if (isImageHandle(handle)) {
            ImageStorage storage = call.unwrap(handle, ImageHandle.class).image().storage();
            return StringValue.of("width:  " + storage.wide() + "\n"
                    + "heigth: " + storage.high() + "\n"
                    + "stride: " + storage.wide() * BYTES_PIXEL + "\n"
                    + "format: " + FORMAT_PREMULTIPLIED + "\n"
                    + "flags:  0\n");
        }
        return StringValue.of("");
    }

    private static String describeLibrary() {
        int processors = Runtime.getRuntime().availableProcessors();
        return "Version:     0.12.0\n"
                + "Build-type:  Release\n"
                + "Compiled-by: JEBOL, a port to Java\n\n"
                + "System: [\n"
                + "  cpuArch:      " + nameArchitecture() + "\n"
                + "  cpuFeatures:  0\n"
                + "  coreCount:    " + processors + "\n"
                + "  threadCount:  " + processors + "\n"
                + "  threadStackSize:       0\n"
                + "  allocationGranularity: 0\n"
                + "]\n\n"
                + "Resource: [\n"
                + "  vmUsed:       0\n"
                + "  vmReserved:   0\n"
                + "  vmOverhead:   0\n"
                + "  vmBlockCount: 0\n"
                + "  zmUsed:       0\n"
                + "  zmReserved:   0\n"
                + "  zmOverhead:   0\n"
                + "  zmBlockCount: 0\n"
                + "  dynamicPipelineCount: 0\n"
                + "]\n";
    }

    private static String nameArchitecture() {
        String named = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (named.contains("86") || named.contains("amd64")) {
            return "x86";
        }
        return named.startsWith("arm") || named.startsWith("aarch") ? "ARM" : "unknown";
    }
}
