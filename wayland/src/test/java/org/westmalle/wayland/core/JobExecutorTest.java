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
package org.westmalle.wayland.core;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import org.freedesktop.wayland.server.Display;
import org.freedesktop.wayland.server.EventLoop;
import org.freedesktop.wayland.server.EventSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;
import org.westmalle.wayland.nativ.Libc;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JobExecutorTest {

    private final int pipeR  = 1;
    private final int pipeWR = 2;
    @Mock
    private Display display;
    @Mock
    private Libc libc;

    private JobExecutor jobExecutor;

    @Before
    public void setUp() {
        this.jobExecutor = new JobExecutor(this.display,
                                           this.pipeR,
                                           this.pipeWR,
                                           this.libc);
    }

    @Test
    public void testSingleStart() throws Exception {
        //event loop mock
        final EventLoop eventLoop = mock(EventLoop.class);
        when(this.display.getEventLoop()).thenReturn(eventLoop);
        final EventSource eventSource = mock(EventSource.class);
        when(eventLoop.addFileDescriptor(anyInt(),
                                         anyInt(),
                                         any())).thenReturn(eventSource);
        this.jobExecutor.start();
        Mockito.verify(eventLoop)
               .addFileDescriptor(eq(this.pipeR),
                                  eq(EventLoop.EVENT_READABLE),
                                  any());
    }

    @Test(expected = IllegalStateException.class)
    public void testDoubleStart() throws Exception {
        final EventLoop eventLoop = mock(EventLoop.class);
        when(this.display.getEventLoop()).thenReturn(eventLoop);
        final EventSource eventSource = mock(EventSource.class);
        when(eventLoop.addFileDescriptor(anyInt(),
                                         anyInt(),
                                         any())).thenReturn(eventSource);
        this.jobExecutor.start();
        this.jobExecutor.start();
    }

    @Test
    public void testSingleFireFinishedEvent() throws Exception {
        final EventLoop eventLoop = mock(EventLoop.class);
        when(this.display.getEventLoop()).thenReturn(eventLoop);
        final EventSource eventSource = mock(EventSource.class);
        when(eventLoop.addFileDescriptor(anyInt(),
                                         anyInt(),
                                         any())).thenReturn(eventSource);

        when(this.libc.write(eq(this.pipeWR),
                             eq((Pointer) Whitebox.getInternalState(this.jobExecutor,
                                                                    "eventFinishedBuffer")),
                             eq(1))).thenAnswer(invocation -> this.jobExecutor.handle(this.pipeR,
                                                                                      1234));
        doAnswer(invocation -> {
                     Pointer buffer = (Pointer) invocation.getArguments()[1];
                     //event finished
                     buffer.setByte(0,
                                    (byte) 0);
                     return null;
                 }
                ).when(this.libc)
                 .read(eq(this.pipeR),
                       any(),
                       anyInt());

        this.jobExecutor.start();
        this.jobExecutor.fireFinishedEvent();

        final ArgumentCaptor<Pointer> bufferArgumentCaptor = ArgumentCaptor.forClass(Pointer.class);
        verify(this.libc).write(eq(this.pipeWR),
                                bufferArgumentCaptor.capture(),
                                eq(1));
        assertThat(bufferArgumentCaptor.getValue()
                                       .getByte(0)).isEqualTo((byte) 0);
        verify(eventSource).remove();
        verify(this.libc).close(this.pipeR);
        verify(this.libc).close(this.pipeWR);
    }

    @Test
    public void testSingleSubmit() throws Exception {
        //event loop mock
        //given
        final EventLoop eventLoop = mock(EventLoop.class);
        when(this.display.getEventLoop()).thenReturn(eventLoop);
        final EventSource eventSource = mock(EventSource.class);
        when(eventLoop.addFileDescriptor(anyInt(),
                                         anyInt(),
                                         any())).thenReturn(eventSource);

        when(this.libc.write(eq(this.pipeWR),
                             eq(new Memory(1)),
                             eq(1))).then(invocation -> {
            this.jobExecutor.handle(this.pipeR,
                                    1234);
            return null;
        });

        doAnswer(invocation -> {
                     Pointer buffer = (Pointer) invocation.getArguments()[1];
                     //new job
                     buffer.setByte(0,
                                    (byte) 1);
                     return null;
                 }
                ).when(this.libc)
                 .read(eq(this.pipeR),
                       any(),
                       anyInt());

        when(this.libc.write(this.pipeWR,
                             (Pointer) Whitebox.getInternalState(this.jobExecutor,
                                                                 "eventNewJobBuffer"),
                             1)).thenAnswer(invocation -> {
            JobExecutorTest.this.jobExecutor.handle(this.pipeR,
                                                    0);
            return null;
        });

        //when
        final Runnable job = mock(Runnable.class);
        this.jobExecutor.start();
        this.jobExecutor.submit(job);

        //then
        final ArgumentCaptor<Pointer> bufferArgumentCaptor = ArgumentCaptor.forClass(Pointer.class);
        verify(this.libc).write(eq(this.pipeWR),
                                bufferArgumentCaptor.capture(),
                                eq(1));
        assertThat(bufferArgumentCaptor.getValue()
                                       .getByte(0)).isEqualTo((byte) 1);
        verify(job).run();
    }

    @Test
    public void testDoubleSubmit() throws Exception {
        //event loop mock
        final EventLoop eventLoop = mock(EventLoop.class);
        when(this.display.getEventLoop()).thenReturn(eventLoop);
        final EventSource eventSource = mock(EventSource.class);
        when(eventLoop.addFileDescriptor(anyInt(),
                                         anyInt(),
                                         any())).thenReturn(eventSource);

        when(this.libc.write(eq(this.pipeWR),
                             eq(new Memory(1) {{
                                 setByte(0,
                                         (byte) 1);
                             }}),
                             eq(1))).then(invocation -> {
            this.jobExecutor.handle(this.pipeR,
                                    1234);
            return null;
        });
        doAnswer(invocation -> {
                     Pointer buffer = (Pointer) invocation.getArguments()[1];
                     //new job
                     buffer.setByte(0,
                                    (byte) 1);
                     return null;
                 }
                ).when(this.libc)
                 .read(eq(this.pipeR),
                       any(),
                       anyInt());

        when(this.libc.write(this.pipeWR,
                             (Pointer) Whitebox.getInternalState(this.jobExecutor,
                                                                 "eventNewJobBuffer"),
                             1)).thenAnswer(invocation -> {
            JobExecutorTest.this.jobExecutor.handle(this.pipeR,
                                                    0);
            return null;
        });

        final Runnable job = mock(Runnable.class);
        this.jobExecutor.start();
        this.jobExecutor.submit(job);
        this.jobExecutor.submit(job);

        final ArgumentCaptor<Pointer> bufferArgumentCaptor = ArgumentCaptor.forClass(Pointer.class);
        verify(this.libc,
               times(2)).write(eq(this.pipeWR),
                               bufferArgumentCaptor.capture(),
                               eq(1));
        assertThat(bufferArgumentCaptor.getValue()
                                       .getByte(0)).isEqualTo((byte) 1);
        verify(job,
               times(2)).run();
    }

    @Test
    public void testSubmitFireFinishedEventSubmit() throws Exception {
        //event loop mock
        final EventLoop eventLoop = mock(EventLoop.class);
        when(this.display.getEventLoop()).thenReturn(eventLoop);
        final EventSource eventSource = mock(EventSource.class);
        when(eventLoop.addFileDescriptor(anyInt(),
                                         anyInt(),
                                         any())).thenReturn(eventSource);

        //new job event mock behavior
        when(this.libc.write(eq(this.pipeWR),
                             eq((Pointer) Whitebox.getInternalState(this.jobExecutor,
                                                                    "eventNewJobBuffer")),
                             eq(1))).thenAnswer(writeAnswer -> {

            doAnswer(readAnswer -> {
                         Pointer buffer = (Pointer) readAnswer.getArguments()[1];
                         //new job
                         buffer.setByte(0,
                                        (byte) 1);
                         return null;
                     }
                    ).when(this.libc)
                     .read(eq(this.pipeR),
                           any(),
                           anyInt());
            this.jobExecutor.handle(this.pipeR,
                                    1234);
            return null;
        });

        //finished event mock behavior
        when(this.libc.write(eq(this.pipeWR),
                             eq((Pointer) Whitebox.getInternalState(this.jobExecutor,
                                                                    "eventFinishedBuffer")),
                             eq(1))).thenAnswer(writeAnswer -> {
            doAnswer(readAnswer -> {
                         Pointer buffer = (Pointer) readAnswer.getArguments()[1];
                         //event finished
                         buffer.setByte(0,
                                        (byte) 0);
                         return null;
                     }
                    ).when(this.libc)
                     .read(eq(this.pipeR),
                           any(),
                           anyInt());
            this.jobExecutor.handle(this.pipeR,
                                    1234);
            return null;
        });

        this.jobExecutor.start();
        final Runnable job = mock(Runnable.class);
        this.jobExecutor.submit(job);
        this.jobExecutor.fireFinishedEvent();
        this.jobExecutor.submit(job);

        final ArgumentCaptor<Pointer> bufferArgumentCaptor = ArgumentCaptor.forClass(Pointer.class);
        verify(this.libc,
               times(3)).write(eq(this.pipeWR),
                               bufferArgumentCaptor.capture(),
                               eq(1));
        assertThat(bufferArgumentCaptor.getAllValues()
                                       .get(0)
                                       .getByte(0)).isEqualTo((byte) 1);
        assertThat(bufferArgumentCaptor.getAllValues()
                                       .get(1)
                                       .getByte(0)).isEqualTo((byte) 0);
        assertThat(bufferArgumentCaptor.getAllValues()
                                       .get(2)
                                       .getByte(0)).isEqualTo((byte) 1);

        verify(job).run();
        verify(eventSource).remove();
        verify(this.libc).close(this.pipeR);
        verify(this.libc).close(this.pipeWR);
        verifyNoMoreInteractions(job);
    }
}