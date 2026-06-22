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
            if (session.info().width() <= 0 || session.info().height() <= 0 || session.info().frameCount() <= 0) {
                throw new IllegalStateException("Decoded PAG metadata is empty: " + session.info());
            }
            if (image.getWidth() != session.info().width() || image.getHeight() != session.info().height()) {
                throw new IllegalStateException("Decoded frame size does not match metadata.");
            }
            System.out.println("Decoded " + sample + " via " + nativePath);
            System.out.println("decoder=" + session.info().width() + "x" + session.info().height()
                    + ", composition=" + session.info().compositionWidth() + "x" + session.info().compositionHeight()
                    + ", frames=" + session.info().frameCount()
                    + ", fps=" + session.info().frameRate());
        }
    }
}
