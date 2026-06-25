import com.github.pagviewer.nativebridge.JnaPagNativeLibrary;
import com.github.pagviewer.nativebridge.PagNativeLibraryResolver;
import com.github.pagviewer.nativebridge.PagPreviewSession;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PagArtifactSmoke {
    private PagArtifactSmoke() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: PagArtifactSmoke <sample.pag>");
        }

        Path sample = Path.of(args[0]);
        Path nativePath = new PagNativeLibraryResolver().resolve().orElseThrow();
        try (PagPreviewSession session = PagPreviewSession.open(
                JnaPagNativeLibrary.load(nativePath),
                Files.readAllBytes(sample),
                sample,
                60.0f,
                1.0f
        )) {
            var image = session.readFrame(0);
            var info = session.getInfo();
            if (info.getWidth() <= 0 || info.getHeight() <= 0 || info.getFrameCount() <= 0) {
                throw new IllegalStateException("Decoded PAG metadata is empty: " + info);
            }
            if (image.getWidth() != info.getWidth() || image.getHeight() != info.getHeight()) {
                throw new IllegalStateException("Decoded frame size does not match metadata.");
            }
            System.out.println("Decoded " + sample + " via " + nativePath);
            System.out.println("decoder=" + info.getWidth() + "x" + info.getHeight()
                    + ", composition=" + info.getCompositionWidth() + "x" + info.getCompositionHeight()
                    + ", frames=" + info.getFrameCount()
                    + ", fps=" + info.getFrameRate());
        }
    }
}
