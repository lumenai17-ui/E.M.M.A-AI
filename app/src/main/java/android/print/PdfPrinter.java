package android.print;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;

/**
 * PdfPrinter — Helper placed in android.print package to access
 * the package-private constructors of LayoutResultCallback and WriteResultCallback.
 *
 * This is the standard workaround used by Android apps that need
 * programmatic PDF generation from a PrintDocumentAdapter (e.g. WebView).
 */
public class PdfPrinter {
    private static final String TAG = "PdfPrinter";
    private final PrintAttributes printAttributes;

    public interface Callback {
        void onComplete(boolean success);
    }

    public PdfPrinter(PrintAttributes printAttributes) {
        this.printAttributes = printAttributes;
    }

    public void print(final PrintDocumentAdapter printAdapter,
                      final File outputFile,
                      final Callback callback) {

        printAdapter.onLayout(null, printAttributes, null,
                new PrintDocumentAdapter.LayoutResultCallback() {
                    @Override
                    public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                        Log.d(TAG, "Layout finished: " + (info != null ? info.getPageCount() : "?") + " pages");
                        writePdf(printAdapter, outputFile, callback);
                    }

                    @Override
                    public void onLayoutFailed(CharSequence error) {
                        Log.e(TAG, "Layout failed: " + error);
                        callback.onComplete(false);
                    }

                    @Override
                    public void onLayoutCancelled() {
                        Log.w(TAG, "Layout cancelled");
                        callback.onComplete(false);
                    }
                }, new Bundle());
    }

    private void writePdf(PrintDocumentAdapter printAdapter,
                          File outputFile,
                          final Callback callback) {
        try {
            final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(outputFile,
                    ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_WRITE_ONLY);

            printAdapter.onWrite(new PageRange[]{PageRange.ALL_PAGES},
                    pfd,
                    new CancellationSignal(),
                    new PrintDocumentAdapter.WriteResultCallback() {
                        @Override
                        public void onWriteFinished(PageRange[] pages) {
                            Log.i(TAG, "Write finished: " + outputFile.length() / 1024 + "KB");
                            closeSilently(pfd);
                            callback.onComplete(true);
                        }

                        @Override
                        public void onWriteFailed(CharSequence error) {
                            Log.e(TAG, "Write failed: " + error);
                            closeSilently(pfd);
                            callback.onComplete(false);
                        }

                        @Override
                        public void onWriteCancelled() {
                            Log.w(TAG, "Write cancelled");
                            closeSilently(pfd);
                            callback.onComplete(false);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error opening PDF output: " + e.getMessage(), e);
            callback.onComplete(false);
        }
    }

    private void closeSilently(ParcelFileDescriptor pfd) {
        try {
            if (pfd != null) pfd.close();
        } catch (Exception ignored) {}
    }
}
