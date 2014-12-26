/*
 * Copyright (c) 2012-2014 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.spotify.netty4.handler.codec.zmtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public class ZMTPWriter {

  private final int version;

  private ByteBufAllocator alloc = ByteBufAllocator.DEFAULT;

  private int expectedFrames;
  private int expectedSize;
  private ByteBuf buf;

  private int frame;

  public ZMTPWriter(final int version) {
    this.version = version;
  }

  public void alloc(final ByteBufAllocator alloc) {
    this.alloc = alloc;
  }

  public void reset() {
    expectedFrames = 0;
    expectedSize = 0;
    buf = null;
    frame = 0;
  }

  public void expectFrame(final int size) {
    expectedFrames++;
    expectedSize += ZMTPUtils.frameSize(size, version);
  }

  public void expectFrames(final int frames) {
    this.expectedFrames = frames;
  }

  public void expectSize(final int size) {
    this.expectedSize = size;
  }

  public void begin() {
    buf = alloc.buffer(expectedSize);
  }

  public ByteBuf frame(final int size) {
    if (frame >= expectedFrames) {
      throw new IllegalStateException("content");
    }
    final boolean more = (frame + 1 < expectedFrames);
    return frame(size, more);
  }

  private ByteBuf frame(final int size, final boolean more) {
    ZMTPUtils.writeFrameHeader(buf, size, more, version);
    frame++;
    return buf;
  }

  public void end() {
  }

  public ByteBuf finish() {
    final ByteBuf result = this.buf;
    reset();
    return result;
  }
}
