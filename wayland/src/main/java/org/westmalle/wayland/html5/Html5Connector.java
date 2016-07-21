package org.westmalle.wayland.html5;


import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.freedesktop.jaccall.JNI;
import org.freedesktop.jaccall.Lng;
import org.freedesktop.jaccall.Pointer;
import org.freedesktop.jaccall.Ptr;
import org.freedesktop.jaccall.Unsigned;
import org.freedesktop.wayland.server.Display;
import org.westmalle.wayland.core.Connector;
import org.westmalle.wayland.core.Renderer;
import org.westmalle.wayland.nativ.libpng.Libpng;
import org.westmalle.wayland.nativ.libpng.png_rw_ptr;
import org.westmalle.wayland.protocol.WlOutput;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.freedesktop.jaccall.Pointer.malloc;
import static org.freedesktop.jaccall.Size.sizeof;
import static org.westmalle.wayland.nativ.libpng.Libpng.PNG_COLOR_TYPE_RGBA;
import static org.westmalle.wayland.nativ.libpng.Libpng.PNG_COMPRESSION_TYPE_DEFAULT;
import static org.westmalle.wayland.nativ.libpng.Libpng.PNG_FILTER_TYPE_DEFAULT;
import static org.westmalle.wayland.nativ.libpng.Libpng.PNG_INTERLACE_NONE;
import static org.westmalle.wayland.nativ.libpng.Libpng.PNG_TRANSFORM_IDENTITY;
import static org.westmalle.wayland.nativ.libpng.Pointerpng_rw_ptr.nref;

@AutoFactory(allowSubclasses = true,
             className = "Html5ConnectorFactory")
public class Html5Connector implements Connector {

    @Nonnull
    private final Libpng    libpng;
    private final Connector connector;

    private final Pointer<png_rw_ptr> pngWriteCallback = nref(this::pngWriteCallback);

    private final ExecutorService connectorThread = Executors.newSingleThreadExecutor();

    private final Set<Html5Socket> html5Sockets = new HashSet<>();

    private final    Lock                 pngBufferLock = new ReentrantLock();
    private volatile Optional<ByteBuffer> pngBuffer     = Optional.empty();
    private volatile long                 pngBufferAge  = 0L;


    Html5Connector(@Provided @Nonnull final Libpng libpng,
                   @Nonnull final Connector connector) {
        this.libpng = libpng;
        this.connector = connector;
    }

    @Nonnull
    @Override
    public WlOutput getWlOutput() {
        return this.connector.getWlOutput();
    }

    //TODO add methods to send screen updates to all connected sockets

    /**
     * @param bufferRGBA
     * @param flipHorizontal
     * @param width
     * @param height
     */
    public void commitFrame(final Pointer<Byte> bufferRGBA,
                            final boolean flipHorizontal,
                            final int width,
                            final int height) {
        this.connectorThread.execute(() -> {
            //we have to allocate a new buffer on each new frame as sockets might hold on to older buffers until the client acks.
            final ByteBuffer buffer = ByteBuffer.allocate(maxPNGSize(width,
                                                                     height));
            this.pngBufferLock.lock();
            try {
                this.pngBufferAge = System.nanoTime();
                this.pngBuffer = Optional.of(buffer);
                toPng(bufferRGBA,
                      flipHorizontal,
                      width,
                      height);
            }
            finally {
                this.pngBufferLock.unlock();
            }
            bufferRGBA.close();
            buffer.rewind();
            this.html5Sockets.forEach(Html5Socket::render);
        });


        //TODO/FIXME always keep the png buffer up to date unless all connected clients are busy or no clients are present
        //TODO/FIXME only send out updated png buffers to those clients who are not busy ie have ack'ed their previous frame
        //TODO/FIXME if a client has ack'ed it's frame and the png buffer was updated, send out a new frame to this client
        //TODO/FIXME if a new client connects, consider it as an frame ack'ed client.
    }

    private int maxPNGSize(final int width,
                           final int height) {
        return 8 // PNG signature bytes
               + 25 // IHDR chunk
               + 12 // IDAT chunk (assuming only one IDAT chunk)
               + height //pixels
                 * (1 // filter byte for each row
                    + (width // pixels
                       * 3 // Red, blue, green color samples
                       * 2 // 16 bits per color sample
                    )
                 )
               + 6 // zlib compression overhead
               + 2 // deflate overhead
               + 12; // IEND chunk
    }

    private void toPng(final Pointer<Byte> sourceRGBA,
                       final boolean flipHorizontal,
                       final int pitch,
                       final int height) {

        final long p = this.libpng.png_create_write_struct(Pointer.nref("1.6.23+apng").address,
                                                           0L,
                                                           0L,
                                                           0L);
        if (p == 0L) {
            throw new RuntimeException("png_create_write_struct() failed");
        }

        final long infoPtr = this.libpng.png_create_info_struct(p);
        if (infoPtr == 0L) {
            throw new RuntimeException("png_create_info_struct() failed");
        }

        //FIXME get this to work...
//        if (0 != this.libc.setjmp(this.libpng.png_jmpbuf(p))) {
//            throw new RuntimeException("setjmp(png_jmpbuf(p) failed");
//        }

        this.libpng.png_set_IHDR(p,
                                 infoPtr,
                                 pitch,
                                 height,
                                 8,
                                 PNG_COLOR_TYPE_RGBA,
                                 PNG_INTERLACE_NONE,
                                 PNG_COMPRESSION_TYPE_DEFAULT,
                                 PNG_FILTER_TYPE_DEFAULT);

        final Pointer<Pointer<Byte>> rows = malloc(height * sizeof((Pointer) null),
                                                   Byte.class).castpp();
        if (flipHorizontal) {
            int row = 0;
            for (int y = height - 1; y >= 0; --y, row++) {
                rows.writei(row,
                            sourceRGBA.offset(y * pitch * 4));
            }
        }
        else {
            for (int y = 0; y < height; ++y) {
                rows.writei(y,
                            sourceRGBA.offset(y * pitch * 4));
            }
        }
        this.libpng.png_set_rows(p,
                                 infoPtr,
                                 rows.address);

        this.libpng.png_set_write_fn(p,
                                     0L,
                                     this.pngWriteCallback.address,
                                     0L);
        this.libpng.png_write_png(p,
                                  infoPtr,
                                  PNG_TRANSFORM_IDENTITY,
                                  0L);

        rows.close();
        this.libpng.png_destroy_write_struct(Pointer.nref(Pointer.wrap(p)).address,
                                             Pointer.nref(Pointer.wrap(infoPtr)).address);
    }

    private void pngWriteCallback(@Ptr final long png_ptr,
                                  @Ptr(byte.class) final long png_bytep,
                                  @Unsigned @Lng final long png_size_t) {
        this.pngBuffer.ifPresent(buffer -> {
            for (int i = 0; i < png_size_t; i++) {
                buffer.put(JNI.readByte(png_bytep,
                                        i));
            }
        });
    }

    public void onWebSocketClose(final Html5Socket html5Socket) {
        this.connectorThread.submit(() -> this.html5Sockets.remove(html5Socket));
    }

    public Optional<ByteBuffer> getPngBufferCopy() {
        this.pngBufferLock.lock();
        try {
            return this.pngBuffer.map(this::clone);
        }
        finally {
            this.pngBufferLock.unlock();
        }
    }

    public long getPngBufferAge() {
        return this.pngBufferAge;
    }

    private ByteBuffer clone(final ByteBuffer original) {
        final ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();//copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
    }

    @Override
    public void accept(@Nonnull final Renderer renderer) {
        renderer.visit(this.connector);
    }

    public void onWebSocketConnect(final Html5Socket html5Socket) {
        this.connectorThread.submit(() -> this.html5Sockets.add(html5Socket));
    }
}
