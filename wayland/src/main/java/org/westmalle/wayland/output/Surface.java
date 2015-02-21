//Copyright 2015 Erik De Rijcke
//
//Licensed under the Apache License,Version2.0(the"License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing,software
//distributed under the License is distributed on an"AS IS"BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
package org.westmalle.wayland.output;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.collect.Lists;
import org.freedesktop.wayland.server.*;
import org.westmalle.wayland.protocol.WlCompositor;

import javax.annotation.Nonnull;
import javax.media.nativewindow.util.Point;
import javax.media.nativewindow.util.PointImmutable;
import javax.media.nativewindow.util.RectangleImmutable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@AutoFactory(className = "SurfaceFactory")
public class Surface {

    private final RegionFactory        regionFactory;
    @Nonnull
    private final WlCompositorResource wlCompositorResource;

    //pending states
    @Nonnull
    private Optional<WlRegionResource> pendingOpaqueRegion = Optional.empty();
    @Nonnull
    private Optional<WlRegionResource> pendingInputRegion  = Optional.empty();
    @Nonnull
    private Optional<Region>           pendingDamage       = Optional.empty();
    @Nonnull
    private Optional<WlBufferResource> pendingBuffer       = Optional.empty();
    @Nonnull
    private float[]                    pendingTransform    = new float[]{
            1, 0, 0,
            0, 1, 0,
            0, 0, 1
    };
    @Nonnull
    private Point                      pendingBufferOffset = new Point();

    //committed states
    @Nonnull
    private final List<WlCallbackResource>   callbacks    = Lists.newLinkedList();
    @Nonnull
    private       Optional<WlRegionResource> opaqueRegion = Optional.empty();
    @Nonnull
    private       Optional<WlRegionResource> inputRegion  = Optional.empty();
    @Nonnull
    private       Optional<Region>           damage       = Optional.empty();
    @Nonnull
    private       Optional<WlBufferResource> buffer       = Optional.empty();
    @Nonnull
    private       float[]                    transform    = new float[]{
            1, 0, 0,
            0, 1, 0,
            0, 0, 1
    };
    @Nonnull
    private       Point                      position     = new Point();
    //additional server side states
    @Nonnull
    private       Boolean                    destroyed    = Boolean.FALSE;

    Surface(@Provided final RegionFactory regionFactory,
            @Nonnull WlCompositorResource wlCompositorResource) {
        this.regionFactory = regionFactory;
        this.wlCompositorResource = wlCompositorResource;
    }

    @Nonnull
    public List<WlCallbackResource> getFrameCallbacks() {
        return this.callbacks;
    }

    @Nonnull
    public Boolean isDestroyed() {
        return this.destroyed;
    }

    @Nonnull
    public float[] getTransform() {
        return this.transform;
    }

    @Nonnull
    public Surface markDestroyed() {
        this.destroyed = true;
        return this;
    }

    @Nonnull
    public Surface markDamaged(@Nonnull final RectangleImmutable damage) {
        this.pendingDamage = Optional.of(this.pendingDamage.orElse(this.regionFactory.create())
                                                           .add(damage));
        return this;
    }

    @Nonnull
    public Surface attachBuffer(@Nonnull final WlBufferResource buffer,
                                @Nonnull final Integer relX,
                                @Nonnull final Integer relY) {

        this.pendingBuffer = Optional.of(buffer);
        this.pendingBufferOffset = new Point(relX,
                                             relY);
        return this;
    }

    @Nonnull
    public Surface setTransform(@Nonnull final float[] transform) {
        this.pendingTransform = transform;
        return this;
    }

    @Nonnull
    public Surface removeTransform() {
        this.pendingTransform = new float[]{1,
                                            0,
                                            0,
                                            0,
                                            1,
                                            0,
                                            0,
                                            0,
                                            1};
        return this;
    }

    @Nonnull
    public Surface detachBuffer() {
        this.pendingBuffer = Optional.empty();
        this.pendingDamage = Optional.empty();
        this.pendingBufferOffset = new Point();
        return this;
    }

    @Nonnull
    public PointImmutable getPosition() {
        return this.position;
    }

    @Nonnull
    public Optional<WlRegionResource> getInputRegion() {
        return this.inputRegion;
    }

    @Nonnull
    public Optional<Region> getDamage() {
        return this.damage;
    }

    @Nonnull
    public Optional<WlRegionResource> getOpaqueRegion() {
        return this.opaqueRegion;
    }

    @Nonnull
    public Optional<WlBufferResource> getBuffer() {
        return this.buffer;
    }

    @Nonnull
    public Surface commit(final WlSurfaceResource wlSurfaceResource) {
        //flush
        this.transform = this.pendingTransform;
        if (this.buffer.isPresent()) {
            //signal client that the previous buffer can be reused as we will now use the
            //newly attached buffer.
            final WlBufferResource wlBufferResource = this.buffer.get();
            wlBufferResource.release();
        }
        this.buffer = this.pendingBuffer;
        this.position = this.position.translate(this.pendingBufferOffset);
        this.damage = this.pendingDamage;
        this.inputRegion = this.pendingInputRegion;
        this.opaqueRegion = this.pendingOpaqueRegion;
        //reset
        detachBuffer();
        WlCompositor wlCompositor = (WlCompositor) this.wlCompositorResource.getImplementation();
        wlCompositor.getCompositor()
                    .requestRender(wlSurfaceResource);
        return this;
    }

    @Nonnull
    public Surface addCallback(final WlCallbackResource callback) {
        this.callbacks.add(callback);
        return this;
    }

    @Nonnull
    public Surface removeOpaqueRegion() {
        this.pendingOpaqueRegion = Optional.empty();
        return this;
    }

    @Nonnull
    public Surface setOpaqueRegion(@Nonnull final WlRegionResource opaqueRegion) {
        this.pendingOpaqueRegion = Optional.of(opaqueRegion);
        return this;
    }

    @Nonnull
    public Surface removeInputRegion() {
        this.pendingInputRegion = Optional.empty();
        return this;
    }

    @Nonnull
    public Surface setInputRegion(@Nonnull final WlRegionResource inputRegion) {
        this.pendingInputRegion = Optional.of(inputRegion);
        return this;
    }

    @Nonnull
    public Surface setPosition(@Nonnull final PointImmutable position) {
        this.position = new Point(position.getX(),
                                  position.getY());
        return this;
    }

    public Surface firePaintCallbacks(final int serial) {
        final List<WlCallbackResource> callbacks = new ArrayList<>(getFrameCallbacks());
        getFrameCallbacks().clear();
        callbacks.forEach(frameCallback -> frameCallback.done(serial));
        return this;
    }

    public PointImmutable relativeCoordinate(final PointImmutable absoluteCoordinate) {
        final PointImmutable position = getPosition();
        final int offsetX = position.getX();
        final int offsetY = position.getY();

        return new Point(absoluteCoordinate.getX() - offsetX,
                         absoluteCoordinate.getY() - offsetY);
    }
}